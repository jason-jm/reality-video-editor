#!/usr/bin/env python3
"""Verify localized text on a PICO emulator with a debug build and an existing sample project.

python3 devtools/i18n/check_layout.py --device emulator-5558 --output /tmp/editor-layout
The library, its About and New project sheets, and all five stages are checked per language. Screenshots and the actual Compose
text measurements are saved side by side. Use a dedicated device: the selected project
is opened for viewing. Its previous interface language is restored in a finally block.
"""
import argparse
import json
from itertools import combinations
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid
from xml.etree import ElementTree as ET

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "devtools/tools"))
from export_fixture import DebugApp, wait_for  # noqa: E402

LANGUAGES = ("zh-Hans", "zh-Hant", "en", "ja", "ko", "fr", "de", "es", "ru")
STAGES = {"media": ("媒体池",), "edit": ("时间线设置",),
          "color": ("一级校色",), "audio": ("录制旁白",),
          "deliver": ("渲染队列",)}


def table(tag):
    if tag == "zh-Hans":
        return {}
    lines = (ROOT / f"app/src/main/resources/i18n/{tag}.tsv").read_text().splitlines()
    return dict(line.split("\t", 1) for line in lines if "\t" in line and not line.startswith("#"))


def initial_language(app):
    files = app.shell("run-as", app.package, "ls", "shared_prefs").splitlines()
    if "ui.xml" not in files:
        return "system"
    prefs = app.text("exec-out", "run-as", app.package, "cat", "shared_prefs/ui.xml")
    return ET.fromstring(prefs).findtext("./string[@name='language']") or "system"


def measurement(app, windows=False):
    request = uuid.uuid4().hex[:12]
    app.command("text-layout " + request + (" windows" if windows else ""))
    pid = app.shell("pidof", "-s", app.package)

    def read():
        dump = app.text("logcat", "-d", "--pid", pid, "-v", "raw", "-s", "PrismTextLayout:I")
        records = [json.loads(line) for line in dump.splitlines() if f'"request":"{request}"' in line]
        if records and "count" in records[-1] and records[-1]["count"] == len(records) - 1:
            return records[:-1] or None
        return None
    return wait_for(read, "a complete layout measurement", timeout=7)


def visible(app, words, windows=False):
    measurements = []
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        try:
            measurements = measurement(app, windows)
        except RuntimeError:
            continue
        if set(words) & {n["text"] for n in measurements}:
            return measurements
        time.sleep(0.35)
    raise RuntimeError(f"Screen has none of {words}; visible: {[n['text'] for n in measurements][:35]}")


def missing_messages(app):
    app.command("i18n-missing")
    log = app.text("logcat", "-d", "--pid", app.shell("pidof", "-s", app.package), "-v", "raw", "-s", "PrismI18n:W")
    lines = log.splitlines()
    for i in reversed(range(len(lines))):
        match = re.fullmatch(r"(\d+) untranslated", lines[i])
        if match:
            return lines[i - int(match[1]):i]
    raise RuntimeError("No translation diagnostic receipt")


def capture(app, output, tag, stage, words, media_names):
    items = visible(app, words, stage in {"about", "new", "picker"})
    directory = output / tag
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"{stage}.json").write_text(json.dumps(items, ensure_ascii=False, indent=1))
    png = directory / f"{stage}.png"
    # The compositor can briefly return a valid but wholly black frame during a spatial window transition.
    for _ in range(4):
        time.sleep(0.8)
        subprocess.run(["pico-cli", "capture", "screenshot", "--device", app.serial, "--out", str(png), "--replace"],
                       capture_output=True, check=True, timeout=55)
        with Image.open(png) as image:
            if image.format == "PNG" and min(image.size) >= 600 and image.convert("RGB").resize((64, 64)).getcolors(32) is None:
                break
    else:
        raise RuntimeError(f"Compositor did not supply a nonblank screenshot: {png}")
    # Project bin/filmstrip filenames are deliberately ellipsized; the property remains true in the JSON.
    clipped = [n["text"] for n in items if n["overflow"] and not (n["ellipsis"] and
               any(name in n["text"] for name in media_names))]
    overlaps = []
    for a, b in combinations((n for n in items if n["text"].strip()), 2):
        if a["surface"] != b["surface"]:
            continue
        for left, top, right, bottom in a["ink"]:
            if any(min(right, x2) - max(left, x1) > 2 and
                   min(bottom, y2) - max(top, y1) > max(2, min(bottom - top, y2 - y1) * 0.25)
                   for x1, y1, x2, y2 in b["ink"]):
                overlaps.append([a["text"], b["text"]])
                break
    print(tag, stage, len(items), "labels;", len(clipped), "clipped;", len(overlaps), "overlaps",
          repr(clipped + overlaps)[:350], flush=True)
    return {"clipped": clipped, "overlaps": overlaps}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--languages", nargs="+", choices=LANGUAGES, default=LANGUAGES)
    parser.add_argument("--package", help="Android application ID (defaults to the project setting)")
    parser.add_argument("--adb", default=shutil.which("adb"))
    args = parser.parse_args()
    package = args.package or subprocess.check_output(
        ["zsh", str(ROOT / "devtools/tools/appid.sh")], text=True).strip()
    app = DebugApp(args.adb, args.device, package)
    prior = initial_language(app)
    args.output.mkdir(parents=True, exist_ok=True)
    summary = {}
    missing = {}
    media_names = set()
    filenames = app.shell("run-as", app.package, "ls", "files/projects").splitlines()
    for filename in filenames:
        if filename.endswith(".json"):
            doc = json.loads(app.text("exec-out", "run-as", app.package, "cat", f"files/projects/{filename}"))
            media_names.update(Path(m["name"]).stem for m in doc.get("media", []) if len(Path(m["name"]).stem) >= 4)

    try:
        app.launch()
        app.command("lang en")
        app.library()
        missing_messages(app)
        for tag in args.languages:
            t = table(tag)
            tr = lambda key: t.get(key, key)
            app.command("lang " + tag)
            def record(stage, words):
                clipped = capture(app, args.output, tag, stage, list(map(tr, words)), media_names)
                summary[f"{tag}/{stage}"] = clipped
                (args.output / "overflow.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))
            record("library", ["项目库"])
            for stage, button, marker in (("about", tr("关于 {0}").replace("{0}", "Reality Video Editor"), "Reality Video Editor"),
                                           ("new", tr("新建项目"), tr("创建项目"))):
                nodes = app.wait_controls(lambda ns: ns.get(button), button)
                app.tap(nodes, [button])
                record(stage, [marker])
                app.shell("input", "keyevent", "KEYCODE_BACK")
                app.wait_controls(lambda ns: ns.get(tr("新建项目")), "return to library")
            app.command("open")
            app.wait_controls(lambda ns: tr("返回项目库") in ns, "the open workspace")
            for i, (stage, words) in enumerate(STAGES.items(), 1):
                app.command(f"key KEYCODE_{i}")
                record(stage, words)
            app.command("back")
            visible(app, [tr("项目库")])
            missing[tag] = missing_messages(app)
            (args.output / "untranslated.json").write_text(json.dumps(missing, ensure_ascii=False, indent=2))
    finally:
        app.command("lang " + prior)
    return any(missing.values()) or any(issue for screen in summary.values() for issue in screen.values())


if __name__ == "__main__":
    sys.exit(int(main()))
