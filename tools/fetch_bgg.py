#!/usr/bin/env python3
"""
Haalt spelersaantal, speelduur en zwaarte op bij BoardGameGeek (XML API 2)
voor alle spellen in het importbestand en schrijft tools/bgg_data.json.

Draait in GitHub Actions (workflow "BGG-gegevens ophalen"). Spellen met een
BGG-link worden op id opgehaald; de rest op naam gezocht (exacte match eerst).
Optioneel: secret BGG_TOKEN (BGG vraagt sinds 2025 een geregistreerde app-token).
"""
import json
import urllib.error
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "app/src/main/assets/seed_medael_2026.json"
OUT = ROOT / "tools/bgg_data.json"
API = "https://boardgamegeek.com/xmlapi2"
TOKEN = os.environ.get("BGG_TOKEN", "").strip()


def get(url: str) -> ET.Element:
    headers = {"User-Agent": "Spellenweekend-app (github.com/mheunen/Slimbo_weekend)"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    for attempt in range(8):
        req = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                body = r.read()
                if r.status == 202:  # BGG zet het verzoek in de wachtrij
                    time.sleep(3 + attempt * 2)
                    continue
                return ET.fromstring(body)
        except urllib.error.HTTPError as e:
            if e.code in (202, 429, 500, 502, 503):
                time.sleep(5 + attempt * 3)
                continue
            raise
    raise RuntimeError(f"BGG gaf geen antwoord: {url}")


def clean(name: str) -> str:
    n = re.sub(r"\(.*?\)", "", name)
    n = re.sub(r"\b(evt|en uitbreiding.*|alle versies|second edition|\+ ?xp|\+ ?uitbreiding)\b.*", "", n, flags=re.I)
    return n.strip(" -+:")


def search(name: str) -> str | None:
    q = clean(name)
    for exact in (1, 0):
        url = f"{API}/search?type=boardgame&exact={exact}&query={urllib.parse.quote(q)}"
        root = get(url)
        items = root.findall("item")
        if items:
            # Bij niet-exact: kies het item waarvan de naam het best overeenkomt
            if exact == 0:
                ql = q.lower()
                items.sort(key=lambda it: (0 if it.find("name").get("value").lower() == ql else
                                           1 if it.find("name").get("value").lower().startswith(ql) else 2))
            return items[0].get("id")
        time.sleep(1.5)
    return None


def weight_label(w: float) -> str:
    if w <= 0:
        return ""
    return "LICHT" if w < 2.2 else "MIDDEL" if w < 3.2 else "ZWAAR"


GEEKDO = "https://api.geekdo.com/api"


def get_json(url: str):
    headers = {"User-Agent": "Mozilla/5.0 (Spellenweekend-app)", "Accept": "application/json"}
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503):
                time.sleep(4 + attempt * 3)
                continue
            raise
    raise RuntimeError(f"geen antwoord: {url}")


SEARCH_LOG: list[str] = []


def _html(url: str) -> str:
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36",
               "Accept": "text/html,application/json"}
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as r:
        return r.read().decode("utf-8", "replace")


def search_web(name: str) -> str | None:
    q = clean(name)
    qq = urllib.parse.quote(q)
    attempts = [
        ("geekdo-search", f"https://api.geekdo.com/api/search?nosession=1&objecttype=thing&subtype=boardgame&q={qq}",
         lambda t: [(str(i.get("objectid")), i.get("name", "")) for i in json.loads(t).get("items", [])]),
        ("bgg-json", f"https://boardgamegeek.com/search/boardgame?q={qq}&showcount=10",
         lambda t: [(str(i.get("objectid")), i.get("name", "")) for i in json.loads(t).get("items", [])]),
        ("geeksearch-html", f"https://boardgamegeek.com/geeksearch.php?action=search&objecttype=boardgame&q={qq}",
         lambda t: [(m.group(1), m.group(2)) for m in re.finditer(r'href="/boardgame/(\d+)/[^"]*"[^>]*>([^<]+)</a>', t)]),
        ("geekdo-xmlapi", f"https://api.geekdo.com/xmlapi/search?search={qq}",
         lambda t: [(b.get("objectid"), (b.findtext("name") or "")) for b in ET.fromstring(t).findall("boardgame")]),
    ]
    for label, url, parse in attempts:
        try:
            items = parse(_html(url))
        except Exception as e:  # noqa: BLE001
            SEARCH_LOG.append(f"{label}: {type(e).__name__} {getattr(e, 'code', '')}")
            continue
        if not items:
            SEARCH_LOG.append(f"{label}: 0 resultaten voor {q}")
            continue
        ql = q.lower()
        items.sort(key=lambda it: (0 if it[1].strip().lower() == ql else 1 if it[1].strip().lower().startswith(ql) else 2))
        SEARCH_LOG.append(f"{label}: ok {q} -> {items[0]}")
        return items[0][0]
    return None


