#!/usr/bin/env bash
# Fails when any source file exceeds 600 lines (same rule as Cumbre).
set -euo pipefail
LIMIT=600
fail=0
while IFS= read -r f; do
  n=$(wc -l < "$f")
  if [ "$n" -gt "$LIMIT" ]; then
    echo "TOO LONG ($n > $LIMIT): $f"
    fail=1
  fi
done < <(git ls-files '*.kt' '*.kts' '*.swift' '*.sq')
[ "$fail" -eq 0 ] && echo "OK: all files <= $LIMIT lines"
exit "$fail"
