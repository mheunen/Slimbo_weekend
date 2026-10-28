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


def search_web(name: str) -> str | None:
    q = clean(name)
    url = "https://boardgamegeek.com/search/boardgame?nosession=1&showcount=10&q=" + urllib.parse.quote(q)
    data = get_json(url)
    items = data.get("items", [])
    if not items:
        return None
    ql = q.lower()
    items.sort(key=lambda it: (0 if it.get("name", "").lower() == ql else
                               1 if it.get("name", "").lower().startswith(ql) else 2))
    return str(items[0].get("objectid"))


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


def main():
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    games = seed["games"]
    result, unresolved, errors = {}, [], []
    cache: dict[str, dict] = {}
    for g in games:
        m = re.search(r"/boardgame/(\d+)", g.get("link", ""))
        bid = m.group(1) if m else None
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
            result[g["id"]] = {"name": g["name"], **cache[bid]}
        except Exception as e:  # noqa: BLE001
            errors.append(f"{g['name']}: {type(e).__name__}: {e}")
            if len(errors) >= 5 and not result:
                raise RuntimeError(" ; ".join(errors))
    OUT.write_text(json.dumps({"games": result, "unresolved": unresolved, "errors": errors},
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