def thing_web(bid: str) -> dict:
    item = get_json(f"{GEEKDO}/geekitems?nosession=1&objecttype=thing&subtype=boardgame&objectid={bid}").get("item", {})
    w = 0.0
    try:
        dyn = get_json(f"{GEEKDO}/dynamicinfo?objectid={bid}&objecttype=thing")
        w = float(dyn.get("item", {}).get("stats", {}).get("avgweight") or 0)
    except Exception:  # noqa: BLE001
        pass
    def num(k):
        try:
            return int(item.get(k) or 0)
        except (TypeError, ValueError):
            return 0
    return {
        "bggId": bid,
        "bggName": item.get("name", ""),
        "minPlayers": num("minplayers"),
        "maxPlayers": num("maxplayers"),
        "durationMin": num("maxplaytime") or num("minplaytime"),
        "weight": weight_label(w),
        "averageWeight": round(w, 2),
    }


def _norm(s: str) -> str:
    s = re.sub(r"\(.*?\)", "", s.lower())
    s = re.sub(r"(second edition|deluxe edition|en uitbreiding.*|en uitbreidingen)", "", s)
    return re.sub(r"[^a-z0-9]", "", s)


def names_match(ours: str, theirs: str) -> bool:
    a, b = _norm(ours), _norm(theirs)
    if not a or not b:
        return False
    return a == b or a in b or b in a


def main():
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    games = seed["games"]
    result, unresolved, errors = {}, [], []
    cache: dict[str, dict] = {}
    manual = {}
    ids_file = ROOT / "tools/bgg_ids.json"
    if ids_file.exists():
        manual = {k: v for k, v in json.loads(ids_file.read_text(encoding="utf-8")).items() if not k.startswith("_")}
    rejected = []
    for g in games:
        m = re.search(r"/boardgame/(\d+)", g.get("link", ""))
        bid = m.group(1) if m else None
        from_manual = False
        if not bid and g["name"] in manual:
            bid, from_manual = manual[g["name"]], True
        try:
            if not bid:
                bid = search_web(g["name"])
                time.sleep(1)
            if not bid:
                unresolved.append(g["name"])
                continue
            if bid not in cache:
                cache[bid] = thing_web(bid)
                time.sleep(1)
            info = cache[bid]
            mismatch = from_manual and not names_match(g["name"], info.get("bggName", ""))
            if mismatch:
                rejected.append(f"{g['name']} -> {bid} = {info.get('bggName')}")
            result[g["id"]] = {"name": g["name"], "source": "id" if from_manual else "link",
                               "nameMismatch": mismatch, **info}
        except Exception as e:  # noqa: BLE001
            errors.append(f"{g['name']}: {type(e).__name__}: {e}")

    OUT.write_text(json.dumps({"games": result, "unresolved": unresolved, "rejected": rejected, "errors": errors,
                               "searchLog": SEARCH_LOG[:60]},
                              ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(result)} van {len(games)} spellen gevonden")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001
        detail = f"{type(e).__name__}: {e}"
        if isinstance(e, urllib.error.HTTPError):
            try:
                detail += " | " + e.read()[:500].decode("utf-8", "replace")
            except Exception:  # noqa: BLE001
                pass
        OUT.write_text(json.dumps({"error": detail, "tokenSet": bool(TOKEN)}, indent=1), encoding="utf-8")
        print(detail, file=sys.stderr)
