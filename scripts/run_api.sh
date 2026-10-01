#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root/api"
if command -v node >/dev/null 2>&1; then node_bin="$(command -v node)"; else node_bin=/tmp/sheshield-tools/node/bin/node; fi
exec "$node_bin" --env-file-if-exists=.env src/server.js
