#!/usr/bin/env bash
#MISE description="Install Node dependencies from the lock file"
set -euo pipefail
exec npm ci "$@"
