#!/usr/bin/env bash
#MISE description="Install Python dependencies from the requirements file"
set -euo pipefail
if command -v uv >/dev/null 2>&1; then
  uv pip install -r "$MISE_PROJECT_ROOT/@REQUIREMENTS@"
else
  python3 -m pip install -r "$MISE_PROJECT_ROOT/@REQUIREMENTS@"
fi
