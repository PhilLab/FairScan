plugins {
    id("com.android.library")
}

android {
    namespace = "org.opencv.minimal"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
}

/**
 * Comma-separated list of OpenCV modules to compile into libopencv_java4.so.
 * `core`, `java` and `java_bindings_generator` are always added by the build script.
 *
 * Set the value in `gradle.properties` (or pass `-PopenCVModulesToInclude=…` on the command line):
 *
 * ```
 * openCVModulesToInclude=imgproc,imgcodecs,video
 * ```
 *
 * Using a `Provider` ensures Gradle tracks the value as a proper build input, so changing
 * it automatically invalidates `buildOpenCVNative` and triggers a rebuild.
 */
val modulesToInclude: Provider<String> = providers
    .gradleProperty("openCVModulesToInclude")
    .orElse("")

/**
 * Comma-separated list of ABIs to compile the native library for.
 *
 * When Android Studio builds for a specific connected device it automatically injects
 * `android.injected.build.abi` (e.g. `arm64-v8a`), so only that one ABI is built –
 * no manual configuration required.  When the property is absent (release builds, CI,
 * command-line `assembleRelease`) the full list from `supportedABIs` in gradle.properties
 * is used.
 *
 * Override from the command line if needed:
 * ```
 * ./gradlew buildOpenCVNative -Pandroid.injected.build.abi=arm64-v8a
 * ```
 */
val abisToBuild: Provider<String> = providers
    .gradleProperty("android.injected.build.abi")
    .orElse(providers.gradleProperty("supportedABIs"))

/*
 * Run the native build script when outputs are missing or inputs have changed.
 *
 * The script downloads the OpenCV source, compiles a minimal native library,
 * and extracts the Java class bindings from the official OpenCV AAR.
 *
 * Gradle's up-to-date checking ensures this is skipped on subsequent builds
 * as long as the outputs still exist and no inputs changed.
 */
tasks.register<Exec>("buildOpenCVNative") {
    workingDir = projectDir

    inputs.file("build-native.sh")
    inputs.property("modulesToInclude", modulesToInclude)
    inputs.property("abisToBuild", abisToBuild)
    outputs.dir("src/main/jniLibs")
    outputs.file("libs/opencv-classes.jar")

    doFirst {
        commandLine("bash", "build-native.sh", modulesToInclude.get(), abisToBuild.get())
    }
}

tasks.register<Delete>("cleanOpenCVNative") {
    // Keep native outputs outside build/ cleaned; build/opencv-native is handled by clean.
    delete(
        layout.projectDirectory.dir("src/main/jniLibs"),
        layout.projectDirectory.file("libs/opencv-classes.jar"),
    )
}

tasks.named("clean") {
    dependsOn("cleanOpenCVNative")
}

tasks.named("preBuild") {
    dependsOn("buildOpenCVNative")
}

dependencies {
    api(files("libs/opencv-classes.jar").builtBy("buildOpenCVNative"))
}
