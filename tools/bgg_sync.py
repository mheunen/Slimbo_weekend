#!/usr/bin/env python3
"""
Vergelijkt de spellen in de app met BoardGameGeek en schrijft web/sync/bgg.json.
Draait in GitHub Actions (workflow "BGG bijwerken"). De beheersite (tabblad BGG)
toont de verschillen; daar kies je wat er in de app verwerkt wordt.

1. Logt anoniem in bij Firebase (zelfde sleutel als de website), zoekt het weekend
   op code, meldt zich tijdelijk aan als verborgen lid om de spellen te lezen en
   meldt zich daarna weer af.
2. BGG-nummer per spel: uit de link (/boardgame/123), anders uit tools/bgg_ids.json,
   anders zoeken op naam (alleen als BGG_TOKEN is ingesteld, via de XML API).
3. Haalt per nummer spelers, speelduur, zwaarte en categorieën op.
"""
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "web/sync/bgg.json"
IDS = ROOT / "tools/bgg_ids.json"
JOIN_CODE = os.environ.get("JOIN_CODE", "J7A3V5")  # Mini Slimbo 2027 (Medael was 3GQ4B9)
TOKEN = os.environ.get("BGG_TOKEN", "").strip()
GEEKDO = "https://api.geekdo.com/api"
UA = "Mozilla/5.0 (Spellenweekend-app; github.com/mheunen/Slimbo_weekend)"


def http(url, data=None, headers=None, method=None):
    h = {"User-Agent": UA, **(headers or {})}
    body = json.dumps(data).encode() if data is not None else None
    if body is not None:
        h["Content-Type"] = "application/json"
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, data=body, headers=h, method=method)
            with urllib.request.urlopen(req, timeout=30) as r:
                if r.status == 202:
                    time.sleep(3 + attempt * 2)
                    continue
                raw = r.read()
                return raw
        except urllib.error.HTTPError as e:
            if e.code in (202, 429, 500, 502, 503) and attempt < 5:
                time.sleep(4 + attempt * 3)
                continue
            raise
    raise RuntimeError(f"geen antwoord: {url}")


# ------------------------------------------------------------------ Firestore
class Store:
    def __init__(self):
        gs = json.loads(os.environ["GOOGLE_SERVICES_JSON"])
        self.key = gs["client"][0]["api_key"][0]["current_key"]
        self.project = gs["project_info"]["project_id"]
        auth = json.loads(http(f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={self.key}",
                               {"returnSecureToken": True}))
        self.token, self.uid = auth["idToken"], auth["localId"]
        self.base = f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/(default)/documents"

    def _h(self):
        return {"Authorization": f"Bearer {self.token}"}

    def find_event(self, code):
        q = {"structuredQuery": {"from": [{"collectionId": "events"}],
                                 "where": {"fieldFilter": {"field": {"fieldPath": "joinCode"}, "op": "EQUAL",
                                                           "value": {"stringValue": code}}}, "limit": 1}}
        res = json.loads(http(f"{self.base}:runQuery", q, self._h()))
        for r in res:
            if "document" in r:
                return r["document"]["name"].rsplit("/", 1)[1]
        raise RuntimeError(f"geen weekend met code {code}")

    def set_member(self, event):
        doc = {"fields": {"name": {"stringValue": "BGG-controle"}, "web": {"booleanValue": True},
                          "hidden": {"booleanValue": True}, "status": {"stringValue": "BEZET"}}}
        http(f"{self.base}/events/{event}/members/{self.uid}", doc, self._h(), "PATCH")

    def delete_member(self, event):
        http(f"{self.base}/events/{event}/members/{self.uid}", None, self._h(), "DELETE")

    def update_game(self, event, gid, data):
        mask = "&".join("updateMask.fieldPaths=" + urllib.parse.quote(k) for k in data)
        doc = {"fields": {k: enc(v) for k, v in data.items()}}
        http(f"{self.base}/events/{event}/games/{gid}?{mask}", doc, self._h(), "PATCH")

    def games(self, event):
        out, page = [], ""
        while True:
            url = f"{self.base}/events/{event}/games?pageSize=300" + (f"&pageToken={page}" if page else "")
            res = json.loads(http(url, None, self._h()))
            for d in res.get("documents", []):
                out.append({"id": d["name"].rsplit("/", 1)[1], **{k: val(v) for k, v in d.get("fields", {}).items()}})
            page = res.get("nextPageToken")
            if not page:
                return out


def enc(v):
    if isinstance(v, bool): return {"booleanValue": v}
    if isinstance(v, int): return {"integerValue": str(v)}
    if isinstance(v, float): return {"doubleValue": v}
    if isinstance(v, list): return {"arrayValue": {"values": [enc(x) for x in v]}}
    return {"stringValue": str(v)}


def val(v):
    if "stringValue" in v: return v["stringValue"]
    if "integerValue" in v: return int(v["integerValue"])
    if "doubleValue" in v: return v["doubleValue"]
    if "booleanValue" in v: return v["booleanValue"]
    if "arrayValue" in v: return [val(x) for x in v["arrayValue"].get("values", [])]
    if "mapValue" in v: return {k: val(x) for k, x in v["mapValue"].get("fields", {}).items()}
    return None


