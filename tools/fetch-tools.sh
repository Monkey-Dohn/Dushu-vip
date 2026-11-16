#!/usr/bin/env bash
# 一键取齐构建所需工具（Windows / Git-Bash 实测通过）
# 用法: bash tools/fetch-tools.sh [工具根目录, 默认 ../../tools]
set -euo pipefail
ROOT="${1:-$(cd "$(dirname "$0")/../.." && pwd)/tools}"
mkdir -p "$ROOT" && cd "$ROOT"

# 1) apktool
[ -f apktool.jar ] || curl -sL -o apktool.jar \
  https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar

# 2) jadx（需要 JDK 17）
[ -d jadx ] || { curl -sL -o jadx.zip \
  https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip; unzip -oq jadx.zip -d jadx; }

# 3) JDK 17
[ -d jdk-17.0.13+11 ] || { curl -sL -o jdk17.zip \
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_windows_hotspot_17.0.13_11.zip"; unzip -oq jdk17.zip; }

# 4) Android build-tools 34 + platform 34（android.jar）
[ -d bt34 ] || { curl -sL -o bt34.zip https://dl.google.com/android/repository/build-tools_r34-windows.zip; unzip -oq bt34.zip -d bt34; }
[ -d plat ] || { curl -sL -o platform-34.zip https://dl.google.com/android/repository/platform-34-ext7_r03.zip; unzip -oq platform-34.zip -d plat; }

# 5) Xposed legacy API 82（maven 镜像比 api.xposed.info 稳）
[ -f XposedBridgeAPI-82.jar ] || curl -sL -o XposedBridgeAPI-82.jar \
  https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar

echo "OK -> $ROOT"
