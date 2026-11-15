// Renders prism-icon.html's layers to PNGs with a transparent background:
//   node devtools/release/icon/render_icon.mjs <out-dir>
// → layer0.png, layer1.png (1024 × 1024) and composite.png (1424 × 1424). Then IconSdf.java makes the SDF images.
import { spawn } from "node:child_process";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const out = process.argv[2];
const page = "file://" + join(dirname(fileURLToPath(import.meta.url)), "prism-icon.html");
const PORT = 9334;
try { await fetch(`http://127.0.0.1:${PORT}/json/version`); console.error(`port ${PORT} is taken`); process.exit(2); } catch {}
const chrome = spawn("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  ["--headless=new", "--disable-gpu", "--hide-scrollbars", `--remote-debugging-port=${PORT}`, "--window-size=1424,1424",
   "--user-data-dir=/tmp/prism-icon-profile", "about:blank"], { stdio: "ignore" });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let ws, id = 0; const pending = new Map();
for (let i = 0; i < 50 && !ws; i++) {
  try {
    const t = (await (await fetch(`http://127.0.0.1:${PORT}/json`)).json()).find((x) => x.type === "page");
    // Listen for "open" at once: a sleep in between can let it fire unheard.
    if (t) { ws = new WebSocket(t.webSocketDebuggerUrl); await new Promise((r) => ws.addEventListener("open", r)); break; }
  } catch {}
  await sleep(200);
}
ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
const send = (method, params = {}) => new Promise((r) => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
await send("Page.enable");
await send("Emulation.setDefaultBackgroundColorOverride", { color: { r: 0, g: 0, b: 0, a: 0 } });
for (const [mode, size] of [["layer0", 1024], ["layer1", 1024], ["composite", 1424]]) {
  await send("Emulation.setDeviceMetricsOverride", { width: size, height: size, deviceScaleFactor: 1, mobile: false });
  await send("Page.navigate", { url: `${page}#${mode}` });
  await sleep(900);
  await send("Page.reload", { ignoreCache: true });
  await sleep(900);
  const r = await send("Page.captureScreenshot", { format: "png", clip: { x: 0, y: 0, width: size, height: size, scale: 1 } });
  writeFileSync(join(out, `${mode}.png`), Buffer.from(r.result.data, "base64"));
  console.log(mode, size);
}
ws.close(); chrome.kill("SIGKILL");
