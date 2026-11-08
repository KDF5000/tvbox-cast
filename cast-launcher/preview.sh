#!/bin/sh
set -eu
# Desktop Java2D artwork preview. No Android runtime or device connection.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PREVIEW_OUT="$SCRIPT_DIR/preview-output"
if [ -n "${JAVA_HOME:-}" ]; then
  PREVIEW_JAVAC="$JAVA_HOME/bin/javac"
  PREVIEW_JAVA="$JAVA_HOME/bin/java"
else
  PREVIEW_JAVAC=javac
  PREVIEW_JAVA=java
fi
mkdir -p "$PREVIEW_OUT/classes"
"$PREVIEW_JAVAC" -encoding UTF-8 -d "$PREVIEW_OUT/classes" \
  "$SCRIPT_DIR"/preview/android/graphics/*.java \
  "$SCRIPT_DIR/preview/ArtworkPreview.java" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/NetworkDisplay.java" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/ReceiverStatus.java" \
  "$SCRIPT_DIR/src/local/tvbox/castlauncher/TvArtwork.java"
"$PREVIEW_JAVA" -Djava.awt.headless=true -cp "$PREVIEW_OUT/classes" \
  local.tvbox.castlauncher.ArtworkPreview "$PREVIEW_OUT" \
  "$SCRIPT_DIR/res/drawable-nodpi/standby_dusk_v1.png"
