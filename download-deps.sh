#!/bin/bash
# 下载构建所需的第三方依赖：Vosk 引擎、JNA、中文语音模型、Android SDK
set -e
cd "$(dirname "$0")"

echo "== 1. Vosk Android 引擎 =="
mkdir -p vosk && cd vosk
if [ ! -f vosk-android.aar ]; then
  curl -L -o vosk-android.aar \
    https://repo1.maven.org/maven2/com/alphacephei/vosk-android/0.3.47/vosk-android-0.3.47.aar
fi
if [ ! -f jna.aar ]; then
  curl -L -o jna.aar https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.aar
fi
[ -d aar ] || (mkdir -p aar && cd aar && unzip -oq ../vosk-android.aar)
[ -d jna ] || (mkdir -p jna && cd jna && unzip -oq ../jna.aar)

echo "== 2. 中文小模型（约 42MB） =="
if [ ! -d ../assets/model ]; then
  [ -f model-cn.zip ] || curl -L -o model-cn.zip \
    https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip
  rm -rf vosk-model-small-cn-0.22
  unzip -q model-cn.zip
  mkdir -p ../assets
  mv vosk-model-small-cn-0.22 ../assets/model
fi

echo "== 3. Android SDK（platform 34 + build-tools 34） =="
cd ..
mkdir -p sdk && cd sdk
if [ ! -d android-34 ]; then
  curl -L -o platform.zip https://mirrors.cloud.tencent.com/AndroidSDK/platform-34-ext7_r03.zip
  unzip -q -o platform.zip
fi
if [ ! -d android-14 ]; then
  curl -L -o build-tools.zip https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r34-linux.zip
  unzip -q -o build-tools.zip
fi

echo "依赖就绪，可以运行 ./build.sh 打包"
