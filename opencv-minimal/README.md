# opencv-minimal - Minified OpenCV for Android

This module builds a **minimal OpenCV native library from source**, containing
only the three modules FairScan actually uses:

| Module       | What it provides                                |
|--------------|-------------------------------------------------|
| `core`       | Mat, Core (rotate, arithmetic, …), CvType, …    |
| `imgproc`    | GaussianBlur, Canny, warpPerspective, cvtColor … |
| `imgcodecs`  | imread / imwrite (JPEG, PNG)                     |

Everything else (video, dnn, features2d, calib3d, objdetect, ml, photo,
stitching, highgui …) is **excluded**, which cuts the native library from
~22 MB down to ~5–8 MB per ABI.

## How it works

`build-native.sh` performs two steps:

1. **Native build from source** - downloads the OpenCV 4.12.0 source tarball
   from GitHub, configures CMake with only the three modules above, and
   cross-compiles for each Android ABI using the NDK.  The resulting
   `libopencv_java4.so` is placed in `src/main/jniLibs/{abi}/`.

2. **Java class extraction** - downloads the official `org.opencv:opencv`
   AAR from Maven Central and extracts `classes.jar`.  This JAR contains
   the Java/JNI wrappers (identical to the ones the full AAR ships).
   Because the Java wrappers are thin JNI stubs, unused module wrappers are
   harmless - they simply never get called.

The Gradle `preBuild` task calls the script automatically when the outputs
are missing.

## Prerequisites

| Tool     | Notes                                                        |
|----------|--------------------------------------------------------------|
| NDK      | Auto-detected from `ANDROID_NDK`, `ANDROID_HOME`, or `local.properties` |
| CMake    | ≥ 3.16 - ships with the Android SDK (`sdkmanager --install "cmake;4.1.2"`) |
| Python 3 | Needed by OpenCV's Java binding generator                    |
| wget / curl | To download source & AAR                                 |

## First build

```bash
# Just build the project - Gradle triggers it automatically:
./gradlew assembleDebug
```

Subsequent builds are instant because the script detects existing outputs
and skips.  Delete `opencv-minimal/src/main/jniLibs/` or
`opencv-minimal/libs/` to force a rebuild.

## Updating the OpenCV version

Edit the `OPENCV_VERSION` variable at the top of `build-native.sh` and
delete the cached outputs:

```bash
rm -rf opencv-minimal/.build opencv-minimal/src/main/jniLibs opencv-minimal/libs
./opencv-minimal/build-native.sh
```

## Adding / removing modules

Set `openCVModulesToInclude` in `gradle.properties` (or pass `-PopenCVModulesToInclude=…` on the
command line).  Gradle tracks this as a proper build input, so changing the value
automatically invalidates the `buildOpenCVNative` task and triggers a rebuild.

```properties
# gradle.properties
openCVModulesToInclude=imgproc,imgcodecs,video
```

Or on the command line:

```bash
./gradlew assembleDebug -PopenCVModulesToInclude=imgproc,imgcodecs,video
```

## F-Droid compatibility

Everything is built from official sources:

* **Native code**: compiled from the [OpenCV source on GitHub](https://github.com/opencv/opencv)
* **Java classes**: extracted from the [official AAR on Maven Central](https://repo1.maven.org/maven2/org/opencv/opencv/)

No third-party or "unknown-origin" pre-compiled binaries are used.
F-Droid's build server has all the required tools (NDK, CMake, Python).
