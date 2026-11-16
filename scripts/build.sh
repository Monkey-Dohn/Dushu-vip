#!/usr/bin/env bash
# 无 Gradle 构建 LSPosed 模块 APK：aapt2 + javac + d8 + zipalign + apksigner
# 用法: bash build/build.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PROJ="$(cd "$HERE/.." && pwd)"
TOOLS="${TOOLS:-$(cd "$PROJ/../tools" && pwd)}"

JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk-17.0.13+11}"
export JAVA_HOME
JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
KEYTOOL="$JAVA_HOME/bin/keytool"

BT="$TOOLS/bt34/android-14"
AAPT2="$BT/aapt2.exe"
D8="$BT/d8.bat"
ZIPALIGN="$BT/zipalign.exe"
APKSIGNER="$BT/apksigner.bat"
ANDROID_JAR="$TOOLS/plat/android-34/android.jar"
XPOSED_JAR="${XPOSED_JAR:-$TOOLS/XposedBridgeAPI-82.jar}"

OUT="$PROJ/build"
DIST="$PROJ/dist"
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$DIST"

for f in "$AAPT2" "$ANDROID_JAR" "$XPOSED_JAR"; do
  [ -e "$f" ] || { echo "缺少: $f（先跑 tools/fetch-tools.sh）"; exit 1; }
done

echo "== [1/7] aapt2 compile 资源"
"$AAPT2" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "== [2/7] aapt2 link 生成资源 APK"
"$AAPT2" link -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 21 --target-sdk-version 33 \
  --auto-add-overlay

echo "== [3/7] javac 编译（source/target 8，bootclasspath=android.jar）"
SRC=$(find "$PROJ/src" "$OUT/gen" -name '*.java')
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -nowarn \
  -bootclasspath "$ANDROID_JAR" \
  -cp "$XPOSED_JAR" \
  -d "$OUT/classes" $SRC 2>&1 | grep -v "^警告\|^warning\|bootstrap class path" || true

echo "== [4/7] d8 生成 classes.dex"
CLASSES=$(find "$OUT/classes" -name '*.class')
"$D8" --release --min-api 21 --lib "$ANDROID_JAR" --lib "$XPOSED_JAR" --output "$OUT/dex" $CLASSES

echo "== [5/7] 打包 dex + assets 进 APK"
cp "$OUT/base.apk" "$OUT/app.apk"
python - "$OUT/app.apk" "$OUT/dex/classes.dex" "$PROJ/assets/xposed_init" <<'PY'
import sys, zipfile
apk, dex, xinit = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
    z.write(xinit, 'assets/xposed_init')
print('   已写入 classes.dex + assets/xposed_init')
PY

echo "== [6/7] zipalign"
"$ZIPALIGN" -f -p 4 "$OUT/app.apk" "$OUT/app-aligned.apk"

echo "== [7/7] 签名"
KS="$TOOLS/debug.keystore"
if [ ! -f "$KS" ]; then
  "$KEYTOOL" -genkeypair -v -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
FINAL="$DIST/DywVipUnlock-v1.0.0.apk"
cp "$OUT/app-aligned.apk" "$FINAL"
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$FINAL" "$OUT/app-aligned.apk"
"$APKSIGNER" verify --print-certs "$FINAL" | head -8

echo
echo "产物: $FINAL"
ls -la "$FINAL"
