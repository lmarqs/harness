#!/usr/bin/env bash
#MISE description="List ready Beads tasks without changing the tracker"
set -euo pipefail
exec bd ready --json "$@"
