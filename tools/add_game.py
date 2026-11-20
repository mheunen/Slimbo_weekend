#!/usr/bin/env python3
"""
Zet een spel in een weekend (of werkt het bij) met BGG-gegevens, en zet ♥ voor opgegeven mensen.
Omgeving: CODE, GAME, OWNER, BGG (nummer, optioneel), NAMES (komma-gescheiden fans), APPLY ("1").
De ♥ komt op de gastenlijst (guests.wants) en, als iemand al met de app meedoet, ook bij het lid.
"""
import os
import sys
import time
import urllib.parse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bgg_sync import Store, enc, http, thing  # noqa: E402
from copy_people import find_event, get_doc, list_col, norm, slug  # noqa: E402


def patch(st, path, data):
    mask = "&".join("updateMask.fieldPaths=" + urllib.parse.quote(k) for k in data)
    http(f"{st.base}/{path}?{mask}", {"fields": {k: enc(v) for k, v in data.items()}}, st._h(), "PATCH")


def main():
    apply = os.environ.get("APPLY", "") == "1"
    name, owner = os.environ["GAME"].strip(), os.environ.get("OWNER", "").strip()
    fans = [n.strip() for n in os.environ.get("NAMES", "").split(",") if n.strip()]
    st = Store()
    ev = find_event(st, os.environ.get("CODE", "J7A3V5"))
    st.set_member(ev)
    try:
        print(f"Weekend: {get_doc(st, f'events/{ev}').get('name')} ({ev})")
        games = list_col(st, ev, "games")
        existing = [g for g in games if norm(g.get("name")) == norm(name)]
        now = int(time.time() * 1000)
        data = {"name": name, "owner": owner, "wishlist": not owner or norm(owner) == "wens", "updatedAt": now}
        bid = os.environ.get("BGG", "").strip()
        if bid:
            b = thing(bid)
            print(f"BGG {bid}: {b['bggName']} ({b['year']}) {b['minPlayers']}-{b['maxPlayers']} sp, {b['durationMin']} min, {b['weight']}, {b['category']}")
            data.update({k: b[k] for k in ("minPlayers", "maxPlayers", "durationMin", "weight", "categories", "link") if b[k]})
            if b["category"]:
                data["category"] = b["category"]
            data.update({"bggId": bid, "infoChecked": True})
        if existing:
            g = existing[0]
            gid = g["id"]
            print(f"Bestaat al: {g.get('name')} (eigenaar {g.get('owner') or '—'}) → bijwerken")
            data["interested"] = sorted(set(g.get("interested") or []) | set(fans))
            if apply:
                patch(st, f"events/{ev}/games/{gid}", data)
        else:
            gid = f"g-{slug(name)}-{slug(owner or 'wens')}"
            data.update({"interested": fans, "notes": "", "video": "", "createdAt": now})
            data.setdefault("minPlayers", 2); data.setdefault("maxPlayers", 4); data.setdefault("durationMin", 60)
            data.setdefault("weight", "MIDDEL"); data.setdefault("category", ""); data.setdefault("link", "")
            print(f"Nieuw: {name} (eigenaar {owner or 'wenslijst'})")
            if apply:
                patch(st, f"events/{ev}/games/{gid}", data)
        guests = list_col(st, ev, "guests")
        members = list_col(st, ev, "members")
        for f in fans:
            gs = [x for x in guests if norm(x.get("name")) == norm(f)]
            if not gs:
                print(f"  ♥ {f}: niet op de gastenlijst gevonden")
                continue
            gu = gs[0]
            if apply:
                patch(st, f"events/{ev}/guests/{gu['id']}", {"wants": sorted(set(gu.get("wants") or []) | {gid})})
            m = next((x for x in members if gu.get("claimedBy") and x["id"] == gu["claimedBy"]), None)
            if m and apply:
                patch(st, f"events/{ev}/members/{m['id']}", {"wants": sorted(set(m.get("wants") or []) | {gid})})
            print(f"  ♥ {f}" + (" (ook in de app)" if m else " (op de gastenlijst; komt mee als ze meedoet)"))
        print("GESCHREVEN" if apply else "PROEF (niets geschreven)")
    finally:
        try:
            st.delete_member(ev)
        except Exception as e:  # noqa: BLE001
            print("afmelden mislukt:", e, file=sys.stderr)


if __name__ == "__main__":
    main()
