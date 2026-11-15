"""Render one fixture with the debug app and copy the media identified by its new job."""

import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import uuid


TOOLS = Path(__file__).resolve().parent
LIBRARY = ("项目库", "Project library")
BACK = ("返回项目库", "Back to project library")
ADD = ("加入渲染队列", "Add to render queue")
RENDER = ("全部渲染", "Render all")


def wait_for(read, description, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = read()
        if result:
            return result
        time.sleep(0.4)
    raise RuntimeError(f"Timed out waiting for {description}")


class DebugApp:
    def __init__(self, adb, serial, package):
        self.adb = [adb, "-s", serial]
        self.serial = serial
        self.package = package

    def text(self, *args):
        return subprocess.check_output(self.adb + list(args), text=True, timeout=30).strip()

    def shell(self, *args):
        return self.text("shell", shlex.join(args))

    def command(self, message):
        self.shell("am", "broadcast", "-a", "com.example.prismstudio.AUTOMATION",
                   "-p", self.package, "--es", "cmd", message)

    def launch(self):
        subprocess.run(["pico-cli", "app", "launch", self.package, "--device", self.serial], check=True, timeout=65)

    def controls(self):
        # A spatial window may extend outside the Android display. The debug app reports
        # clickable Compose bounds in its own window coordinates, including those controls.
        # The launcher acknowledgement can precede process creation on a cold start.
        process = subprocess.run([*self.adb, "shell", "pidof", "-s", self.package],
                                 capture_output=True, text=True, timeout=10)
        if process.returncode == 1 and not process.stderr.strip():
            return {}
        process.check_returncode()
        pid = process.stdout.strip()
        def dump():
            return self.text("logcat", "-d", "--pid", pid, "-v", "epoch", "-s", "PrismClick:W").splitlines()
        def ends(lines):
            return [i for i, line in enumerate(lines) if re.search(r"\d+ nodes$", line)]
        previous = dump()
        old = ends(previous)
        marker = previous[old[-1]] if old else None
        # A cold or heavily loaded emulator may delay an ordered broadcast for tens
        # of seconds. Wait for the dump itself, which is the readiness signal.
        self.shell("am", "broadcast", "--async", "-a", "com.example.prismstudio.AUTOMATION",
                   "-p", self.package, "--es", "cmd", "controls")
        def fresh():
            lines = dump()
            end = ends(lines)
            if not end or lines[end[-1]] == marker:
                return None
            nodes = {}
            for line in lines[end[-2] + 1 if len(end) > 1 else 0:end[-1]]:
                fields = line.partition("PrismClick: ")[2].split(" | ")
                if len(fields) < 7:
                    continue
                label = fields[0]
                nodes.setdefault(label, None)
                if fields[2] == "click" and fields[6] == "true":
                    left, top, right, bottom = map(int, re.findall(r"-?\d+", fields[1]))
                    if right > left and bottom > top:
                        nodes[label] = ((left + right) // 2, (top + bottom) // 2)
            return nodes
        # On cold boot the receiver can already be alive before a Compose root exists.
        # A controls request is read-only. Let the caller request it again once the window appears.
        for _ in range(5):
            time.sleep(0.25)
            nodes = fresh()
            if nodes:
                return nodes
        return {}

    def wait_controls(self, predicate, description):
        def match():
            nodes = self.controls()
            return nodes if predicate(nodes) else None
        return wait_for(match, description)

    def tap(self, nodes, labels):
        point = next(nodes[label] for label in labels if nodes.get(label))
        self.command(f"tap {point[0]} {point[1]}")

    def library(self):
        nodes = self.wait_controls(lambda n: any(k in n for k in LIBRARY + BACK), "the project window")
        if any(k in nodes for k in BACK):
            self.command("back")  # Finish saving through the same path as the title bar.
            self.wait_controls(lambda n: any(k in n for k in LIBRARY), "the saved project library")


def copy_output(adb, job, kind, destination):
    if job.get("status") != "DONE":
        raise RuntimeError(f"Job {job.get('id')} is {job.get('status')}: {job.get('error') or 'no completed output'}")
    uri = job.get("output") or ""
    if not re.fullmatch(rf"content://media/[\w-]+/{kind}/media/[0-9]+", uri):
        raise ValueError(f"Job did not record a {kind} MediaStore item: {uri!r}")
    expected = job.get("bytes")
    if not isinstance(expected, int) or expected <= 0:
        raise ValueError("Job did not record a positive output size")
    # Read on the device first. The Android content command can write an exception to
    # stdout and still exit successfully for an unavailable item.
    with tempfile.TemporaryDirectory(dir=destination.parent, prefix=".dango-export-") as folder:
        part = Path(folder) / "output"
        with part.open("wb") as stream:
            subprocess.run([*adb, "exec-out", "content", "read", "--uri", uri], stdout=stream, check=True, timeout=1200)
        if part.stat().st_size != expected:
            raise RuntimeError(f"Job recorded {expected} bytes, received {part.stat().st_size}")
        with part.open("rb") as media:
            header = media.read(12)
        valid = header[4:8] == b"ftyp" or (kind == "audio" and header[:4] == b"RIFF" and header[8:12] == b"WAVE")
        if not valid:
            raise RuntimeError(f"MediaStore did not return an MP4, M4A or WAV file for {job['id']}")
        os.replace(part, destination)
    print(f"Exported job {job['id']} from {uri} ({expected} bytes) to {destination}", flush=True)


def run_fixture(app, fixture, kind, destination):
    token = uuid.uuid4().hex
    project_id = "p-export-" + token
    title = "Export QA " + token[:12]
    filename = f"files/projects/{project_id}.json"
    # Old fixture queues are history. Start a fresh queue in a separate library entry
    # while retaining all source clips, URIs, timelines and requested render settings.
    fixture.update(id=project_id, name=title, modifiedAt=int(time.time() * 1000), jobs=[])
    payload = json.dumps(fixture, ensure_ascii=False).encode()
    def private(*args):
        return app.shell("run-as", app.package, *args)
    def jobs():
        doc = json.loads(app.text("exec-out", "run-as", app.package, "cat", filename))
        if doc["id"] != project_id:
            raise RuntimeError("The test document changed identity")
        return doc["jobs"]

    private("id")
    app.launch()
    app.library()
    app.shell("am", "force-stop", app.package)
    private("mkdir", "-p", "files/projects")
    # The generated basename is also the app's project id. Only publish a fully written file.
    transaction = f"cat > {filename}.part && mv {filename}.part {filename}"
    try:
        subprocess.run([*app.adb, "shell", shlex.join(["run-as", app.package, "sh", "-c", transaction])],
                       input=payload, check=True, timeout=45)
        if subprocess.check_output([*app.adb, "exec-out", "run-as", app.package, "cat", filename]) != payload:
            raise RuntimeError("The fixture was not installed intact")
        app.launch()
        def card(nodes):
            return [k for k, point in nodes.items() if point and k.startswith((title + ",", title + "，"))]
        library = app.wait_controls(card, title)
        app.tap(library, card(library))
        if not any(k in app.controls() for k in BACK):
            app.command("open")
        app.wait_controls(lambda n: title in n and any(k in n for k in BACK), "the exact fixture workspace")
        app.command("key KEYCODE_5")
        app.tap(app.wait_controls(lambda n: any(n.get(k) for k in ADD), "Add to render queue"), ADD)
        queued = wait_for(jobs, "the new render job")
        if len(queued) != 1:
            raise ValueError(f"One output path was supplied for {len(queued)} jobs; select single-file rendering in the fixture")
        job_id = queued[0]["id"]
        app.tap(app.wait_controls(lambda n: any(n.get(k) for k in RENDER), "Render all"), RENDER)
        def finished():
            job = next((j for j in jobs() if j["id"] == job_id), None)
            if job is None:
                raise RuntimeError(f"Job {job_id} was removed")
            return job if job["status"] in {"DONE", "PARTIAL", "FAILED"} else None
        job = wait_for(finished, f"job {job_id} to finish", timeout=1200)
        copy_output(app.adb, job, kind, destination)
    finally:
        # Stop only this debug package after the fixture has run. Removing our UUID and
        # its automatic backups does not touch the source project or its media.
        app.shell("am", "force-stop", app.package)
        private("rm", "-f", filename, filename + ".part")
        private("rm", "-rf", f"files/backups/{project_id}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("audio", "video"))
    parser.add_argument("project", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    fixture = json.loads(args.project.read_text())
    if not isinstance(fixture, dict) or not isinstance(fixture.get("timeline"), dict):
        raise ValueError("Expected a project with a timeline")
    if fixture.get("render", {}).get("video", True) != (args.kind == "video"):
        raise ValueError(f"Fixture render settings do not request {args.kind}")
    if not args.output.parent.is_dir() or args.output.is_dir() or args.output.resolve() == args.project.resolve():
        raise ValueError("Choose an output file in an existing directory, distinct from the input")
    adb = shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")
    android, pico = os.environ.get("ANDROID_SERIAL"), os.environ.get("PICO_CLI_DEVICE")
    if android and pico and android != pico:
        raise ValueError("ANDROID_SERIAL and PICO_CLI_DEVICE specify different targets")
    serial = android or pico or subprocess.check_output([adb, "get-serialno"], text=True).strip()
    package = os.environ.get("DANGOCUT_TEST_PACKAGE") or subprocess.check_output(["zsh", str(TOOLS / "appid.sh")], text=True).strip()
    if not re.fullmatch(r"[A-Za-z][\w]*(?:\.[A-Za-z][\w]*)+", package):
        raise ValueError("Invalid Android application ID")
    # Only one shell can drive this app's window at a time.
    key = hashlib.sha256(f"{serial}/{package}".encode()).hexdigest()[:20]
    with (Path(tempfile.gettempdir()) / f"dangocut-export-{key}.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        run_fixture(DebugApp(adb, serial, package), fixture, args.kind, args.output)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        sys.exit(str(error))
