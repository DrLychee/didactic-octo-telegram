#!/bin/bash
# 一键构建脚本：需设置 ANDROID_HOME（含 build-tools/34.0.0 与 platforms/android-34）
set -e
[ -z "$ANDROID_HOME" ] && { echo "请设置 ANDROID_HOME"; exit 1; }
BT=$ANDROID_HOME/build-tools/34.0.0
PLAT=$ANDROID_HOME/platforms/android-34/android.jar
cd "$(dirname "$0")/.."
rm -rf bin && mkdir -p bin/dex bin/classes
$BT/aapt2 compile --dir app/res -o bin/res.zip
$BT/aapt2 link -o bin/base.apk -I $PLAT --manifest app/AndroidManifest.xml -A app/assets bin/res.zip --java bin
javac -source 8 -target 8 -classpath $PLAT -d bin/classes bin/com/lcz/attend/R.java app/src/com/lcz/attend/MainActivity.java
$BT/d8 --release --lib $PLAT --min-api 21 --output bin/dex bin/classes/com/lcz/attend/*.class
mkdir -p bin/apkstage && cd bin/apkstage
unzip -qo ../base.apk -x 'classes*.dex'
cp ../dex/classes.dex .
zip -qr ../unsigned.apk AndroidManifest.xml res assets classes.dex
cd ..
$BT/zipalign -f 4 unsigned.apk aligned.apk
if [ -n "$KEYSTORE" ]; then
  $BT/apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" --out ../班级考勤.apk aligned.apk
  $BT/apksigner verify ../班级考勤.apk && echo "BUILD OK: 班级考勤.apk"
else
  echo "未设置 KEYSTORE，仅输出未签名包 bin/aligned.apk（zipalign 已完成）"
fi
