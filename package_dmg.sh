#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# Detect JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/opt/homebrew/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home" ]; then
        export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home"
    elif [ -d "/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home"
    elif [ -x "/usr/libexec/java_home" ]; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null || /usr/libexec/java_home 2>/dev/null)"
    fi
fi

# Ensure .icns file is created from figures/app_icon.jpeg if needed
if [ ! -f "figures/app_icon.icns" ] && [ -f "figures/app_icon.jpeg" ]; then
    echo "Generating figures/app_icon.icns..."
    ICONSET_DIR="/tmp/icon_$$.iconset"
    mkdir -p "$ICONSET_DIR"
    sips -s format png -z 16 16     figures/app_icon.jpeg --out "$ICONSET_DIR/icon_16x16.png" > /dev/null
    sips -s format png -z 32 32     figures/app_icon.jpeg --out "$ICONSET_DIR/icon_16x16@2x.png" > /dev/null
    sips -s format png -z 32 32     figures/app_icon.jpeg --out "$ICONSET_DIR/icon_32x32.png" > /dev/null
    sips -s format png -z 64 64     figures/app_icon.jpeg --out "$ICONSET_DIR/icon_32x32@2x.png" > /dev/null
    sips -s format png -z 128 128   figures/app_icon.jpeg --out "$ICONSET_DIR/icon_128x128.png" > /dev/null
    sips -s format png -z 256 256   figures/app_icon.jpeg --out "$ICONSET_DIR/icon_128x128@2x.png" > /dev/null
    sips -s format png -z 256 256   figures/app_icon.jpeg --out "$ICONSET_DIR/icon_256x256.png" > /dev/null
    sips -s format png -z 512 512   figures/app_icon.jpeg --out "$ICONSET_DIR/icon_256x256@2x.png" > /dev/null
    sips -s format png -z 512 512   figures/app_icon.jpeg --out "$ICONSET_DIR/icon_512x512.png" > /dev/null
    sips -s format png -z 1024 1024 figures/app_icon.jpeg --out "$ICONSET_DIR/icon_512x512@2x.png" > /dev/null
    iconutil -c icns "$ICONSET_DIR" -o figures/app_icon.icns
    rm -rf "$ICONSET_DIR"
fi

echo "Using JAVA_HOME: $JAVA_HOME"
echo "Building and packaging kiki's youtube mixer DMG..."

./gradlew :desktop:packageDmg

echo "✅ DMG generated successfully in root:"
ls -lh "kiki's youtube mixer-0.0.2.dmg" 2>/dev/null || ls -lh ./*.dmg 2>/dev/null
