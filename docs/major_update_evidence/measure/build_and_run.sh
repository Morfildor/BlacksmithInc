#!/usr/bin/env bash
# Scratch only: compiles Measure.kt against a frozen copy of the core jar with the Kotlin compiler from the Gradle cache.
set -u
S="$(cd "$(dirname "$0")" && pwd)"
G=/c/Users/tuncb/.gradle/caches/modules-2/files-2.1
w() { cygpath -w "$1"; }
one() { ls $1 | head -1; }
KC=$(w "$(one "$G/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.2.21/*/kotlin-compiler-embeddable-2.2.21.jar")")
KS=$(w "$(one "$G/org.jetbrains.kotlin/kotlin-stdlib/2.2.21/*/kotlin-stdlib-2.2.21.jar")")
KR=$(w "$(one "$G/org.jetbrains.kotlin/kotlin-script-runtime/2.2.21/*/kotlin-script-runtime-2.2.21.jar")")
KF=$(w "$(one "$G/org.jetbrains.kotlin/kotlin-reflect/1.6.10/*/kotlin-reflect-1.6.10.jar")")
KD=$(w "$(one "$G/org.jetbrains.kotlin/kotlin-daemon-embeddable/2.2.21/*/kotlin-daemon-embeddable-2.2.21.jar")")
KX=$(w "$(one "$G/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.0/*/kotlinx-coroutines-core-jvm-1.8.0.jar")")
AN=$(w "$(one "$G/org.jetbrains/annotations/13.0/*/annotations-13.0.jar")")
SC=$(w "$(one "$G/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.9.0/*/kotlinx-serialization-core-jvm-1.9.0.jar")")
SJ=$(w "$(one "$G/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm/1.9.0/*/kotlinx-serialization-json-jvm-1.9.0.jar")")
CORE=$(w "$S/${CORE_JAR:-core-d7b3283.jar}")
OUTD=$(w "$S/out")
if [ "${1:-}" = "compile" ]; then
  rm -rf "$S/out"
  java -cp "$KC;$KS;$KR;$KF;$KD;$KX;$AN" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 11 -cp "$CORE;$KS;$SC;$SJ" -d "$OUTD" "$(w "$S/Measure.kt")"
  echo "compile exit=$?"
else
  java -cp "$OUTD;$CORE;$KS;$SC;$SJ" MeasureKt "$@"
fi
