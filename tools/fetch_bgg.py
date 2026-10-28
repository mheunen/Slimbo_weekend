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


def main():
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    games = seed["games"]
    ids: dict[str, str] = {}
    unresolved = []
    for g in games:
        m = re.search(r"/boardgame/(\d+)", g.get("link", "")) or re.search(r"boardgamegeek\.com/boardgame/(\d+)", g.get("link", ""))
        if m:
            ids[g["id"]] = m.group(1)
            continue
        try:
            found = search(g["name"])
        except Exception as e:  # noqa: BLE001
            print(f"zoeken mislukt voor {g['name']}: {e}", file=sys.stderr)
            found = None
        time.sleep(1.5)
        if found:
            ids[g["id"]] = found
        else:
            unresolved.append(g["name"])

    result = {}
    bgg_ids = sorted(set(ids.values()))
    info = {}
    for i in range(0, len(bgg_ids), 20):
        chunk = bgg_ids[i:i + 20]
        root = get(f"{API}/thing?stats=1&id={','.join(chunk)}")
        for it in root.findall("item"):
            def val(tag, default="0"):
                el = it.find(tag)
                return el.get("value") if el is not None else default
            primary = next((n.get("value") for n in it.findall("name") if n.get("type") == "primary"), "")
            w = it.find("statistics/ratings/averageweight")
            info[it.get("id")] = {
                "bggId": it.get("id"),
                "bggName": primary,
                "minPlayers": int(val("minplayers") or 0),
                "maxPlayers": int(val("maxplayers") or 0),
                "durationMin": int(val("playingtime") or 0) or int(val("maxplaytime") or 0),
                "weight": weight_label(float(w.get("value")) if w is not None else 0.0),
                "averageWeight": round(float(w.get("value")), 2) if w is not None else 0.0,
            }
        time.sleep(2)

    for g in games:
        bid = ids.get(g["id"])
        if bid and bid in info:
            result[g["id"]] = {"name": g["name"], **info[bid]}

    OUT.write_text(json.dumps({"games": result, "unresolved": unresolved}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(result)} van {len(games)} spellen gevonden; niet gevonden: {unresolved}")


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
