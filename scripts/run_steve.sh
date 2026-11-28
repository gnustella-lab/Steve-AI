#!/bin/bash
set -euo pipefail

# Steve AI Mod - Launch Script
# This script runs Minecraft with the mod from the repository root.

cd "$(dirname "$0")/.."

echo "🎮 Steve AI Mod - Launcher"
echo "================================"
echo ""

# Minecraft 26.3 / Forge 66.1.9 require Java 25.
# Prefer a caller-provided Java 25, but support a local unpacked JDK for developers.
if [ -z "${JAVA_HOME:-}" ] && [ -d "$PWD/jdk-25.jdk/Contents/Home" ]; then
    export JAVA_HOME="$PWD/jdk-25.jdk/Contents/Home"
fi

if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "✅ Using JAVA_HOME=$JAVA_HOME"
else
    echo "⚠️  JAVA_HOME is not set; make sure your default Java is Java 25."
fi

echo "Starting Minecraft..."
echo "⏳ First launch will download assets (~1-2 minutes)"
echo ""

# Run Minecraft with the committed Gradle 9.7.1 wrapper (fallback for source-only checkouts).
if [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
    ./gradlew runClient --no-daemon
else
    echo "gradle-wrapper.jar is missing; using the local Gradle installation instead."
    echo "Regenerate the wrapper with 'gradle wrapper --gradle-version 9.7.1' if needed."
    gradle runClient --no-daemon
fi

echo ""
echo "================================"
echo "Minecraft closed. Thanks for testing!"
