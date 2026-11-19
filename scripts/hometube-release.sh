#!/usr/bin/env bash
# HomeTube: publish a new version. TVs pick it up via Settings > "Check for updates"
# (and automatically on app start).
#
# Usage:
#   scripts/hometube-release.sh "Change 1" "Change 2" ...
#
# Steps: raise the release number, build signed APKs, write hometube.json,
# commit + tag, push the branch and tag, create a GitHub release with the files.
# Needs: ~/MyProjects/smarttube-tools/env.sh (JDK/SDK), the signing key, `gh` logged in.
set -euo pipefail

REPO="MustafaCelal/SmartTube"
BRANCH="hometube"
GRADLE_FILE="smarttubetv/build.gradle"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ "$(git rev-parse --abbrev-ref HEAD)" != "$BRANCH" ]; then
    echo "Switch to the $BRANCH branch first." >&2
    exit 1
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "Commit or stash your changes first." >&2
    exit 1
fi

CHANGES=("$@")
if [ ${#CHANGES[@]} -eq 0 ]; then
    CHANGES=("Hata düzeltmeleri ve iyileştirmeler")
fi

# 1. Raise the release number
OLD_BUILD=$(sed -n 's/^def hometubeBuild = \([0-9]*\).*/\1/p' "$GRADLE_FILE")
BUILD=$((OLD_BUILD + 1))
sed -i.bak "s/^def hometubeBuild = [0-9]*/def hometubeBuild = $BUILD/" "$GRADLE_FILE" && rm -f "$GRADLE_FILE.bak"
VERSION_NAME="1.$BUILD"
VERSION_CODE=$((3000000 + BUILD))
TAG="hometube-v$VERSION_NAME"
echo "Releasing HomeTube $VERSION_NAME (versionCode $VERSION_CODE)"

# 2. Build
# shellcheck disable=SC1090
source "$HOME/MyProjects/smarttube-tools/env.sh"
./gradlew --no-daemon --offline -q :smarttubetv:assembleSthomeRelease

OUT="smarttubetv/build/outputs/apk/sthome/release"
DIST="$(mktemp -d)"
ABIS=(armeabi-v7a arm64-v8a x86 universal)
for abi in "${ABIS[@]}"; do
    src="$OUT/SmartTube_home_${VERSION_NAME}_${abi}.apk"
    [ -f "$src" ] || { echo "Missing $src" >&2; exit 1; }
    cp "$src" "$DIST/HomeTube_${VERSION_NAME}_${abi}.apk"
done

# 3. Update manifest read by the app (see AppVersionChecker)
URL_BASE="https://github.com/$REPO/releases/download/$TAG"
python3 - "$DIST/hometube.json" "$VERSION_NAME" "$VERSION_CODE" "$URL_BASE" "${CHANGES[@]}" <<'PY'
import json, sys
out, name, code, base, *changes = sys.argv[1:]
apk = lambda abi: f"{base}/HomeTube_{name}_{abi}.apk"
manifest = {
    "package": {
        "downloadUrlList_armeabi-v7a": [apk("armeabi-v7a")],
        "downloadUrlList_arm64-v8a": [apk("arm64-v8a")],
        "downloadUrlList_x86": [apk("x86")],
        "downloadUrlList": [apk("universal")],
    },
    name: {"versionCode": int(code), "changelog": changes},
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
PY

# 4. Commit, tag, push
git add "$GRADLE_FILE"
git commit -q -m "HomeTube $VERSION_NAME"
git tag "$TAG"
git push -q origin "$BRANCH" "$TAG"

# 5. GitHub release (marked latest, so .../releases/latest/download/hometube.json points to it)
NOTES=$(printf -- '- %s\n' "${CHANGES[@]}")
gh release create "$TAG" --repo "$REPO" --title "HomeTube $VERSION_NAME" --notes "$NOTES" --latest \
    "$DIST"/HomeTube_*.apk "$DIST/hometube.json"

rm -rf "$DIST"
echo "Done: https://github.com/$REPO/releases/tag/$TAG"
