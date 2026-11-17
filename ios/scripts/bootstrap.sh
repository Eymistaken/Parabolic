#!/usr/bin/env bash
# Fetches everything the iOS build needs that is not kept in git:
#   Vendor/Python.xcframework   CPython for iOS (BeeWare Python-Apple-support)
#   Vendor/app_packages/        yt-dlp (+ its EJS solver scripts) and certifi
#
# Environment overrides:
#   PYTHON_SUPPORT_TAG   BeeWare release tag (default below)
#   YTDLP_VERSION        yt-dlp release tag to bundle, or "latest"
set -euo pipefail

cd "$(dirname "$0")/.."

PYTHON_SUPPORT_TAG="${PYTHON_SUPPORT_TAG:-3.14-b14}"
PYTHON_MINOR="${PYTHON_SUPPORT_TAG%%-*}"
PYTHON_BUILD="${PYTHON_SUPPORT_TAG##*-}"
PYTHON_ASSET="Python-${PYTHON_MINOR}-iOS-support.${PYTHON_BUILD}.tar.gz"
YTDLP_VERSION="${YTDLP_VERSION:-latest}"

VENDOR="Vendor"
mkdir -p "$VENDOR"

# --- CPython ----------------------------------------------------------------
STAMP="$VENDOR/.python-support-tag"
if [ ! -d "$VENDOR/Python.xcframework" ] || [ "$(cat "$STAMP" 2>/dev/null)" != "$PYTHON_SUPPORT_TAG" ]; then
  echo "==> Downloading $PYTHON_ASSET"
  rm -rf "$VENDOR/Python.xcframework" "$VENDOR/python-support"
  mkdir -p "$VENDOR/python-support"
  curl -fsSL --retry 3 \
    "https://github.com/beeware/Python-Apple-support/releases/download/${PYTHON_SUPPORT_TAG}/${PYTHON_ASSET}" \
    -o "$VENDOR/python-support.tar.gz"
  tar -xzf "$VENDOR/python-support.tar.gz" -C "$VENDOR/python-support"
  mv "$VENDOR/python-support/Python.xcframework" "$VENDOR/Python.xcframework"
  cp "$VENDOR/python-support/VERSIONS" "$VENDOR/PYTHON-VERSIONS"
  rm -rf "$VENDOR/python-support" "$VENDOR/python-support.tar.gz"
  echo "$PYTHON_SUPPORT_TAG" > "$STAMP"
fi

# --- yt-dlp -----------------------------------------------------------------
if [ "$YTDLP_VERSION" = "latest" ]; then
  YTDLP_URL="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
else
  YTDLP_URL="https://github.com/yt-dlp/yt-dlp/releases/download/${YTDLP_VERSION}/yt-dlp"
fi
echo "==> Downloading yt-dlp ($YTDLP_VERSION)"
rm -rf "$VENDOR/app_packages"
mkdir -p "$VENDOR/app_packages"
curl -fsSL --retry 3 "$YTDLP_URL" -o "$VENDOR/yt-dlp.zipapp"

# The release binary is a zipapp (a shebang line followed by a zip archive).
# It already contains the yt-dlp-ejs challenge solver scripts.
python3 -I - "$VENDOR/yt-dlp.zipapp" "$VENDOR/app_packages" <<'PY'
import sys, zipfile
archive, target = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(archive) as z:
    members = [m for m in z.namelist() if m.startswith(("yt_dlp/", "yt_dlp_ejs/"))]
    if not any(m.startswith("yt_dlp_ejs/") for m in members):
        sys.exit("yt-dlp release does not bundle yt_dlp_ejs; the YouTube solver would be missing")
    z.extractall(target, members)
PY
rm -f "$VENDOR/yt-dlp.zipapp"

# --- certifi ----------------------------------------------------------------
# iOS gives Python no system CA bundle, so TLS verification needs certifi.
echo "==> Downloading certifi"
python3 -I - "$VENDOR/app_packages" <<'PY'
import io, json, sys, urllib.request, zipfile
target = sys.argv[1]
meta = json.load(urllib.request.urlopen("https://pypi.org/pypi/certifi/json", timeout=60))
wheel = next(u for u in meta["urls"] if u["filename"].endswith("-py3-none-any.whl"))
data = urllib.request.urlopen(wheel["url"], timeout=120).read()
with zipfile.ZipFile(io.BytesIO(data)) as z:
    z.extractall(target, [m for m in z.namelist() if m.startswith("certifi/")])
print("    certifi", meta["info"]["version"])
PY

YTDLP_BUNDLED=$(python3 -I -c "import re,sys; print(re.search(r\"__version__ = '([^']+)'\", open(sys.argv[1]).read()).group(1))" "$VENDOR/app_packages/yt_dlp/version.py")
echo "$YTDLP_BUNDLED" > "$VENDOR/YTDLP-VERSION"
echo "    yt-dlp $YTDLP_BUNDLED"

# --- bytecode ---------------------------------------------------------------
# The app bundle is read-only, so Python would recompile every module on each
# launch. Pre-compile with a matching host interpreter when one is available.
HOST_PYTHON="python${PYTHON_MINOR}"
if command -v "$HOST_PYTHON" >/dev/null 2>&1; then
  echo "==> Pre-compiling bytecode with $HOST_PYTHON"
  "$HOST_PYTHON" -m compileall -q -j 0 --invalidation-mode unchecked-hash \
    -x '/(test|idlelib|tkinter|turtledemo|ensurepip)/' \
    "$VENDOR/Python.xcframework/lib" "$VENDOR/app_packages" python >/dev/null || true
else
  echo "==> $HOST_PYTHON not found; skipping bytecode pre-compilation (startup will be slower)"
fi

echo "==> Done. Next: xcodegen generate"
