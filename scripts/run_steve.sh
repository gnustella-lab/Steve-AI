#!/bin/bash
set -euo pipefail

# Steve AI Mod - Launch Script
# This script runs Minecraft with the mod from the repository root.

cd "$(dirname "$0")/.."

echo "🎮 Steve AI Mod - Launcher"
echo "================================"
echo ""

# Prefer a caller-provided Java 17, but support a local unpacked JDK for developers.
if [ -z "${JAVA_HOME:-}" ] && [ -d "$PWD/jdk-17.0.2.jdk/Contents/Home" ]; then
    export JAVA_HOME="$PWD/jdk-17.0.2.jdk/Contents/Home"
fi

if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "✅ Using JAVA_HOME=$JAVA_HOME"
else
    echo "⚠️  JAVA_HOME is not set; make sure your default Java is Java 17."
fi

echo "Starting Minecraft..."
echo "⏳ First launch will download assets (~1-2 minutes)"
echo ""

# Run Minecraft. The wrapper JAR is intentionally not committed because this fork
# must not include binary files in PRs.
if [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
    ./gradlew runClient --no-daemon
else
    echo "gradle-wrapper.jar is not committed in this fork because binary files are not allowed in PRs."
    echo "Using local Gradle installation instead. Run 'gradle wrapper --gradle-version 8.4' locally if you need ./gradlew."
    gradle runClient --no-daemon
fi

echo ""
echo "================================"
echo "Minecraft closed. Thanks for testing!"
