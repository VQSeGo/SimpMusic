#!/usr/bin/env python3
"""Network helpers for the offline scenarios (docs/plans/2.6.0/OFFLINE_PLAN.md, section 4). Standard library only.

  proxy <listen> [--blackhole]
      HTTP proxy (CONNECT and absolute-URI requests). <listen> is a TCP port (bound to 127.0.0.1 only)
      or a unix socket path. Logs one line per connection. --blackhole accepts every connection and
      never answers: the "network is there but nothing loads" case (scenario C3).
      Killing the process drops every connection at once (scenario C2).
  fake-dns <ip>
      answer every A query on 127.0.0.1:53 with <ip>, every other query with an empty answer, so names
      resolve and connections then hang (desktop C3, inside a network namespace)
  forward <port> <unix-socket>
      inside a namespace: 127.0.0.1:<port> -> <unix-socket>

Phone through this computer (the phone's own network may not reach YouTube):
  net-helper.py proxy 8899 [--blackhole] &
  adb -s <serial> reverse tcp:8899 tcp:8899
  then the phone's proxy 127.0.0.1:8899: `settings put global http_proxy 127.0.0.1:8899`, or, where MIUI
  refuses that to adb, the Wi-Fi network's own proxy setting. Undo: http_proxy :0 (or the network's
  proxy back to None) and `adb reverse --remove-all`. Airplane mode does not cut this path (it runs over
  USB); stop the proxy for that.
"""
import time
import os
import socket
import struct
import sys
import threading


def pump(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def splice(a, b):
    threading.Thread(target=pump, args=(a, b), daemon=True).start()
    threading.Thread(target=pump, args=(b, a), daemon=True).start()


def fake_dns(ip):
    answer_ip = socket.inet_aton(ip)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("127.0.0.1", 53))
    while True:
        query, addr = sock.recvfrom(4096)
        if len(query) < 12:
            continue
        tid = query[:2]
        # Question section: labels up to the zero byte, then QTYPE and QCLASS.
        end = query.index(b"\x00", 12) + 5
        question = query[12:end]
        qtype = struct.unpack(">H", query[end - 4:end - 2])[0]
        if qtype == 1:
            header = tid + b"\x81\x80" + struct.pack(">HHHH", 1, 1, 0, 0)
            record = b"\xc0\x0c" + struct.pack(">HHIH", 1, 1, 60, 4) + answer_ip
        else:
            header = tid + b"\x81\x80" + struct.pack(">HHHH", 1, 0, 0, 0)
            record = b""
        sock.sendto(header + question + record, addr)


def read_head(conn):
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = conn.recv(4096)
        if not chunk:
            return None, b""
        data += chunk
    head, rest = data.split(b"\r\n\r\n", 1)
    return head, rest


def handle_proxy(conn):
    try:
        head, rest = read_head(conn)
        if head is None:
            conn.close()
            return
        method, target, version = head.split(b"\r\n", 1)[0].split(b" ", 2)
        if method == b"CONNECT":
            host, port = target.rsplit(b":", 1)
            upstream = socket.create_connection((host.decode(), int(port)), timeout=15)
            upstream.settimeout(None)
            conn.sendall(b"HTTP/1.1 200 Connection established\r\n\r\n")
            if rest:
                upstream.sendall(rest)
        else:
            url = target.decode()
            hostport = url.split("://", 1)[1].split("/", 1)[0]
            host, _, port = hostport.partition(":")
            upstream = socket.create_connection((host, int(port or 80)), timeout=15)
            upstream.settimeout(None)
            upstream.sendall(head + b"\r\n\r\n" + rest)
        print(f"{time.strftime('%H:%M:%S')} {method.decode()} {target.decode()[:80]}", flush=True)
        splice(conn, upstream)
    except Exception as e:  # noqa: BLE001 - any failure just drops this one client
        print(f"{time.strftime('%H:%M:%S')} error {e}", flush=True)
        conn.close()


def hold(conn):
    """Blackhole: read whatever comes, answer nothing, until the client gives up."""
    try:
        head, _ = read_head(conn)
        if head:
            print(f"{time.strftime('%H:%M:%S')} blackhole {head.split(b' ')[1].decode()[:80]}", flush=True)
        while conn.recv(65536):
            pass
    except OSError:
        pass
    conn.close()


def proxy(listen, blackhole):
    if listen.isdigit():
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("127.0.0.1", int(listen)))
    else:
        if os.path.exists(listen):
            os.unlink(listen)
        server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        server.bind(listen)
    server.listen(128)
    print(f"proxy on {listen}{' (blackhole)' if blackhole else ''}", flush=True)
    while True:
        conn, _ = server.accept()
        target = hold if blackhole else handle_proxy
        threading.Thread(target=target, args=(conn,), daemon=True).start()


def forward(port, path):
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", port))
    server.listen(128)
    while True:
        conn, _ = server.accept()
        try:
            upstream = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            upstream.connect(path)
        except OSError:
            conn.close()
            continue
        splice(conn, upstream)


if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == "fake-dns":
        fake_dns(sys.argv[2])
    elif mode == "proxy":
        proxy(sys.argv[2], "--blackhole" in sys.argv[3:])
    elif mode == "forward":
        forward(int(sys.argv[2]), sys.argv[3])
    else:
        sys.exit(__doc__)
