#!/usr/bin/env python3
"""
Zet deelnemers in de gastenlijst van een weekend en kopieert hun spellen uit een ander weekend.
Draait in GitHub Actions (workflow "Beheer: deelnemers kopiëren").

Omgeving: TARGET_CODE, SOURCE_CODE, NAMES (komma-gescheiden voornamen), APPLY ("1" = echt schrijven).
Spellen gaan mee als de eigenaar in het bronweekend dezelfde voornaam heeft (hoofdletters/accenten negeren).
Wenslijst-spellen en spellen die al in het doelweekend staan (zelfde naam en eigenaar) worden overgeslagen.
"""
import os
import re
import sys
import time
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bgg_sync import Store, enc, http  # noqa: E402


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]", "", s)


def slug(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def find_event(st, code):
    for c in dict.fromkeys([code, code.upper(), code.lower()]):
        try:
            return st.find_event(c)
        except RuntimeError:
            continue
    raise RuntimeError(f"geen weekend met code {code}")


def get_doc(st, path):
    import json
    res = json.loads(http(f"{st.base}/{path}", None, st._h()))
    from bgg_sync import val
    return {k: val(v) for k, v in res.get("fields", {}).items()}


def list_col(st, event, col):
    import json
    from bgg_sync import val
    out, page = [], ""
    while True:
        url = f"{st.base}/events/{event}/{col}?pageSize=300" + (f"&pageToken={page}" if page else "")
        res = json.loads(http(url, None, st._h()))
        for d in res.get("documents", []):
            out.append({"id": d["name"].rsplit("/", 1)[1], **{k: val(v) for k, v in d.get("fields", {}).items()}})
        page = res.get("nextPageToken")
        if not page:
            return out


def put(st, path, data):
    http(f"{st.base}/{path}", {"fields": {k: enc(v) for k, v in data.items()}}, st._h(), "PATCH")


def main():
    apply = os.environ.get("APPLY", "") == "1"
    prune = os.environ.get("PRUNE", "") == "1"
    names = [n.strip() for n in os.environ["NAMES"].split(",") if n.strip()]
    st = Store()
    target = find_event(st, os.environ["TARGET_CODE"])
    source = find_event(st, os.environ.get("SOURCE_CODE", "3GQ4B9"))
    for ev in (target, source):
        st.set_member(ev)
    try:
        tinfo = get_doc(st, f"events/{target}")
        sinfo = get_doc(st, f"events/{source}")
        print(f"Doel:  {tinfo.get('name')} ({target})")
        print(f"Bron:  {sinfo.get('name')} ({source})")
        tguests = list_col(st, target, "guests")
        tgames = list_col(st, target, "games")
        sgames = list_col(st, source, "games")
        have_guest = {norm(g.get("name")) for g in tguests}
        have_game = {(norm(g.get("name")), norm(g.get("owner"))) for g in tgames}
        owners = {}
        for g in sgames:
            o = (g.get("owner") or "").strip()
            if o and not g.get("wishlist") and norm(o) != "wens":
                owners.setdefault(norm(o), o)

        print(f"\nGasten ({len(names)}):")
        new_guests = 0
        for n in names:
            if norm(n) in have_guest:
                print(f"  = {n} (stond er al)")
                continue
            print(f"  + {n}")
            new_guests += 1
            if apply:
                put(st, f"events/{target}/guests/gst-{slug(n)}", {"name": n, "roomId": "", "wants": [], "claimedBy": ""})

        print("\nSpellen:")
        copied = 0
        now = int(time.time() * 1000)
        for n in names:
            mine = [g for g in sgames if norm(g.get("owner")) == norm(n) and not g.get("wishlist")]
            if not mine:
                print(f"  {n}: geen spellen in het bronweekend")
                continue
            todo = [g for g in mine if (norm(g.get("name")), norm(n)) not in have_game]
            print(f"  {n}: {len(todo)} van {len(mine)} — " + ", ".join(g.get("name", "") for g in todo))
            for g in todo:
                copied += 1
                if apply:
                    data = {k: v for k, v in g.items() if k not in ("id", "interested") and v is not None}
                    data.update({"owner": n, "wishlist": False, "interested": [], "createdAt": now, "updatedAt": now})
                    gid = "g-" + slug(g.get("name")) + "-" + slug(n)
                    put(st, f"events/{target}/games/{gid}", data)

        # Opruimen: spellen in het doelweekend van mensen die niet meegaan, en wensen
        keep = {norm(n) for n in names}
        drop = [g for g in tgames if g.get("wishlist") or norm(g.get("owner")) not in keep]
        by_owner = {}
        for g in drop:
            by_owner.setdefault("Wens" if g.get("wishlist") or norm(g.get("owner")) == "wens" else (g.get("owner") or "(geen eigenaar)"), []).append(g.get("name", ""))
        print(f"\nOp te ruimen in het doelweekend ({len(drop)} spellen):")
        for o, lst in sorted(by_owner.items()):
            print(f"  {o}: {len(lst)} — " + ", ".join(sorted(lst)))
        if prune and apply:
            for g in drop:
                http(f"{st.base}/events/{target}/games/{g['id']}", None, st._h(), "DELETE")
        removed = len(drop) if (prune and apply) else 0

        unmatched = sorted(o for k, o in owners.items() if k not in {norm(n) for n in names})
        print("\nEigenaars in het bronweekend die niet in de lijst staan:", ", ".join(unmatched) or "geen")
        print(f"\n{'GESCHREVEN' if apply else 'PROEF (niets geschreven)'}: {new_guests} gasten, {copied} spellen, {removed} verwijderd")
    finally:
        for ev in (target, source):
            try:
                st.delete_member(ev)
            except Exception as e:  # noqa: BLE001
                print("afmelden mislukt:", e, file=sys.stderr)


if __name__ == "__main__":
    main()
