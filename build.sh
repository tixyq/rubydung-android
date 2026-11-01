#!/data/data/com.termux/files/usr/bin/bash
# Build a single APK that uses only Android 1.0 (API level 1) APIs.
# minSdkVersion=1, targetSdkVersion=24 (Android 14+ refuses target < 23,
# Android 15+ refuses target < 24), v1+v2+v3 signatures.
set -e

cd "$(dirname "$0")"
P="$PWD"
SDK="${SDK:-/data/data/com.termux/files/usr/tmp/sdk}"
AJAR="$SDK/platforms/android-28/android.jar"
[ -f "$AJAR" ] || AJAR="$SDK/platforms/android-35/android.jar"
[ -f "$AJAR" ] || { echo "android.jar not found"; exit 1; }

BUILD="$P/build"
OUT="$P/out"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$OUT"

echo "[1/6] javac (API 1 discipline, Java 8 bytecode)"
find src -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$AJAR" \
    -d "$BUILD/classes" \
    @"$BUILD/sources.txt"

echo "[2/6] d8 (dex 035, min-api 1)"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
d8 --min-api 1 --lib "$AJAR" --output "$BUILD/dex" @"$BUILD/classes.txt"

echo "[3/6] aapt package"
aapt package -f \
    -M AndroidManifest.xml \
    -S res \
    -A assets \
    -I "$AJAR" \
    -F "$BUILD/app-unsigned.apk"

echo "[4/6] add classes.dex"
(cd "$BUILD/dex" && aapt add "$BUILD/app-unsigned.apk" classes.dex)

echo "[5/6] zipalign"
zipalign -f 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

echo "[6/6] sign"
if [ ! -f debug.keystore ]; then
    keytool -genkeypair -keystore debug.keystore -storepass android \
        -storetype JKS -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -sigalg SHA1withRSA \
        -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
apksigner sign \
    --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version 1 \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT/RubyDung-rd-132211.apk" \
    "$BUILD/app-aligned.apk"

echo
echo "APK: $OUT/RubyDung-rd-132211.apk"
ls -l "$OUT/RubyDung-rd-132211.apk"
apksigner verify --verbose --min-sdk-version 1 "$OUT/RubyDung-rd-132211.apk" | head -8
aapt dump badging "$OUT/RubyDung-rd-132211.apk" | head -4