# ------------------------------------------------------------------ BGG
SUBDOMAIN_NL = {
    "Party Games": "Party", "Children's Games": "Kinderen", "Wargames": "Oorlog", "Thematic Games": "Thematisch",
    "Strategy Games": "Strategie", "Abstract Games": "Abstract", "Customizable Games": "Kaartspel", "Family Games": "Familie",
}
CATEGORY_NL = [
    ("Party Game", "Party"), ("Children's Game", "Kinderen"), ("Wargame", "Oorlog"), ("Abstract Strategy", "Abstract"),
    ("Deduction", "Party"), ("Card Game", "Kaartspel"), ("Economic", "Strategie"), ("Civilization", "Strategie"),
    ("Science Fiction", "Thematisch"), ("Fantasy", "Thematisch"), ("Horror", "Thematisch"), ("Adventure", "Thematisch"),
    ("Animals", "Familie"), ("Puzzle", "Familie"), ("Dice", "Familie"),
]


def category_of(subs, cats, coop):
    for prio in ("Party Games", "Children's Games", "Wargames"):
        if prio in subs:
            return SUBDOMAIN_NL[prio]
    if coop and not subs:
        return "Coöperatief"
    if subs:
        if "Family Games" in subs and "Card Game" in cats:
            return "Kaartspel"
        return SUBDOMAIN_NL.get(subs[0], "Overig")
    for en, nl in CATEGORY_NL:
        if en in cats:
            return nl
    return ""


def weight_label(w):
    if w <= 0:
        return ""
    return "LICHT" if w < 2.2 else "MIDDEL" if w < 3.2 else "ZWAAR"


def thing(bid):
    item = json.loads(http(f"{GEEKDO}/geekitems?nosession=1&objecttype=thing&subtype=boardgame&objectid={bid}")).get("item", {})
    if not item:
        item = json.loads(http(f"{GEEKDO}/geekitems?nosession=1&objecttype=thing&subtype=boardgameexpansion&objectid={bid}")).get("item", {})
    w = 0.0
    try:
        dyn = json.loads(http(f"{GEEKDO}/dynamicinfo?objectid={bid}&objecttype=thing"))
        w = float(dyn.get("item", {}).get("stats", {}).get("avgweight") or 0)
    except Exception:  # noqa: BLE001
        pass
    links = item.get("links", {}) or {}
    num = lambda k: int(item.get(k) or 0) if str(item.get(k) or "0").isdigit() else 0  # noqa: E731
    subs = [l.get("name", "") for l in links.get("boardgamesubdomain", [])]
    cats = [l.get("name", "") for l in links.get("boardgamecategory", [])]
    mechs = [l.get("name", "") for l in links.get("boardgamemechanic", [])]
    return {
        "bggId": str(bid), "bggName": item.get("name", ""), "year": item.get("yearpublished", ""),
        "minPlayers": num("minplayers"), "maxPlayers": num("maxplayers"),
        "durationMin": num("maxplaytime") or num("minplaytime"),
        "weight": weight_label(w), "averageWeight": round(w, 2),
        "category": category_of(subs, cats, "Cooperative Game" in mechs),
        "categories": cats[:6],
        "link": f"https://boardgamegeek.com/boardgame/{bid}",
    }


def search(name):
    if not TOKEN:
        return None
    q = clean(name)
    for exact in (1, 0):
        root = ET.fromstring(http(f"https://boardgamegeek.com/xmlapi2/search?type=boardgame&exact={exact}&query={urllib.parse.quote(q)}",
                                  headers={"Authorization": f"Bearer {TOKEN}"}))
        items = root.findall("item")
        if items:
            ql = norm(q)
            items.sort(key=lambda it: 0 if norm(it.find("name").get("value")) == ql else 1)
            return items[0].get("id")
        time.sleep(1.5)
    return None


def clean(name):
    n = re.sub(r"\(.*?\)", "", name)
    n = re.sub(r"\s*(\+|evt\.?|en uitbreiding|alle versies).*$", "", n, flags=re.I)
    return n.strip(" -+:")


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode()
    s = re.sub(r"\(.*?\)", "", s.lower())
    s = re.sub(r"(second edition|deluxe edition|en uitbreiding.*|uitbreidingen?|\+.*)", "", s)
    return re.sub(r"[^a-z0-9]", "", s)


def names_match(a, b):
    a, b = norm(a), norm(b)
    return bool(a and b) and (a == b or a in b or b in a)


def main():
    manual = {k: (v if isinstance(v, list) else str(v)) for k, v in json.loads(IDS.read_text(encoding="utf-8")).items() if not k.startswith("_")}
    manual_norm = {norm(k): v for k, v in manual.items()}
    st = Store()
    event = st.find_event(JOIN_CODE)
    st.set_member(event)
    try:
        run(st, event, manual, manual_norm)
    finally:
        try:
            st.delete_member(event)
        except Exception as e:  # noqa: BLE001
            print("afmelden mislukt:", e, file=sys.stderr)


