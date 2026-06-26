#!/usr/bin/env bash
# =============================================================================
# Builds a minimal OpenCV native library for Android from source.
#
# The Java class bindings are extracted from the official OpenCV AAR published
# on Maven Central (same source code, just pre-compiled to bytecode).
#
# Prerequisites:
#   - Android NDK (auto-detected from ANDROID_NDK, ANDROID_HOME, or local.properties)
#   - CMake and Ninja (both ship with the Android SDK)
#   - Python 3 (for OpenCV's Java binding generator)
#   - JDK with javac (auto-detected from PATH, JAVA_HOME, Android Studio's bundled JBR,
#     or /usr/lib/jvm; install with: sudo apt install default-jdk)
#
# Parameters
#   $1  Comma-separated OpenCV modules to include (required, pass "" for none).
#       core, java and java_bindings_generator are always appended automatically.
#   $2  Comma-separated ABIs to build for (required).
#       Android Studio passes android.injected.build.abi automatically for
#       device-specific builds; the Gradle task forwards it here as $2.
#
# Usage:
#   ./opencv-minimal/build-native.sh "" "arm64-v8a,armeabi-v7a,x86_64"
#   ./opencv-minimal/build-native.sh "imgproc" "arm64-v8a"
#   ./opencv-minimal/build-native.sh ""         "arm64-v8a,x86_64"
#
# The Gradle build calls this automatically when outputs are missing.
# =============================================================================
set -Eeuo pipefail

OPENCV_VERSION="4.12.0"
SOURCE_URL="https://github.com/opencv/opencv/archive/refs/tags/${OPENCV_VERSION}.tar.gz"
AAR_URL="https://repo1.maven.org/maven2/org/opencv/opencv/${OPENCV_VERSION}/opencv-${OPENCV_VERSION}.aar"
MIN_SDK=26

