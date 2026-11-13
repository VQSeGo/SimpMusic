#!/usr/bin/env bash
# Runs the desktop AppImage in its own network namespace, leaving the computer's network alone.
#
#   scripts/offline/desktop-no-network.sh [mode] [path/to/SimpMusic.AppImage] [app arguments...]
#
# App arguments go to the app, e.g. a deep link: simpmusic://album?id=MPREb_...
#
# Modes (scenarios from OFFLINE_PLAN.md, section 4):
#   offline   (default) C1, C4: only a loopback that is down. DNS and every connection fail at once.
#   hang      C3 (an imitation): names resolve (a fake DNS answers every name with 10.255.255.1) and the
#             default route leads into a dummy interface, so every connection hangs until its own
#             timeout instead of failing. Real "white list" networks may differ: see OFFLINE_PLAN.md, 8.
#   proxied   C2: the namespace's only way out is an HTTP proxy outside it (net-helper.py on a unix
#             socket). The JVM gets it through -Dhttp(s).proxyHost, mpv through http_proxy. Run
#             `desktop-no-network.sh cut` from another terminal to kill that proxy mid-track: every open
#             connection drops and new ones are refused.
#   cut       kill the proxy of a running `proxied` session.
#
# Window (Wayland), sound (PipeWire) and MPRIS (D-Bus) are unix sockets and keep working inside. The
# user id stays the real one (unshare --map-current-user / a nested user namespace): D-Bus refuses a
# client that claims uid 0. The AppImage is mounted outside the namespace, because FUSE cannot mount
# from an unprivileged user namespace.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
mode="offline"
case "${1:-}" in
    offline | hang | proxied | cut) mode="$1"; shift ;;
esac
appimage="$HOME/TorvaldsVPN/Music/builds/SimpMusic-redesign-x86_64.AppImage"
if [[ "${1:-}" == *.AppImage ]]; then appimage="$1"; shift; fi
run_dir="${XDG_RUNTIME_DIR:-/tmp}/simpmusic-offline"

if [[ "$mode" == "cut" ]]; then
    if [[ -f "$run_dir/proxy.pid" ]] && kill "$(cat "$run_dir/proxy.pid")" 2>/dev/null; then
        echo "proxy killed at $(date +%T): the app has no network now"
    else
        echo "no running proxy in $run_dir" >&2
        exit 1
    fi
    exit 0
fi

if hyprctl clients -j 2>/dev/null | grep -q '"class": "SimpMusic"' || pgrep -x simpmusic >/dev/null; then
    echo "SimpMusic is already running; close it first (it takes 5-9 s to exit)." >&2
    exit 1
fi
[[ -x "$appimage" ]] || { echo "No AppImage at $appimage" >&2; exit 1; }

mkdir -p "$run_dir"
mount_log="$run_dir/mount.log"
"$appimage" --appimage-mount > "$mount_log" 2>&1 &
mount_pid=$!
pids=("$mount_pid")
cleanup() {
    for p in "${pids[@]}"; do kill "$p" 2>/dev/null || true; done
    rm -f "$run_dir/proxy.pid" "$run_dir/proxy.sock"
}
trap cleanup EXIT
for _ in $(seq 50); do
    mount_point="$(head -n1 "$mount_log" 2>/dev/null || true)"
    [[ -n "$mount_point" && -x "$mount_point/AppRun" ]] && break
    sleep 0.1
done
[[ -x "${mount_point:-}/AppRun" ]] || { echo "Could not mount $appimage" >&2; cat "$mount_log" >&2; exit 1; }
export APPIMAGE="$appimage"
echo "$(date +%T) starting in mode $mode"

case "$mode" in
    offline)
        unshare --net --map-current-user -- "$mount_point/AppRun" "$@" || true
        ;;
    hang)
        # Root inside a fresh user namespace sets up the network, then the app runs one namespace
        # deeper as the real user id. The fake DNS dies with the namespace's shell.
        unshare --net --map-root-user -- bash -c '
            set -e
            ip link set lo up
            ip link add blackhole0 type dummy
            ip addr add 10.255.255.2/24 dev blackhole0
            ip link set blackhole0 up
            ip route add default via 10.255.255.1 dev blackhole0
            python3 "$1" fake-dns 10.255.255.1 &
            dns=$!
            sleep 0.3
            shift
            unshare --user --map-user='"$(id -u)"' --map-group='"$(id -g)"' -- "$@" || true
            kill $dns
        ' bash "$here/net-helper.py" "$mount_point/AppRun" "$@"
        ;;
    proxied)
        python3 -u "$here/net-helper.py" proxy "$run_dir/proxy.sock" > "$run_dir/proxy.log" 2>&1 &
        echo $! > "$run_dir/proxy.pid"
        pids+=("$!")
        echo "proxy log: $run_dir/proxy.log"
        export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=3128 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=3128"
        export http_proxy="http://127.0.0.1:3128"
        unshare --net --map-root-user -- bash -c '
            set -e
            ip link set lo up
            python3 "$1" forward 3128 "$2" &
            fwd=$!
            sleep 0.3
            shift 2
            unshare --user --map-user='"$(id -u)"' --map-group='"$(id -g)"' -- "$@" || true
            kill $fwd
        ' bash "$here/net-helper.py" "$run_dir/proxy.sock" "$mount_point/AppRun" "$@"
        ;;
esac
echo "$(date +%T) the app has exited"
