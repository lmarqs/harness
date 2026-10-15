#!/usr/bin/env bash
#MISE description="Refresh the Yarn lock file"
set -euo pipefail
exec yarn install "$@"
