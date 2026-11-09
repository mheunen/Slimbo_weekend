#!/usr/bin/env python3
"""
Verwijdert wenslijst-spellen waar niemand interesse in heeft getoond:
geen ♥ van een deelnemer (members.wants), geen ♥ op de gastenlijst (guests.wants)
en geen namen in het veld 'interested'. Omgeving: CODE, APPLY ("1" = echt verwijderen).
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bgg_sync import Store, http  # noqa: E402
from copy_people import find_event, get_doc, list_col, norm  # noqa: E402


def main():
    apply = os.environ.get("APPLY", "") == "1"
    st = Store()
    ev = find_event(st, os.environ.get("CODE", "J7A3V5"))
    st.set_member(ev)
    try:
        print(f"Weekend: {get_doc(st, f'events/{ev}').get('name')} ({ev})")
        games = list_col(st, ev, "games")
        members = [m for m in list_col(st, ev, "members") if m.get("id") != st.uid]
        guests = list_col(st, ev, "guests")
        wanted = {}
        for p in members + guests:
            for gid in p.get("wants") or []:
                wanted.setdefault(gid, set()).add(p.get("name") or "?")
        wishes = [g for g in games if g.get("wishlist") or norm(g.get("owner")) == "wens"]
        keep, drop = [], []
        for g in sorted(wishes, key=lambda x: (x.get("name") or "").lower()):
            fans = wanted.get(g["id"], set()) | set(g.get("interested") or [])
            (keep if fans else drop).append((g, sorted(fans)))
        print(f"\nWenslijst: {len(wishes)} spellen")
        print(f"\nBlijft staan ({len(keep)}):")
        for g, fans in keep:
            print(f"  {g.get('name')} — {', '.join(fans)}")
        print(f"\nWeg ({len(drop)}):")
        for g, _ in drop:
            print(f"  {g.get('name')}")
            if apply:
                http(f"{st.base}/events/{ev}/games/{g['id']}", None, st._h(), "DELETE")
        print(f"\n{'VERWIJDERD' if apply else 'PROEF (niets verwijderd)'}: {len(drop)}")
    finally:
        try:
            st.delete_member(ev)
        except Exception as e:  # noqa: BLE001
            print("afmelden mislukt:", e, file=sys.stderr)


if __name__ == "__main__":
    main()
