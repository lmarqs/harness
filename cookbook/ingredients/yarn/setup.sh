#!/usr/bin/env bash
#MISE description="Install exactly the dependencies recorded in yarn.lock"
set -euo pipefail
if [[ ! -f yarn.lock ]]; then
  printf '%s\n' 'yarn.lock is required. Run mise run node:lock, review the lockfile, then retry.' >&2
  exit 1
fi
exec yarn install --immutable "$@"
