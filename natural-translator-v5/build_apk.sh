#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/.build"
DIST="$ROOT/dist"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [[ -z "$SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME not set" >&2
  exit 2
fi

BT="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
PLATFORM="$(find "$SDK_ROOT/platforms" -mindepth 1 -maxdepth 1 -type d -name 'android-*' | sort -V | tail -1)/android.jar"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$PLATFORM"; do
  [[ -e "$f" ]] || { echo "Missing Android SDK component: $f" >&2; exit 3; }
done

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$DIST"

"$BT/aapt2" link   -o "$BUILD/base.apk"   -I "$PLATFORM"   --manifest "$ROOT/app/src/main/AndroidManifest.xml"   --min-sdk-version 29   --target-sdk-version 35   --version-code 5   --version-name 5.0

find "$ROOT/app/src/main/java" -name '*.java' -print0 |   xargs -0 javac -encoding UTF-8 -source 8 -target 8 -classpath "$PLATFORM" -d "$BUILD/classes"

jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$BT/d8" --release --min-api 29 --lib "$PLATFORM" --output "$BUILD/dex" "$BUILD/classes.jar"

cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q "$BUILD/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$DIST/Natural-Translator-AI-Rector-v5-UNSIGNED.apk"

echo "Built: $DIST/Natural-Translator-AI-Rector-v5-UNSIGNED.apk"
sha256sum "$DIST/Natural-Translator-AI-Rector-v5-UNSIGNED.apk"
