#!/usr/bin/env bash
# Замер холодного старта на телефоне по ADB: Displayed, Fully drawn, метки Startup, зависания, запросы browse.
#
#   scripts/perf/android-cold-start.sh
#
# Переменные: RUNS (5), WAIT_S (12), ADB (путь к adb), PKG (com.maxrave.simpmusic), SERIAL (если устройств несколько).
# Телефон должен быть разблокирован, отладка по USB разрешена. `am start -S` принудительно останавливает
# приложение (очередь воспроизведения сохраняется). Журнал logcat не очищаем.
set -euo pipefail

ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
  else
    ADB="$HOME/Android/Sdk/platform-tools/adb"
  fi
fi
[ -x "$ADB" ] || { echo "adb не найден (искал в PATH и $HOME/Android/Sdk/platform-tools/adb); задайте ADB=..." >&2; exit 1; }

PKG="${PKG:-com.maxrave.simpmusic}"
ACTIVITY="$PKG/.MainActivity"
RUNS="${RUNS:-5}"
WAIT_S="${WAIT_S:-12}"
SKIP_MIN="${SKIP_MIN:-30}"

adb_cmd() {
  if [ -n "${SERIAL:-}" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}

state="$(adb_cmd get-state 2>/dev/null || true)"
if [ "$state" != "device" ]; then
  echo "Нет подключённого и авторизованного устройства (get-state: '${state:-нет}'). Подключите телефон, разрешите отладку." >&2
  exit 1
fi

median() {
  # Медиана чисел со стандартного ввода; пустой ввод даёт "-".
  sort -n | awk '{a[NR]=$1} END{ if(NR==0){print "-"} else if(NR%2){print a[(NR+1)/2]} else {printf "%.0f\n",(a[NR/2]+a[NR/2+1])/2} }'
}

# "+5s580ms" / "+896ms" / "+1m2s3ms" -> миллисекунды.
to_ms() {
  awk -v s="$1" 'BEGIN{
    ms=0; sub(/^\+/,"",s);
    if (match(s,/[0-9]+m[^s]/)) { m=substr(s,RSTART,RLENGTH-1); ms+=m*60000; s=substr(s,RSTART+RLENGTH-1) }
    if (match(s,/[0-9]+s/)) { v=substr(s,RSTART,RLENGTH-1); ms+=v*1000; s=substr(s,RSTART+RLENGTH) }
    if (match(s,/[0-9]+ms/)) { v=substr(s,RSTART,RLENGTH-2); ms+=v }
    print ms }'
}

displayed_all=(); fully_all=(); home_all=(); first_all=(); koin_all=(); skips_all=(); browse_all=(); lines_all=()

for run in $(seq 1 "$RUNS"); do
  # Метка времени устройства: в logcat попадают только строки этого прогона.
  since="$(adb_cmd shell date '+%m-%d\ %H:%M:%S.000' | tr -d '\r')"
  start_out="$(adb_cmd shell am start -S -W -n "$ACTIVITY" | tr -d '\r')"
  sleep "$WAIT_S"
  pid="$(adb_cmd shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')"
  log="$(adb_cmd logcat -d -v threadtime -T "$since" | tr -d '\r')"

  displayed_ms="$(printf '%s\n' "$log" | grep -E "Displayed $PKG/" | tail -n 1 | grep -oE '\+[0-9msh]+' | head -n 1 || true)"
  [ -n "$displayed_ms" ] && displayed_ms="$(to_ms "$displayed_ms")" || displayed_ms=""
  if [ -z "$displayed_ms" ]; then
    displayed_ms="$(printf '%s\n' "$start_out" | awk '/TotalTime:/{print $2}')"
  fi
  fully_raw="$(printf '%s\n' "$log" | grep -E "Fully drawn $PKG/" | tail -n 1 | grep -oE '\+[0-9msh]+' | head -n 1 || true)"
  fully_ms=""; [ -n "$fully_raw" ] && fully_ms="$(to_ms "$fully_raw")"

  proc_log="$log"
  [ -n "$pid" ] && proc_log="$(printf '%s\n' "$log" | awk -v p="$pid" '$3==p')"
  marks="$(printf '%s\n' "$proc_log" | grep -E ' Startup *: ' | sed -E 's/^.* Startup *: *//' || true)"
  mark_ms() { printf '%s\n' "$marks" | awk -v n="$1" '$1==n {print $2; exit}'; }
  koin_ms="$(mark_ms koin-done)"; first_ms="$(mark_ms first-composition)"; home_ms="$(mark_ms home-shown)"
  skips="$(printf '%s\n' "$proc_log" | grep -E 'Choreographer *: Skipped [0-9]+ frames' \
    | sed -E 's/.*Skipped ([0-9]+) frames.*/\1/' | awk -v m="$SKIP_MIN" '$1>=m' | paste -sd, - || true)"
  browse_n="$(printf '%s\n' "$proc_log" | grep -cE 'curl .*/browse' || true)"
  lines_n="$(printf '%s\n' "$proc_log" | grep -c . || true)"

  echo "== Прогон $run (pid ${pid:-?})"
  echo "   Displayed:   ${displayed_ms:--} мс"
  echo "   Fully drawn: ${fully_ms:--} мс"
  echo "   Метки Startup (мс от старта процесса):"
  if [ -n "$marks" ]; then printf '%s\n' "$marks" | sed 's/^/     /'; else echo "     нет"; fi
  echo "   Skipped >= $SKIP_MIN кадров: ${skips:-нет}"
  echo "   Запросов browse: $browse_n; строк logcat процесса: $lines_n"

  [ -n "$displayed_ms" ] && displayed_all+=("$displayed_ms")
  [ -n "$fully_ms" ] && fully_all+=("$fully_ms")
  [ -n "$koin_ms" ] && koin_all+=("$koin_ms")
  [ -n "$first_ms" ] && first_all+=("$first_ms")
  [ -n "$home_ms" ] && home_all+=("$home_ms")
  skips_all+=("$(printf '%s' "$skips" | awk -F, '{print ($1==""?0:NF)}')")
  browse_all+=("$browse_n"); lines_all+=("$lines_n")
done

med() { if [ "$#" -eq 0 ]; then echo "-"; else printf '%s\n' "$@" | median; fi; }
# Пустой массив при set -u: разворачиваем только если в нём что-то есть.
m() { local -n arr=$1; if [ "${#arr[@]}" -eq 0 ]; then echo "-"; else med "${arr[@]}"; fi; }
echo
echo "== Медианы по $RUNS прогонам"
echo "   Displayed:        $(m displayed_all) мс"
echo "   Fully drawn:      $(m fully_all) мс"
echo "   koin-done:        $(m koin_all) мс"
echo "   first-composition: $(m first_all) мс"
echo "   home-shown:       $(m home_all) мс"
echo "   Зависаний >= $SKIP_MIN кадров: $(m skips_all)"
echo "   Запросов browse:  $(m browse_all)"
echo "   Строк logcat:     $(m lines_all)"
