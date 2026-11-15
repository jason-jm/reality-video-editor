#!/usr/bin/env python3
"""The open-source notices the About sheet shows, from what the release build actually ships.

Reads `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, looks each component's license up in its POM
(or its parent POM) in the Gradle cache, and writes app/src/main/resources/licenses/:
  libraries.tsv   group, artifact, version, license id (apache-2.0 / mit / pico-sdk), one component a line
  apache-2.0.txt  the Apache License 2.0 as Guava ships it (META-INF/LICENSE)
  mit-checker-qual.txt  the Checker Framework qualifiers' MIT notice (META-INF/LICENSE.txt)
Run it from the project root before a release; it fails on a component whose license it does not know.
"""
import glob
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app/src/main/resources/licenses")
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")


def components():
    text = subprocess.run(["./gradlew", ":app:dependencies", "--configuration", "releaseRuntimeClasspath", "-q"],
                          cwd=ROOT, capture_output=True, text=True, check=True).stdout
    found = set()
    for m in re.finditer(r"([\w.-]+):([\w.-]+):(\d[^\s()]*)(?: -> (\d[^\s()]*))?", text):
        g, a, v = m.group(1), m.group(2), m.group(4) or m.group(3)
        if a == "bom":
            continue
        found.add((g, a, v))
    return sorted(found)


def pom_licenses(g, a, v, depth=0):
    poms = glob.glob(f"{CACHE}/{g}/{a}/{v}/*/*.pom")
    if not poms:
        return []
    s = open(poms[0], encoding="utf-8", errors="ignore").read()
    names = re.findall(r"<license>\s*<name>([^<]+)</name>", s)
    if names or depth > 2:
        return names
    m = re.search(r"<parent>.*?<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>\s*<version>([^<]+)</version>", s, re.S)
    return pom_licenses(m.group(1), m.group(2), m.group(3), depth + 1) if m else []


def license_id(g, names):
    joined = " ".join(names).lower()
    if "apache" in joined:
        return "apache-2.0"
    if "mit" in joined.split() or "mit license" in joined:
        return "mit"
    if g.startswith("com.pico.") and not names:
        return "pico-sdk"  # PICO's own SDK modules name no license: used under the PICO developer agreement
    return None


def jar_entry(pattern, entry):
    jars = [j for j in glob.glob(f"{CACHE}/{pattern}") if not j.endswith("-sources.jar")]
    if not jars:
        sys.exit(f"no jar for {pattern}")
    with zipfile.ZipFile(sorted(jars)[-1]) as z:
        return z.read(entry).decode("utf-8")


def main():
    os.makedirs(OUT, exist_ok=True)
    rows, unknown = [], []
    for g, a, v in components():
        lid = license_id(g, pom_licenses(g, a, v))
        if lid is None:
            unknown.append(f"{g}:{a}:{v}")
        rows.append(f"{g}\t{a}\t{v}\t{lid}")
    if unknown:
        sys.exit("unknown licenses: " + ", ".join(unknown))
    with open(os.path.join(OUT, "libraries.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    with open(os.path.join(OUT, "apache-2.0.txt"), "w", encoding="utf-8") as f:
        f.write(jar_entry("com.google.guava/guava/*/*/guava-*.jar", "META-INF/LICENSE"))
    with open(os.path.join(OUT, "mit-checker-qual.txt"), "w", encoding="utf-8") as f:
        f.write(jar_entry("org.checkerframework/checker-qual/*/*/checker-qual-*.jar", "META-INF/LICENSE.txt"))
    counts = {}
    for r in rows:
        counts[r.rsplit("\t", 1)[1]] = counts.get(r.rsplit("\t", 1)[1], 0) + 1
    print(f"{len(rows)} components: {counts}")


if __name__ == "__main__":
    main()
