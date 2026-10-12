#!/usr/bin/env bash
#MISE description="Check the current AWS session before an AWS-dependent task"
set -euo pipefail
if ! aws sts get-caller-identity --query Account --output text >/dev/null 2>&1; then
  echo "No AWS session. Sign in with your chosen AWS profile or SSO session, then retry." >&2
  exit 1
fi
