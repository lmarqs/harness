#!/usr/bin/env bash
#MISE description="Refresh the npm lock file without installing"
set -euo pipefail
exec npm install --package-lock-only "$@"
