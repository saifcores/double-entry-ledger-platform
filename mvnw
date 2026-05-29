#!/usr/bin/env bash
set -euo pipefail

resolve_java21() {
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE 'version "21'; then
    return 0
  fi

  local sdkman_java="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java"
  if [ -d "$sdkman_java" ]; then
    local candidate
    for candidate in "$sdkman_java"/21.*; do
      if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
        export JAVA_HOME="$candidate"
        return 0
      fi
    done
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local mac_home
    mac_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [ -n "$mac_home" ]; then
      export JAVA_HOME="$mac_home"
      return 0
    fi
  fi

  for candidate in \
    /usr/lib/jvm/java-21-openjdk \
    /usr/lib/jvm/temurin-21-jdk \
    /usr/lib/jvm/java-21-openjdk-amd64; do
    if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      return 0
    fi
  done

  return 1
}

if ! resolve_java21; then
  echo "ERROR: JDK 21 is required but was not found." >&2
  echo "Install Temurin 21 (https://adoptium.net/) or run: sdk install java 21.0.8-tem" >&2
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"
exec mvn "$@"
