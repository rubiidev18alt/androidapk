#!/usr/bin/env sh

set -e

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "ERROR: Gradle is not installed. Install Gradle or add the Gradle Wrapper files." >&2
  exit 1
fi
