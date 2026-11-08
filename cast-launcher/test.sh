#!/bin/sh
set -eu
# Protocol tests bind loopback only. No adb, device changes or LAN advertisements.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TEST_OUT="$SCRIPT_DIR/test-output/classes"
if [ -n "${JAVA_HOME:-}" ]; then
  TEST_JAVAC="$JAVA_HOME/bin/javac"
  TEST_JAVA="$JAVA_HOME/bin/java"
else
  TEST_JAVAC=javac
  TEST_JAVA=java
fi
mkdir -p "$TEST_OUT"
"$TEST_JAVAC" -encoding UTF-8 -d "$TEST_OUT" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/PlaybackState.java" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/DlnaProtocol.java" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/DlnaDescription.java" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/SsdpDiscovery.java" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/DlnaHttpServer.java" \
  "$SCRIPT_DIR/test/DlnaReceiverTest.java"
"$TEST_JAVA" -cp "$TEST_OUT" local.tvbox.castlauncher.DlnaReceiverTest