UNKNOWN_NOTE = "ⓘ Spelers/duur onbekend – graag aanvullen"


def changes(row):
    """Zelfde regels als het BGG-tabblad op de beheersite (bestaande categorie blijft staan)."""
    c, b = row["current"], row["bgg"]
    upd = {}
    if b["minPlayers"]:
        upd["minPlayers"] = b["minPlayers"]
    if b["maxPlayers"]:
        upd["maxPlayers"] = max(b["maxPlayers"], upd.get("minPlayers", c.get("minPlayers") or 1))
    if b["durationMin"]:
        upd["durationMin"] = b["durationMin"]
    if b["weight"]:
        upd["weight"] = b["weight"]
    if b["category"] and not c.get("category"):
        upd["category"] = b["category"]
    if b["categories"]:
        upd["categories"] = b["categories"]
    if row["source"] == "lijst" or not re.search(r"/boardgame(?:expansion)?/\d+", c.get("link") or ""):
        upd["link"] = b["link"]
    upd["infoChecked"] = True
    upd["bggId"] = b["bggId"]
    return upd


def run(st, event, manual, manual_norm):
    games = st.games(event)
    apply_names = {norm(x) for x in os.environ.get("BGG_APPLY", "").split(",") if x.strip()}
    applied = []

    cache, result, unresolved, errors, rejected = {}, [], [], [], []

    def info(bid):
        if bid not in cache:
            cache[bid] = thing(bid)
            time.sleep(0.8)
        return cache[bid]

    for g in sorted(games, key=lambda x: (x.get("name") or "").lower()):
        name = g.get("name") or ""
        m = re.search(r"/boardgame(?:expansion)?/(\d+)", g.get("link") or "")
        bid, source = (m.group(1), "link") if m else (None, "")
        fixed = manual.get(name) or manual_norm.get(norm(name))
        if isinstance(fixed, str) and norm(name) in apply_names and fixed != bid:
            bid, source = fixed, "lijst"  # vaste koppeling gaat bij direct bijwerken voor de link
        try:
            if not bid:
                entry = manual.get(name) or manual_norm.get(norm(name))
                if isinstance(entry, str):
                    bid, source = entry, "lijst"
                elif entry:
                    # Kandidaten: eerst exact dezelfde naam, anders een naam die erin voorkomt
                    found = []
                    for c in entry:
                        try:
                            found.append((c, info(c)["bggName"]))
                        except Exception as e:  # noqa: BLE001
                            found.append((c, f"fout {type(e).__name__}"))
                    exact = [c for c, n in found if norm(n) == norm(name)]
                    loose = [c for c, n in found if names_match(name, n)]
                    pick = (exact or loose or [None])[0]
                    if pick:
                        bid, source = pick, "gezocht"
                    else:
                        rejected.append(f"{name}: " + ", ".join(f"{c} = {n}" for c, n in found))
            if not bid:
                bid = search(name)
                source = "zoeken" if bid else ""
                time.sleep(1)
            if not bid:
                unresolved.append(name)
                continue
            b = info(bid)
            current = {k: g.get(k) for k in ("minPlayers", "maxPlayers", "durationMin", "weight", "category", "categories", "link", "infoChecked")}
            result.append({"id": g["id"], "name": name, "source": source,
                           "nameMatch": names_match(name, b["bggName"]), "current": current, "bgg": b})
        except Exception as e:  # noqa: BLE001
            errors.append(f"{name}: {type(e).__name__}: {e}")

    # Direct in de app zetten (alleen voor de opgegeven spellen; workflow-invoer "apply")
    if apply_names:
        notes_by_id = {g["id"]: g.get("notes") or "" for g in games}
        for row in result:
            if norm(row["name"]) not in apply_names or not (row["nameMatch"] or row["source"] == "lijst"):
                continue
            upd = changes(row)
            notes = notes_by_id.get(row["id"], "")
            if UNKNOWN_NOTE in notes:
                upd["notes"] = " · ".join(p for p in notes.split(" · ") if p.strip() != UNKNOWN_NOTE)
            upd["updatedAt"] = int(time.time() * 1000)
            try:
                st.update_game(event, row["id"], upd)
                applied.append(row["name"])
                row["current"].update({k: v for k, v in upd.items() if k in row["current"]})
            except Exception as e:  # noqa: BLE001
                errors.append(f"bijwerken {row['name']}: {type(e).__name__}: {e}")
        print("Direct bijgewerkt:", ", ".join(applied) or "niets")
        missing = apply_names - {norm(n) for n in applied}
        if missing:
            print("Niet bijgewerkt:", ", ".join(sorted(missing)))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"generated": time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), "event": event,
                               "total": len(games), "games": result, "unresolved": unresolved, "rejected": rejected, "errors": errors,
                               "tokenSet": bool(TOKEN), "applied": applied}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(result)} van {len(games)} spellen gekoppeld; {len(unresolved)} zonder BGG-nummer; {len(errors)} fouten")


if __name__ == "__main__":
    main()
