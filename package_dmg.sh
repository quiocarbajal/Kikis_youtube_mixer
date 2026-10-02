#!/bin/bash
set -e

# Detect JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home"
    elif [ -x "/usr/libexec/java_home" ]; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null || /usr/libexec/java_home 2>/dev/null)"
    fi
fi

echo "Using JAVA_HOME: $JAVA_HOME"
echo "Building and packaging YouTube Music Player DMG..."

./gradlew :desktop:packageDmg

echo ""
echo "✅ DMG generated successfully:"
echo "   $(pwd)/desktop/build/dist/YouTubeMusicPlayer-1.0.0.dmg"
