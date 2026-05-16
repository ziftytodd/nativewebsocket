#!/usr/bin/env sh
set -eu

java_major() {
  "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1
}

JDK21_HOME=""

use_jdk21() {
  if [ -n "$1" ] && [ -x "$1/bin/java" ] && [ "$(java_major "$1")" = "21" ]; then
    JDK21_HOME="$1"
  fi
}

use_jdk21 "${JAVA_HOME:-}"

if [ -z "$JDK21_HOME" ]; then
  SDKMAN_JAVA_DIR="${SDKMAN_CANDIDATES_DIR:-$HOME/.sdkman/candidates}/java"
  if [ -d "$SDKMAN_JAVA_DIR" ]; then
    for candidate in "$SDKMAN_JAVA_DIR"/21*; do
      if [ -z "$JDK21_HOME" ] && [ -d "$candidate" ]; then
        use_jdk21 "$candidate"
      fi
    done
  fi
fi

if [ -z "$JDK21_HOME" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  use_jdk21 "$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi

if [ -z "$JDK21_HOME" ] && command -v brew >/dev/null 2>&1; then
  BREW_JDK21="$(brew --prefix openjdk@21 2>/dev/null || true)"
  use_jdk21 "$BREW_JDK21/libexec/openjdk.jdk/Contents/Home"
fi

if [ -z "$JDK21_HOME" ]; then
  use_jdk21 "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi

if [ -z "$JDK21_HOME" ]; then
  use_jdk21 "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi

if [ -z "$JDK21_HOME" ] || [ ! -x "$JDK21_HOME/bin/java" ] || [ "$(java_major "$JDK21_HOME")" != "21" ]; then
  echo "Android verification requires JDK 21 for Capacitor 7." >&2
  echo "Install JDK 21, or set JAVA_HOME to a JDK 21 home before running npm run verify." >&2
  echo "With SDKMAN: sdk install java 21.0.7-amzn" >&2
  exit 1
fi

export JAVA_HOME="$JDK21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using JDK 21 at $JAVA_HOME"

cd android
exec ./gradlew clean build test
