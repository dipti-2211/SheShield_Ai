#!/usr/bin/env bash
set -euo pipefail
if command -v cloudflared >/dev/null 2>&1; then tunnel_bin="$(command -v cloudflared)"; else tunnel_bin=/tmp/sheshield-tools/cloudflared; fi
exec "$tunnel_bin" tunnel --url http://127.0.0.1:8787 --protocol http2 --no-autoupdate
