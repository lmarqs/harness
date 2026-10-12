#!/usr/bin/env bash
#MISE description="Resolve npm dependencies and update the durable package-lock.json"
set -euo pipefail
exec npm install --package-lock-only "$@"
