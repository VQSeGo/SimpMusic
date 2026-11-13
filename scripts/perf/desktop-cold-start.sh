#!/usr/bin/env bash
# Замер холодного старта десктопа (Hyprland): время до появления окна и метки `Startup` из app.log.
#
#   scripts/perf/desktop-cold-start.sh [путь/к/SimpMusic.AppImage]
#
# Переменные: RUNS (3), WINDOW_CLASS (SimpMusic), WINDOW_TIMEOUT_S (60), EXIT_TIMEOUT_S (15),
# SETTLE_S (12, сколько ждать после появления окна, чтобы успели метки home-*).
# Приложение не должно быть запущено. Второй экземпляр не запускаем никогда.
set -euo pipefail

APPIMAGE="${1:-$HOME/TorvaldsVPN/Music/builds/SimpMusic-redesign-x86_64.AppImage}"
RUNS="${RUNS:-3}"
WINDOW_CLASS="${WINDOW_CLASS:-SimpMusic}"
WINDOW_TIMEOUT_S="${WINDOW_TIMEOUT_S:-60}"
EXIT_TIMEOUT_S="${EXIT_TIMEOUT_S:-15}"
SETTLE_S="${SETTLE_S:-12}"
LOG_DIR="$HOME/.simpmusic/logs"

for tool in hyprctl jq; do
  command -v "$tool" >/dev/null || { echo "Нужен $tool" >&2; exit 1; }
done
[ -x "$APPIMAGE" ] || { echo "Нет исполняемого файла: $APPIMAGE" >&2; exit 1; }

now_ms() { date +%s%3N; }

window_present() {
  hyprctl clients -j 2>/dev/null | jq -e --arg c "$WINDOW_CLASS" 'any(.[]; .class == $c)' >/dev/null
}

app_running() {
  window_present || pgrep -f 'bin/simpmusic' >/dev/null
}

if ! hyprctl clients -j >/dev/null 2>&1; then
  echo "hyprctl не отвечает: нет сессии Hyprland, замер невозможен" >&2
  exit 1
fi

# Выход приложения: SIGTERM процессу bin/simpmusic, ждём до EXIT_TIMEOUT_S.
stop_app() {
  local pids deadline
  pids="$(pgrep -f 'bin/simpmusic' || true)"
  [ -z "$pids" ] && return 0
  # shellcheck disable=SC2086
  kill -TERM $pids 2>/dev/null || true
  deadline=$(( $(now_ms) + EXIT_TIMEOUT_S * 1000 ))
  while pgrep -f 'bin/simpmusic' >/dev/null; do
    if [ "$(now_ms)" -gt "$deadline" ]; then
      echo "Приложение не вышло за ${EXIT_TIMEOUT_S} с" >&2
      return 1
    fi
    sleep 0.2
  done
}

if app_running; then
  echo "Приложение уже запущено (окно $WINDOW_CLASS или процесс bin/simpmusic). Закройте его и повторите." >&2
  exit 1
fi

windows=()
for run in $(seq 1 "$RUNS"); do
  # Метки читаем по времени (UTC, как в app.log), а не по номерам строк: файл может перекатиться посреди прогона.
  start_iso="$(date -u +%Y-%m-%dT%H:%M:%S)"

  start_ms="$(now_ms)"
  setsid "$APPIMAGE" >/dev/null 2>&1 &
  window_ms=""
  deadline=$(( start_ms + WINDOW_TIMEOUT_S * 1000 ))
  while [ "$(now_ms)" -le "$deadline" ]; do
    if window_present; then
      window_ms=$(( $(now_ms) - start_ms ))
      break
    fi
    sleep 0.05
  done

  if [ -z "$window_ms" ]; then
    echo "Прогон $run: окно класса $WINDOW_CLASS не появилось за ${WINDOW_TIMEOUT_S} с" >&2
    stop_app || true
    exit 1
  fi
  windows+=("$window_ms")

  sleep "$SETTLE_S"
  echo "== Прогон $run: окно через $(awk -v m="$window_ms" 'BEGIN{printf "%.2f", m/1000}') с (от запуска AppImage)"
  marks="$(cat "$LOG_DIR"/app*.log 2>/dev/null | grep -E 'I/Startup:' | awk -v t="$start_iso" '$1 >= t' | sort || true)"
  if [ -n "$marks" ]; then printf '%s\n' "$marks" | sed 's/^/   /'; else echo "   меток Startup нет"; fi

  stop_app || { echo "Дальше не идём: приложение не вышло" >&2; exit 1; }
done

printf 'Окно, мс по прогонам:'
printf ' %s' "${windows[@]}"
echo
printf '%s\n' "${windows[@]}" | sort -n | awk '{a[NR]=$1} END{m=(NR%2)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2; printf "Медиана окна: %.2f с\n", m/1000}'
