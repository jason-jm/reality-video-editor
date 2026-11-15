#!/bin/zsh
# Release preflight (release round R4): checks a built APK against what the PICO Store and the release checklist need.
#   zsh devtools/release/preflight.sh <apk> [--qa]
# --qa allows what the qa build has by design (the .qa / com.example package, the debug signing key).
# Exits 1 when a check fails; prints every check either way.
if (( $# < 1 || $# > 2 )) || [[ $# == 2 && $2 != --qa ]]; then
    echo "usage: zsh devtools/release/preflight.sh <apk> [--qa]" >&2; exit 2
fi
APK=$1; QA=$2
ROOT=${0:A:h:h:h}
sdk_root=${ANDROID_HOME:-$HOME/Library/Android/sdk}
toolsVersion=$(sed -nE 's/^[[:space:]]*buildToolsVersion = "([^"]+)"$/\1/p' "$ROOT/app/build.gradle.kts")
[[ -n $toolsVersion ]] || { echo "cannot determine the project's Android buildToolsVersion"; exit 2; }
B="$sdk_root/build-tools/$toolsVersion"
[[ -f $APK ]] || { echo "no APK: $APK"; exit 2; }
[[ -x $B/aapt2 && -x $B/apksigner && -x $B/zipalign ]] || { echo "Android build tools unavailable: $B"; exit 2; }
fail=0
ok() { echo "  ok    $1"; }
bad() { echo "  FAIL  $1"; fail=1; }
note() { echo "  note  $1"; }

TREE=$("$B/aapt2" dump xmltree "$APK" --file AndroidManifest.xml) || { echo "cannot read APK manifest"; exit 1; }
BADGING=$("$B/aapt2" dump badging "$APK") || { echo "cannot read APK package information"; exit 1; }
PKG=$(echo "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
VC=$(echo "$BADGING" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)
VN=$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
[[ -n $PKG && -n $VC && -n $VN ]] || { echo "missing APK package or version"; exit 1; }
echo "$APK: $PKG $VN ($VC), $(( $(stat -f %z "$APK") / 1024 )) KB"
projectId=$(zsh "$ROOT/devtools/tools/appid.sh")
[[ -n $projectId ]] || { echo "cannot determine the project's applicationId"; exit 2; }
expectedId=$projectId
[[ $QA == --qa ]] && expectedId="$projectId.qa"
if [[ $PKG == "$expectedId" ]]; then ok "application ID $PKG"
else bad "application ID: expected $expectedId, found $PKG"; fi

productName=$(sed -nE 's/^val productName = "([^"]+)".*/\1/p' "$ROOT/app/build.gradle.kts")
if [[ -n $productName ]] && awk -F"'" -v expected="$productName" '
    /^application-label(:|-)/ {
        if ($1 == "application-label:") hasDefault = 1
        if ($2 != expected) wrong = 1
    }
    END { exit !(hasDefault && !wrong) }
' <<< "$BADGING"; then
    ok "application labels in all locales: $productName"
else
    bad "expected application label '$productName' in every locale"
fi

# The PICO Store rejects any app whose manifest carries the experimental-API flag (SpatialUI's design library adds it).
if echo "$TREE" | grep -q "pico.spatial.use_experimental_api"; then bad "manifest has pico.spatial.use_experimental_api (PICO Store rejects it)"; else ok "no experimental-API flag"; fi
# The debug-only automation never ships.
if echo "$TREE" | grep -qE "AutomationReceiver|AutomationInstaller"; then bad "debug automation components in the manifest"; else ok "no debug automation"; fi
if echo "$BADGING" | grep -q "^application-debuggable"; then bad "android:debuggable is on"; else ok "not debuggable"; fi
# Exported components (R23): only the launcher activity, and receivers a system permission guards. Compose's
# PreviewActivity (from ui-tooling in a release build) was exported, letting any app run a composable by name.
EXPORTED=$(python3 - "$APK" "$B/aapt2" <<'PY'
import re, subprocess, sys
tree = subprocess.run([sys.argv[2], "dump", "xmltree", sys.argv[1], "--file", "AndroidManifest.xml"], capture_output=True, text=True, check=True).stdout
out = []
for block in re.split(r"\n\s*E: (?=activity|service|receiver|provider)", tree)[1:]:
    head = block
    if not re.search(r"exported\(0x01010010\)=true", head.split("E: intent-filter")[0]):
        continue
    name = re.search(r'android:name\(0x01010003\)="([^"]+)"', head).group(1)
    guarded = re.search(r'android:permission\(0x01010006\)="android\.permission\.(DUMP|BIND_[A-Z_]+)"', head.split("E: intent-filter")[0])
    launcher = "android.intent.category.LAUNCHER" in head
    if not (guarded or launcher):
        out.append(name.rsplit(".", 1)[-1])
print(" ".join(out))
PY
) || { echo "cannot inspect exported components"; exit 1; }
if [[ -n $EXPORTED ]]; then bad "exported without protection: $EXPORTED"; else ok "exported: the launcher and permission-guarded receivers only"; fi
# The privacy policy says the app never goes online.
if echo "$BADGING" | grep -q "uses-permission: name='android.permission.INTERNET'"; then bad "INTERNET permission (the privacy policy says the app never goes online)"; else ok "no INTERNET permission"; fi
if echo "$BADGING" | grep -q "^native-code: 'arm64-v8a'$"; then ok "native code arm64-v8a only"; else bad "expected arm64-v8a only"; fi
if "$B/zipalign" -c -P 16 4 "$APK"; then ok "16 KB ZIP alignment"; else bad "invalid APK alignment"; fi
for field in minSdk targetSdk; do
    expected=$(sed -nE "s/^[[:space:]]*$field = ([0-9]+)$/\\1/p" "$ROOT/app/build.gradle.kts")
    actual=$(echo "$BADGING" | sed -nE "s/^${field}Version:'([0-9]+)'.*/\\1/p")
    if [[ -n $expected && $actual == $expected ]]; then ok "$field $actual"; else bad "$field expected '$expected', found '$actual'"; fi
done
expected=$(sed -nE 's/^spatialBom = "([^"]+)"$/\1/p' "$ROOT/gradle/libs.versions.toml")
actual=$(echo "$TREE" | awk '/android:name\([^)]*\)="pico\.spatial\.sdk_version_string"/ { seen=1; next } /E: / { seen=0 } seen && /android:value\([^)]*\)="/ {split($0, a, "\""); print a[2]; seen=0}')
if [[ -n $expected && $actual == $expected ]]; then ok "PICO Spatial SDK $actual"; else bad "PICO SDK expected '$expected', found '$actual'"; fi
# The open-source notices travel with the app.
if unzip -p "$APK" licenses/libraries.tsv | cmp -s - "$ROOT/app/src/main/resources/licenses/libraries.tsv"; then ok "open-source notices match the release inventory"; else bad "missing or stale licenses/libraries.tsv"; fi
# Identity and signing: placeholders are fine for the qa build only.
if certs=$("$B/apksigner" verify --print-certs "$APK" 2>/dev/null); then
    CERT=$(echo "$certs" | grep "certificate DN" | head -1)
else
    CERT=""
fi
if [[ $PKG == com.example.* ]]; then [[ $QA == --qa ]] && note "placeholder package $PKG (qa)" || bad "placeholder package $PKG (stores refuse com.example)"; else ok "package $PKG"; fi
if [[ -z $CERT ]]; then bad "APK is not signed"
elif [[ $CERT == *"Android Debug"* ]]; then [[ $QA == --qa ]] && note "debug signing key (qa)" || bad "signed with the debug key"
else ok "signed: ${CERT#*: }"; fi
[[ $fail == 0 ]] && echo "preflight: PASS" || echo "preflight: FAIL"
exit $fail
