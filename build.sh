#!/bin/bash
set -e
cd /root/workspace/voicecalc
SDK=/root/workspace/voicecalc/sdk
JAR=$SDK/android-34/android.jar
D8=$SDK/android-14/d8
AAPT=/usr/bin/aapt
VOSK=/root/workspace/voicecalc/vosk/aar/classes.jar
JNA=/root/workspace/voicecalc/vosk/jna/classes.jar
SHERPA=/root/workspace/voicecalc/sherpa/sherpa-onnx.jar
KOTLIN=/root/workspace/voicecalc/sherpa/kotlin-stdlib.jar

rm -rf build gen classes
mkdir -p build gen classes

echo "== 1. 编译资源 + assets =="
ASSET=""
if [ -d assets ]; then ASSET="-A assets"; fi
$AAPT package -f -m -J gen -M AndroidManifest.xml -S res -I "$JAR" $ASSET -F build/app.unsigned.apk

echo "== 2. 编译 Java =="
find src gen -name "*.java" > build/sources.txt
if ! javac -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath "$JAR" \
    -classpath "$VOSK:$JNA:$SHERPA:$KOTLIN" -d classes @build/sources.txt > build/javac.log 2>&1; then
  echo "!! Java 编译失败"; cat build/javac.log; exit 1
fi
echo "class 文件: $(find classes -name '*.class' | wc -l) 个"

echo "== 3. dex（含 vosk / jna） =="
$D8 --release --lib "$JAR" --min-api 21 --output build \
  $(find classes -name "*.class" ! -name "R.class" ! -name "R\$*.class") "$VOSK" "$JNA" "$SHERPA" "$KOTLIN"
ls -l build/classes.dex

echo "== 4. 打包 + 原生库 =="
mkdir -p build/lib/arm64-v8a
cp vosk/aar/jni/arm64-v8a/libvosk.so build/lib/arm64-v8a/
cp vosk/jna/jni/arm64-v8a/libjnidispatch.so build/lib/arm64-v8a/
cp jniLibs/arm64-v8a/libsherpa-onnx-jni.so build/lib/arm64-v8a/
(cd build && $AAPT add app.unsigned.apk classes.dex >/dev/null)
(cd build && $AAPT add app.unsigned.apk lib/arm64-v8a/libvosk.so >/dev/null)
(cd build && $AAPT add app.unsigned.apk lib/arm64-v8a/libjnidispatch.so >/dev/null)
(cd build && $AAPT add app.unsigned.apk lib/arm64-v8a/libsherpa-onnx-jni.so >/dev/null)

echo "== 5. 对齐 + 签名 =="
zipalign -f 4 build/app.unsigned.apk build/app.aligned.apk
KS=/root/workspace/voicecalc/keystore/dsh.jks
mkdir -p /root/workspace/voicecalc/keystore
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias dsh -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass android -keypass android \
    -dname "CN=DSH VoiceCalc, O=DSH, C=CN" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 21 --out build/VoiceCalc.apk build/app.aligned.apk

echo "== 6. 校验 =="
apksigner verify --print-certs build/VoiceCalc.apk | head -2
ls -lh build/VoiceCalc.apk
