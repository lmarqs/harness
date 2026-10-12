#!/usr/bin/env bash
#MISE description="Install exactly the dependencies recorded in package-lock.json"
set -euo pipefail
if [[ ! -f package-lock.json ]]; then
  printf '%s\n' 'package-lock.json is required. Run mise run node:lock, review the lockfile, then retry.' >&2
  exit 1
fi
exec npm ci "$@"