# Comma-separated list of OpenCV modules to build.
# First argument: OpenCV Modules to include - comma-separated list. Pass an empty string "" to include no extra modules.
if [ $# -lt 1 ]; then
    echo "ERROR: build-native.sh requires at least the modules argument." >&2
    echo "       Pass an empty string to include no extra modules:" >&2
    echo "         ./build-native.sh \"\"" >&2
    echo "         ./build-native.sh \"\" \"arm64-v8a\"" >&2
    exit 1
fi
OPENCV_MODULES="$1"

# Second argument: ABIs to build for – comma-separated list.
if [ $# -lt 2 ] || [ -z "$2" ]; then
    echo "ERROR: build-native.sh requires the ABI list as the second argument." >&2
    echo "       Example: ./build-native.sh \"imgproc\" \"arm64-v8a,x86_64\"" >&2
    exit 1
fi
IFS=',' read -ra ABIS <<< "$2"
echo "ABIs       : ${ABIS[*]}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_ROOT="$SCRIPT_DIR/build/opencv-native"
SOURCE_DIR="$BUILD_ROOT/opencv-${OPENCV_VERSION}"
JNILIBS_DIR="$SCRIPT_DIR/src/main/jniLibs"
LIBS_DIR="$SCRIPT_DIR/libs"

# --- Error reporting for scripting errors ---
on_err() {
    local rc=$?
    echo "ERROR: build-native.sh failed at line ${BASH_LINENO[0]}: ${BASH_COMMAND}" >&2
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

# ---------- Locate the Android NDK ----------
find_ndk() {
    for var in ANDROID_NDK ANDROID_NDK_HOME; do
        local val="${!var:-}"
        if [ -n "$val" ] && [ -d "$val/build/cmake" ]; then
            echo "$val"; return
        fi
    done

    # Try local.properties → sdk.dir → ndk/
    local props="$PROJECT_ROOT/local.properties"
    if [ -f "$props" ]; then
        local sdk
        sdk=$(awk -F= '/^sdk.dir=/{print $2}' "$props" | tr -d '[:space:]')
        if [ -n "$sdk" ] && [ -d "$sdk/ndk" ]; then
            local ndk
            ndk=$(find "$sdk/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
            if [ -n "$ndk" ]; then echo "$ndk"; return; fi
        fi
    fi

    # Try ANDROID_HOME
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
        local ndk
        ndk=$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
        if [ -n "$ndk" ]; then echo "$ndk"; return; fi
    fi

    echo "ERROR: Cannot find Android NDK.  Install one via SDK Manager or set ANDROID_NDK." >&2
    exit 1
}

NDK=$(find_ndk)
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"
echo "NDK        : $NDK"
[ -f "$TOOLCHAIN" ] || { echo "ERROR: CMake toolchain not found: $TOOLCHAIN" >&2; exit 1; }

# ---------- Locate CMake (prefer Android SDK's cmake over system cmake) ----------
find_cmake_bin() {
    # Resolve SDK dir from local.properties or ANDROID_HOME
    local sdk=""
    local props="$PROJECT_ROOT/local.properties"
    if [ -f "$props" ]; then
        sdk=$(awk -F= '/^sdk.dir=/{print $2}' "$props" | tr -d '[:space:]')
    fi
    [ -z "$sdk" ] && sdk="${ANDROID_HOME:-}"

    # Prefer the SDK-bundled cmake (highest installed version wins)
    if [ -n "$sdk" ] && [ -d "$sdk/cmake" ]; then
        local cmake_bin
        cmake_bin=$(find "$sdk/cmake" -mindepth 2 -maxdepth 2 -type d -name bin 2>/dev/null | sort -V | tail -n 1)
        if [ -n "$cmake_bin" ] && [ -f "$cmake_bin/cmake" ]; then
            export PATH="$cmake_bin:$PATH"
            echo "CMake      : $cmake_bin/cmake"
            return
        fi
    fi

    # Fall back to whatever is on PATH
    if command -v cmake &>/dev/null; then
        echo "CMake      : $(command -v cmake)"
        return
    fi

    echo "ERROR: cmake not found.  Install it via: sdkmanager --install \"cmake;4.1.2\"" >&2
    exit 1
}

find_cmake_bin

# ---------- Host tool paths derived from NDK ----------
NDK_HOST_TAG="linux-x86_64"
NDK_HOST_PREBUILT="$NDK/toolchains/llvm/prebuilt/$NDK_HOST_TAG"

STRIP_BIN="$NDK_HOST_PREBUILT/bin/llvm-strip"
[ -f "$STRIP_BIN" ] || STRIP_BIN=""  # stripping is optional

# jni.h - sanity-check that the NDK sysroot is intact (the NDK compiler
# picks it up automatically; we don't need to pass it to cmake).
JNI_INCLUDE="$NDK_HOST_PREBUILT/sysroot/usr/include"
if [ ! -f "$JNI_INCLUDE/jni.h" ]; then
    echo "ERROR: jni.h not found at $JNI_INCLUDE - NDK installation may be corrupt." >&2
    exit 1
fi
echo "jni.h      : $JNI_INCLUDE/jni.h"

# javac - needed by OpenCV's cmake to enable the Java-wrapper code generator
# (generates the JNI C++ bindings that go into libopencv_java4.so).
find_javac() {
    # 1. Already on PATH
    if command -v javac &>/dev/null; then echo "$(command -v javac)"; return; fi

    # 2. JAVA_HOME set explicitly
    if [ -n "${JAVA_HOME:-}" ] && [ -f "$JAVA_HOME/bin/javac" ]; then
        echo "$JAVA_HOME/bin/javac"; return
    fi

    # 3. Android Studio's bundled JetBrains Runtime (jbr/) - common Linux paths
    local studio_roots=(
        "/opt/android-studio"
        "/usr/local/android-studio"
        "$HOME/android-studio"
        "$HOME/.local/share/JetBrains/Toolbox/apps/AndroidStudio/ch-0"  # Toolbox installs
        "/snap/android-studio/current/android-studio"
    )
    for root in "${studio_roots[@]}"; do
        [ -d "$root" ] || continue

        # Toolbox nests one more version directory; glob handles both cases
        local javac_bin
        javac_bin=$(find "$root" -maxdepth 4 -path "*/jbr/bin/javac" 2>/dev/null | sort -V | tail -n 1)
        [ -n "$javac_bin" ] && { echo "$javac_bin"; return; }
        javac_bin=$(find "$root" -maxdepth 4 -path "*/jre/bin/javac" 2>/dev/null | sort -V | tail -n 1)
        [ -n "$javac_bin" ] && { echo "$javac_bin"; return; }
    done

    # 4. System JVMs (/usr/lib/jvm/*)
    local javac_bin
    if [ -d /usr/lib/jvm ]; then
        javac_bin=$(find /usr/lib/jvm -maxdepth 3 -name "javac" 2>/dev/null | sort -V | tail -n 1)
    else
        javac_bin=""
    fi
    [ -n "$javac_bin" ] && { echo "$javac_bin"; return; }

    echo ""
}

JAVAC_BIN=$(find_javac)
if [ -z "$JAVAC_BIN" ]; then
    echo "ERROR: javac not found.  OpenCV's cmake needs it to generate the JNI" >&2
    echo "       bindings inside libopencv_java4.so.  Fix with one of:" >&2
    echo "         sudo apt install default-jdk" >&2
    echo "         export JAVA_HOME=/path/to/jdk" >&2
    exit 1
fi
echo "javac from : $JAVAC_BIN"

# ---------- Download OpenCV source ----------
mkdir -p "$BUILD_ROOT"
if [ ! -d "$SOURCE_DIR" ]; then
    TARBALL="$BUILD_ROOT/${OPENCV_VERSION}.tar.gz"
    if [ ! -f "$TARBALL" ]; then
        echo "Downloading OpenCV ${OPENCV_VERSION} source ..."
        download_file "$SOURCE_URL" "$TARBALL"
    fi
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
    echo "Extracting ..."
    mkdir -p "$LIBS_DIR" "$BUILD_ROOT/aar-extract"
    unzip -o -q "$AAR_FILE" classes.jar -d "$BUILD_ROOT/aar-extract"
    mv "$BUILD_ROOT/aar-extract/classes.jar" "$LIBS_DIR/opencv-classes.jar"
    echo "Java classes : $(du -h "$LIBS_DIR/opencv-classes.jar" | cut -f1)"
fi

# ---------- Build native library for each ABI ----------
for ABI in "${ABIS[@]}"; do
    SO_OUT="$JNILIBS_DIR/$ABI/libopencv_java4.so"
    if [ -f "$SO_OUT" ]; then
        # Rebuild, probably due to changed modules. Delete the old .so
        rm -rf "$SO_OUT"
    fi

    echo ""
    echo "========================================"
    echo " Building OpenCV $OPENCV_VERSION for $ABI"
    echo "========================================"

    BUILD_DIR="$BUILD_ROOT/build-$ABI"

    # Delete the entire build directory to guarantee a clean cmake configure.
    # (Deleting only CMakeCache.txt leaves stale state in CMakeFiles/ that
    # cmake 4.x may restore, causing detection results to be wrong.)
    # The static-lib .o files live inside CMakeFiles/ too, so this forces a
    # full rebuild - acceptable because outputs are cached in jniLibs/ after
    # the first successful build.
    echo "Cleaning build dir for fresh cmake configure ..."
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"

    CMAKE_LOG="$BUILD_DIR/cmake-configure.log"

    cmake -S "$SOURCE_DIR" -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_NATIVE_API_LEVEL="$MIN_SDK" \
        -DCMAKE_BUILD_TYPE=Release \
        \
        -DBUILD_LIST="${OPENCV_MODULES},core,java,java_bindings_generator" \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_FAT_JAVA_LIB=ON \
        -DBUILD_JAVA=ON \
        \
        ${JAVAC_BIN:+-DANDROID_JAVAC="$JAVAC_BIN"} \
        -DJava_FOUND=TRUE \
        -DANDROID_BUILD_BASE_DIR="$BUILD_DIR/opencv_android" \
        -DANDROID_TMP_INSTALL_BASE_DIR="$BUILD_DIR/opencv_android_install" \
        -DANDROID_GRADLE_JAVA_VERSION_INIT=17 \
        -DOPENCV_ANDROID_NAMESPACE_DECLARATION="namespace 'org.opencv'" \
        \
        -DBUILD_ANDROID_PROJECTS=OFF \
        -DBUILD_ANDROID_EXAMPLES=OFF \
        -DBUILD_DOCS=OFF \
        -DBUILD_EXAMPLES=OFF \
        -DBUILD_PERF_TESTS=OFF \
        -DBUILD_TESTS=OFF \
        \
        -DWITH_OPENCL=OFF \
        -DWITH_CUDA=OFF \
        -DWITH_VULKAN=OFF \
        -DWITH_PROTOBUF=OFF \
        -DWITH_QUIRC=OFF \
        -DWITH_TIFF=OFF \
        -DWITH_WEBP=OFF \
        -DWITH_OPENJPEG=OFF \
        -DWITH_JASPER=OFF \
        -DWITH_OPENEXR=OFF \
        -DWITH_IPP=OFF \
        -DWITH_ITT=OFF \
        -DWITH_EIGEN=OFF \
        -DWITH_LAPACK=OFF \
        -DWITH_JPEG=ON \
        -DWITH_PNG=ON \
        -DBUILD_ZLIB=ON \
        -DBUILD_PNG=ON \
        -DBUILD_JPEG=ON \
        2>&1 | tee "$CMAKE_LOG" | tail -30

    # Show Java-related detection lines so failures are obvious
    echo "--- Java detection summary ---"
    grep -i "java\|jni\|ant\|python.*build\|HAVE_opencv_java\|module.*disabled\|disabled.*java" \
         "$CMAKE_LOG" | grep -v "^--$" || true
    echo "------------------------------"

    # Build – Java compilation may fail (we only need the .so), hence || true
    cmake --build "$BUILD_DIR" -j "$(nproc)" 2>&1 \
        || echo "  (partial build failure - checking for .so)"

    # Locate the .so
    SO_FILE=$(find "$BUILD_DIR/lib" -name "libopencv_java4.so" 2>/dev/null | head -1)
    [ -n "$SO_FILE" ] || SO_FILE=$(find "$BUILD_DIR" -name "libopencv_java*.so" 2>/dev/null | head -1)
    if [ -z "$SO_FILE" ]; then
        echo "ERROR: native library not found for $ABI" >&2
        echo "       Check build output above for errors." >&2
        exit 1
    fi

    mkdir -p "$JNILIBS_DIR/$ABI"
    cp "$SO_FILE" "$SO_OUT"

    # Strip debug symbols for minimum size
    if [ -n "$STRIP_BIN" ]; then
        "$STRIP_BIN" --strip-unneeded "$SO_OUT"
    fi

    echo "[$ABI] → $(du -h "$SO_OUT" | cut -f1)"
done

echo ""
echo "========================================"
echo " Build complete"
echo "========================================"
for ABI in "${ABIS[@]}"; do
    [ -f "$JNILIBS_DIR/$ABI/libopencv_java4.so" ] && \
        echo "  $ABI : $(du -h "$JNILIBS_DIR/$ABI/libopencv_java4.so" | cut -f1)"
done
echo "  Java   : $(du -h "$LIBS_DIR/opencv-classes.jar" | cut -f1)"
