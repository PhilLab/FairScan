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

/*
 * Run the native build script when outputs are missing.
 *
 * The script downloads the OpenCV source, compiles a minimal native library,
 * and extracts the Java class bindings from the official OpenCV AAR.
 *
 * Gradle's up-to-date checking ensures this is skipped on subsequent builds
 * as long as the outputs still exist.
 */
tasks.register<Exec>("buildOpenCVNative") {
    workingDir = projectDir

    inputs.file("build-native.sh")
    inputs.property("modulesToInclude", modulesToInclude)
    outputs.dir("src/main/jniLibs")
    outputs.file("libs/opencv-classes.jar")

    doFirst {
        commandLine("bash", "build-native.sh", modulesToInclude.get())
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
