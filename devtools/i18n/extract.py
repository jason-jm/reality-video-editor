#!/usr/bin/env python3
"""Extracts the app's user-facing Chinese strings as translation templates (gettext-style msgids).

Each Kotlin string literal holding Chinese becomes a template: literal text, with every `$name` / `${expr}` replaced by
{0}, {1}, ... in order (expressions holding literals of their own yield those too). Log lines and comments are skipped.
Writes devtools/i18n/msgids.tsv: msgid <TAB> first file:line. The app translates at display time (I18n.translate), so
the Chinese stays in the code as the key.

Usage: python3 devtools/i18n/extract.py [--check]   (--check: exit 1 if msgids.tsv is out of date)
"""
import os, re, sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "java")
OUT = os.path.join(os.path.dirname(__file__), "msgids.tsv")
HAN = re.compile(r"[\u3400-\u9fff\uff00-\uffef]")
IDENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def escape(s):
    return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")


class Lexer:
    """Walks Kotlin source, yielding (template, line) for every string literal (nested ones in ${...} too)."""

    def __init__(self, src):
        self.s, self.i, self.n = src, 0, len(src)
        self.found = []

    def line(self, i):
        return self.s.count("\n", 0, i) + 1

    def code(self, stop_at_brace=False):
        depth = 0
        while self.i < self.n:
            c = self.s[self.i]
            nxt = self.s[self.i + 1] if self.i + 1 < self.n else ""
            if c == "/" and nxt == "/":
                j = self.s.find("\n", self.i)
                self.i = self.n if j < 0 else j
            elif c == "/" and nxt == "*":
                self.block_comment()
            elif c == '"':
                if self.s.startswith('"""', self.i):
                    self.raw_string()
                else:
                    self.string()
            elif c == "'":
                self.char_literal()
            elif c == "{":
                depth += 1
                self.i += 1
            elif c == "}":
                if stop_at_brace and depth == 0:
                    self.i += 1
                    return
                depth -= 1
                self.i += 1
            else:
                self.i += 1

    def block_comment(self):
        depth = 0
        while self.i < self.n:
            if self.s.startswith("/*", self.i):
                depth += 1
                self.i += 2
            elif self.s.startswith("*/", self.i):
                depth -= 1
                self.i += 2
                if depth == 0:
                    return
            else:
                self.i += 1

    def char_literal(self):
        self.i += 1
        while self.i < self.n and self.s[self.i] != "'":
            self.i += 2 if self.s[self.i] == "\\" else 1
        self.i += 1

    def raw_string(self):
        start = self.i
        self.i += 3
        end = self.s.find('"""', self.i)
        self.i = self.n if end < 0 else end + 3

    def string(self):
        start = self.i
        self.i += 1
        parts, args = [], 0
        ESC = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", '"': '"', "'": "'", "\\": "\\", "$": "$"}
        while self.i < self.n:
            c = self.s[self.i]
            if c == "\\":
                e = self.s[self.i + 1]
                if e == "u":
                    parts.append(chr(int(self.s[self.i + 2:self.i + 6], 16)))
                    self.i += 6
                else:
                    parts.append(ESC.get(e, e))
                    self.i += 2
            elif c == '"':
                self.i += 1
                break
            elif c == "$" and self.s.startswith("${", self.i):
                self.i += 2
                self.code(stop_at_brace=True)
                parts.append("{%d}" % args)
                args += 1
            elif c == "$" and IDENT.match(self.s, self.i + 1):
                m = IDENT.match(self.s, self.i + 1)
                self.i = m.end()
                parts.append("{%d}" % args)
                args += 1
            else:
                parts.append(c)
                self.i += 1
        text = "".join(parts)
        # printf specifiers (String.format) are variable parts too: numbered after the template's own.
        spec = re.compile(r"%(?:\d+\$)?[-+ 0#,(]*\d*(?:\.\d+)?[sdfxXc]")
        def number(m, counter=[args]):
            k = counter[0]
            counter[0] += 1
            return "{%d}" % k
        text = spec.sub(number, text)
        line_start = self.s.rfind("\n", 0, start) + 1
        line_end = self.s.find("\n", start)
        source_line = self.s[line_start:line_end if line_end >= 0 else self.n]
        if HAN.search(text) and "Log." not in source_line:
            self.found.append((text, self.line(start)))


def extract(root=ROOT):
    seen = {}
    for base, _, files in os.walk(root):
        # The translator's own tables (separators, punctuation) are not text to translate.
        if base.replace(os.sep, "/").endswith("domain/i18n"):
            continue
        for f in sorted(files):
            if not f.endswith(".kt"):
                continue
            path = os.path.join(base, f)
            lex = Lexer(open(path, encoding="utf-8").read())
            lex.code()
            rel = os.path.relpath(path, root)
            for text, line in lex.found:
                seen.setdefault(text, f"{rel}:{line}")
    return seen


if __name__ == "__main__":
    seen = extract()
    body = "".join(f"{escape(k)}\t{v}\n" for k, v in sorted(seen.items()))
    if "--check" in sys.argv:
        old = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        strip = lambda t: sorted(l.split("\t")[0] for l in t.splitlines())
        if strip(old) != strip(body):
            print("msgids.tsv is out of date: run devtools/i18n/extract.py")
            sys.exit(1)
        print("msgids up to date:", len(seen))
    else:
        open(OUT, "w", encoding="utf-8").write(body)
        print("wrote", OUT, len(seen), "msgids")
