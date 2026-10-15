#!/usr/bin/env bash
#MISE description="Show the AWS identity of the current session"
set -euo pipefail
exec aws sts get-caller-identity "$@"
