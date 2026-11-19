#!/usr/bin/env bash
# Собирает Linux AppImage и кладёт его в ~/TorvaldsVPN/Music/builds (или в $OUT_DIR).
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/.gradle/jdks/eclipse_adoptium-21-*/ | head -n1)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$HOME/.local/opt/conveyor/conveyor-22.0/bin:$PATH"

OUT_DIR="${OUT_DIR:-$HOME/TorvaldsVPN/Music/builds}"
GRADLE_FLAGS=(--no-configuration-cache)

./gradlew :composeApp:mpvSetupAll "${GRADLE_FLAGS[@]}"
# mpvSetupAll unpacks the stock libmpv from the release of the original (no silenceremove, so no
# "skip silence" on Desktop) over mpv-natives/linux-x64. With Docker available the slice is rebuilt
# from scripts/mpv-linux on top of it (cached after the first run), so the AppImage carries our filters.
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    ./gradlew :composeApp:mpvSetupLinuxCi "${GRADLE_FLAGS[@]}"
else
    echo "Docker не запущен: в AppImage уйдёт libmpv из релиза, без silenceremove (пропуск тишины на ПК не заработает)" >&2
fi
./gradlew :desktopApp:writeConveyorConfig "${GRADLE_FLAGS[@]}"
conveyor --agree-to-license=1 -Kapp.machines=linux.amd64.glibc make linux-app
./gradlew :desktopApp:packageConveyorAppImage "${GRADLE_FLAGS[@]}"

mkdir -p "$OUT_DIR"
branch="$(git rev-parse --abbrev-ref HEAD | tr '/' '-')"
target="$OUT_DIR/SimpMusic-$branch-x86_64.AppImage"
cp -f desktopApp/build/appimage/conveyor/SimpMusic-x86_64.AppImage "$target"
chmod +x "$target"
echo "Готово: $target"
