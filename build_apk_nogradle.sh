#!/usr/bin/env bash
# Gradle/AGP 없이 aapt2 + javac + d8 + uber-apk-signer로 APK를 만드는 스크립트
set -euo pipefail

T=/path/to/tools
P=/path/to/project/app/src/main
OUT=/path/to/build
PKG=io.github.nunnoo58.fullcharge
AAPT2=$T/aaptjs3-2.0.2/package/bin/x64/linux/aapt2
ANDROID_JAR=$T/android-36.jar
D8CP=$T/d8x/classes.jar:$T/d8x/classes2.jar:$T/d8x/classes3.jar

rm -rf "$OUT" && mkdir -p "$OUT"/{flat,gen,classes,dex}

# 1) Gradle이 하던 일: manifest에 package·uses-sdk 추가, tools: 속성 제거
python3 - "$P/AndroidManifest.xml" "$OUT/AndroidManifest.xml" "$PKG" <<'EOF'
import re, sys
src, dst, pkg = sys.argv[1:4]
s = open(src, encoding="utf-8").read()
s = re.sub(r'\s+xmlns:tools="[^"]*"', '', s)
s = re.sub(r'\s+tools:\w+="[^"]*"', '', s)
s = s.replace('<manifest ', f'<manifest package="{pkg}" android:versionCode="3" android:versionName="1.2" ', 1)
s = re.sub(r'(<manifest[^>]*>)', r'\1\n    <uses-sdk android:minSdkVersion="34" android:targetSdkVersion="36" />', s, count=1)
open(dst, "w", encoding="utf-8").write(s)
EOF

# 2) 리소스 컴파일·링크 → R.java 생성
"$AAPT2" compile --dir "$P/res" -o "$OUT/flat/res.zip"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
    --manifest "$OUT/AndroidManifest.xml" --java "$OUT/gen" \
    --auto-add-overlay "$OUT/flat/res.zip"

# 3) Java 컴파일 (구형 d8과 호환되도록 Java 8 바이트코드)
find "$P/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

# 4) dex 변환 (dx, 람다 없는 Java 8 바이트코드)
DX=$(ls $T/dex2jar/*/lib/dx-*.jar)
java -cp "$DX" com.android.dx.command.Main --dex --min-sdk-version=26 \
    --output="$OUT/dex/classes.dex" "$OUT/classes"

# 5) dex를 APK에 넣고, zipalign + 서명(v1/v2/v3)
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -0 "$OUT/unsigned.apk" classes.dex )
java -jar "$T/uber-apk-signer.jar" -a "$OUT/unsigned.apk" -o "$OUT/signed" --allowResign
ls -la "$OUT/signed"
