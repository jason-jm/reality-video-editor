#!/usr/bin/env python3
"""Merges `index::translation` lines (indices into msgids.tsv) into app/src/main/resources/i18n/LANG.tsv.

Usage: merge.py LANG batch.txt   — checks each translation keeps the msgid's {n} placeholders and has no Han (except for
CJK targets), and keeps the file sorted by msgid.
"""
import os, re, sys
here = os.path.dirname(__file__)
lang, batch = sys.argv[1], sys.argv[2]
ids = [l.split("\t")[0] for l in open(os.path.join(here, "msgids.tsv"), encoding="utf-8").read().splitlines()]
out = os.path.join(here, "..", "..", "app", "src", "main", "resources", "i18n", f"{lang}.tsv")
os.makedirs(os.path.dirname(out), exist_ok=True)
table = {}
if os.path.exists(out):
    for l in open(out, encoding="utf-8").read().splitlines():
        if "\t" in l and not l.startswith("#"):
            k, v = l.split("\t", 1)
            table[k] = v
PH = re.compile(r"\{(\d+)(?:,|\})")
HAN = re.compile(r"[㐀-鿿]")
problems = 0
for line in open(batch, encoding="utf-8").read().splitlines():
    if not line.strip() or "::" not in line:
        continue
    idx, tr = line.split("::", 1)
    tr = tr.replace("\t", "\\t")
    src = ids[int(idx)]
    need, got = set(PH.findall(src.replace("\\n", " "))), set(PH.findall(tr))
    if need != got:
        print(f"placeholders differ at {idx}: {src!r} -> {tr!r}"); problems += 1
    # A native language name ("日本語") stays itself: written as the source.
    if not tr.strip() or src.count(r"\n") != tr.count(r"\n"):
        print(f"empty translation or changed line breaks at {idx}"); problems += 1
    if lang not in ("ja", "zh-Hant", "zh-Hans") and HAN.search(tr) and not (tr == src and src in ("简体中文", "繁體中文", "日本語")):
        print(f"han left at {idx}: {tr!r}"); problems += 1
    table[src] = tr
if problems:
    sys.exit(f"{lang}: {problems} invalid entries; the catalog was not changed")
with open(out, "w", encoding="utf-8") as f:
    f.write(f"# Reality Video Editor — {lang} translations of the Chinese source (devtools/i18n/msgids.tsv). source<TAB>translation\n")
    for k in sorted(table):
        f.write(f"{k}\t{table[k]}\n")
print(f"{lang}: {len(table)} entries, {problems} problems")
