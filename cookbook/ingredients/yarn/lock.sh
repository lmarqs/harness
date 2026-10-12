#!/usr/bin/env bash
#MISE description="Resolve Yarn dependencies and update the durable yarn.lock"
set -euo pipefail
exec yarn install "$@"
