#!/usr/bin/env bash
#MISE description="Run Ralph over a PRD file or Beads epic"
set -euo pipefail
TRACKER='@TRACKER@'
ARGS=(run --agent '@AGENT@' --tracker "$TRACKER")
if [[ "$TRACKER" == json ]]; then
  SOURCE="${1:-@PRD@}"
  if [[ $# -gt 0 ]]; then shift; fi
  ARGS+=(--prd "$SOURCE")
else
  SOURCE="${1:?Supply a Beads epic ID as the first argument}"
  shift
  ARGS+=(--epic "$SOURCE")
fi
@HEADLESS@
exec ralph-tui "${ARGS[@]}" "$@"
