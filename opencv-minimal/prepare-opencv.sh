#!/usr/bin/env bash
# =============================================================================
# Downloads the OpenCV source and extracts the Java class bindings JAR from
# the official OpenCV AAR published on Maven Central.
#
# Must be run once before any per-ABI build-native.sh invocation.
# Both operations are idempotent – already-present files are not re-downloaded.
#
# SHA-256 hashes are verified after every download to detect tampering via
# git-tag rewrites or man-in-the-middle attacks.
#
# Parameters
#   $1  OpenCV version string (required), e.g. "4.12.0".
#       Defined in gradle.properties (openCVVersion) and forwarded by Gradle.
#   $2  Expected SHA-256 of the source tarball (required).
#       Defined in gradle.properties (openCVSourceSha256).
#   $3  Expected SHA-256 of the official AAR (required).
#       Defined in gradle.properties (openCVAarSha256).
#
# Usage:
#   ./opencv-minimal/prepare-opencv.sh "4.12.0" "<tarball-sha256>" "<aar-sha256>"
#
# The Gradle build calls this automatically via the prepareOpenCV task.
# =============================================================================
set -Eeuo pipefail

# ---------- Parameters ----------
if [ $# -lt 3 ] || [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
    echo "ERROR: prepare-opencv.sh requires three arguments." >&2
    echo "       Usage: prepare-opencv.sh <version> <tarball-sha256> <aar-sha256>" >&2
    echo "       These values come from gradle.properties and are forwarded by Gradle." >&2
    exit 1
fi

OPENCV_VERSION="$1"
EXPECTED_SOURCE_SHA256="$2"
EXPECTED_AAR_SHA256="$3"

SOURCE_URL="https://github.com/opencv/opencv/archive/refs/tags/${OPENCV_VERSION}.tar.gz"
AAR_URL="https://repo1.maven.org/maven2/org/opencv/opencv/${OPENCV_VERSION}/opencv-${OPENCV_VERSION}.aar"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_ROOT="$SCRIPT_DIR/build/opencv-native"
SOURCE_DIR="$BUILD_ROOT/opencv-${OPENCV_VERSION}"
LIBS_DIR="$SCRIPT_DIR/libs"

echo "OpenCV     : $OPENCV_VERSION"

# --- Error reporting for scripting errors ---
on_err() {
    local rc=$?
    echo "ERROR: prepare-opencv.sh failed at line ${BASH_LINENO[0]}: ${BASH_COMMAND}" >&2
    exit "$rc"
}
trap on_err ERR

# Download a file without progress spam; only errors are printed.
download_file() {
    local url="$1"
    local out="$2"

    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --silent --show-error --output "$out" "$url" && return 0
    fi

    if command -v wget >/dev/null 2>&1; then
        wget -q -O "$out" "$url" && return 0
    fi

    echo "ERROR: failed to download $url" >&2
    return 1
}

# Verify a file's SHA-256 against an expected value.
# Deletes the file and exits with an error if the hash does not match.
verify_sha256() {
    local file="$1"
    local expected="$2"

    local actual
    if command -v sha256sum >/dev/null 2>&1; then
        actual=$(sha256sum "$file" | awk '{print $1}')
    elif command -v shasum >/dev/null 2>&1; then
        actual=$(shasum -a 256 "$file" | awk '{print $1}')
    else
        echo "ERROR: neither sha256sum nor shasum found – cannot verify download integrity." >&2
        exit 1
    fi

    if [ "$actual" != "$expected" ]; then
        echo "ERROR: SHA-256 mismatch for $(basename "$file")" >&2
        echo "  expected : $expected" >&2
        echo "  actual   : $actual" >&2
        echo "  The downloaded file has been removed. Check openCVVersion / hash values" >&2
        echo "  in gradle.properties, or re-run the build to re-download." >&2
        rm -f "$file"
        exit 1
    fi

    echo "SHA-256 OK : $(basename "$file")"
}

# ---------- Download OpenCV source ----------
mkdir -p "$BUILD_ROOT"
if [ ! -d "$SOURCE_DIR" ]; then
    TARBALL="$BUILD_ROOT/${OPENCV_VERSION}.tar.gz"
    if [ ! -f "$TARBALL" ]; then
        echo "Downloading OpenCV ${OPENCV_VERSION} source ..."
        download_file "$SOURCE_URL" "$TARBALL"
    fi
    verify_sha256 "$TARBALL" "$EXPECTED_SOURCE_SHA256"
    echo "Extracting ..."
    tar xf "$TARBALL" -C "$BUILD_ROOT"
fi

# ---------- Extract Java classes from official AAR ----------
if [ ! -f "$LIBS_DIR/opencv-classes.jar" ]; then
    AAR_FILE="$BUILD_ROOT/opencv-${OPENCV_VERSION}.aar"
    if [ ! -f "$AAR_FILE" ]; then
        echo "Downloading official OpenCV ${OPENCV_VERSION} AAR (for Java class bindings) ..."
        download_file "$AAR_URL" "$AAR_FILE"
    fi
    verify_sha256 "$AAR_FILE" "$EXPECTED_AAR_SHA256"
    echo "Extracting ..."
    mkdir -p "$LIBS_DIR" "$BUILD_ROOT/aar-extract"
    unzip -o -q "$AAR_FILE" classes.jar -d "$BUILD_ROOT/aar-extract"
    mv "$BUILD_ROOT/aar-extract/classes.jar" "$LIBS_DIR/opencv-classes.jar"
fi

echo "Source     : $SOURCE_DIR"
echo "Java JAR   : $(du -h "$LIBS_DIR/opencv-classes.jar" | cut -f1)  ($LIBS_DIR/opencv-classes.jar)"
