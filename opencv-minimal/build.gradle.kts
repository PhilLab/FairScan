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

/*
 * Run the native build script when outputs are missing.
 *
 * The script downloads the OpenCV source, compiles a minimal native library
 * (core + imgproc + imgcodecs only), and extracts the Java class bindings
 * from the official OpenCV AAR.
 *
 * Gradle's up-to-date checking ensures this is skipped on subsequent builds
 * as long as the outputs still exist.
 */
tasks.register<Exec>("buildOpenCVNative") {
    workingDir = projectDir
    commandLine("bash", "build-native.sh")

    inputs.file("build-native.sh")
    outputs.dir("src/main/jniLibs")
    outputs.file("libs/opencv-classes.jar")
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
