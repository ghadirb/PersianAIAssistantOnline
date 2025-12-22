#!/usr/bin/env bash
set -euo pipefail

# Fetch prebuilt native libraries archive from PREBUILT_LIBS_URL env var and unpack into repo root prebuilt_libs/
if [ -z "${PREBUILT_LIBS_URL-}" ]; then
  echo "PREBUILT_LIBS_URL not set; skipping prebuilt libs download"
  exit 0
fi

DEST_DIR="$(pwd)/prebuilt_libs"
TMPZIP="/tmp/prebuilt_libs.zip"

echo "Downloading prebuilt libs from $PREBUILT_LIBS_URL"
curl -fsSL "$PREBUILT_LIBS_URL" -o "$TMPZIP"
mkdir -p "$DEST_DIR"
unzip -o "$TMPZIP" -d "$DEST_DIR"
echo "Prebuilt libs unpacked to $DEST_DIR"
rm -f "$TMPZIP"
