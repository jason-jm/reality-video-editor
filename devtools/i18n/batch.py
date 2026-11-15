#!/usr/bin/env python3
"""Prints msgids [start, start+count) of msgids.tsv as `index<TAB>msgid` (untranslated only with --todo LANG)."""
import os, sys
here = os.path.dirname(__file__)
start, count = int(sys.argv[1]), int(sys.argv[2])
todo = sys.argv[sys.argv.index("--todo") + 1] if "--todo" in sys.argv else None
ids = [l.split("\t")[0] for l in open(os.path.join(here, "msgids.tsv"), encoding="utf-8").read().splitlines()]
done = set()
if todo:
    p = os.path.join(here, "..", "..", "app", "src", "main", "resources", "i18n", f"{todo}.tsv")
    if os.path.exists(p):
        done = {l.split("\t")[0] for l in open(p, encoding="utf-8").read().splitlines() if "\t" in l and not l.startswith("#")}
for i in range(start, min(start + count, len(ids))):
    if ids[i] not in done:
        print(f"{i}\t{ids[i]}")
