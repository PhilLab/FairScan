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
 * it automatically invalidates all per-ABI tasks and triggers a rebuild.
 */
val modulesToInclude: Provider<String> = providers
    .gradleProperty("openCVModulesToInclude")
    .orElse("")

/**
 * OpenCV version string, e.g. "4.12.0".
 * Defined centrally in gradle.properties so all build artefacts stay in sync.
 */
val openCVVersion: Provider<String> = providers
    .gradleProperty("openCVVersion")

/**
 * SHA-256 of the OpenCV source tarball.  Verified by prepare-opencv.sh after
 * downloading to prevent tampering via git-tag rewrites or MITM.
 */
val openCVSourceSha256: Provider<String> = providers
    .gradleProperty("openCVSourceSha256")

/**
 * SHA-256 of the official OpenCV AAR from Maven Central.
 */
val openCVAarSha256: Provider<String> = providers
    .gradleProperty("openCVAarSha256")

/**
 * All ABIs this project ever supports, read from `supportedABIs` in gradle.properties.
 * A Gradle task is registered for each one so Gradle always knows about every
 * possible output – even for ABIs not included in the current build.
 */
val allSupportedAbis: List<String> = (findProperty("supportedABIs") as? String ?: "")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

/**
 * The ABIs to actually build in this invocation.
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
val requestedAbis: List<String> = providers
    .gradleProperty("android.injected.build.abi")
    .orElse(providers.gradleProperty("supportedABIs"))
    .get()
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

// =============================================================================
// prepareOpenCV
//   Downloads the OpenCV source tarball and extracts opencv-classes.jar from
//   the official AAR.  Runs once; skipped as long as the JAR output exists.
// =============================================================================
val prepareOpenCV = tasks.register<Exec>("prepareOpenCV") {
    group = "build"
    description = "Downloads OpenCV source and extracts the Java classes JAR."

    workingDir = projectDir
    inputs.file("../prepare-opencv.sh")
    inputs.property("openCVVersion", openCVVersion)
    inputs.property("openCVSourceSha256", openCVSourceSha256)
    inputs.property("openCVAarSha256", openCVAarSha256)
    outputs.file("../libs/opencv-classes.jar")

    commandLine(
        "bash", "../prepare-opencv.sh",
        openCVVersion.get(),
        openCVSourceSha256.get(),
        openCVAarSha256.get(),
    )
}

// =============================================================================
// buildOpenCVNative_<abi>  (one task per supported ABI)
//   Each task builds libopencv_java4.so for exactly one ABI.
//   Gradle's incremental engine decides independently per ABI whether a rebuild
//   is needed:
//     - .so missing           → runs
//     - modulesToInclude changed → runs  (input changed)
//     - nothing changed       → skipped (up-to-date)
//   Tasks for ABIs not in requestedAbis are never depended upon by the
//   aggregate, so they don't run – and their .so files on disk are preserved
//   for future use.
// =============================================================================
val perAbiTasks = allSupportedAbis.associateWith { abi ->
    val taskName = "buildOpenCVNative_${abi.replace("-", "_").replace(".", "_")}"
    tasks.register<Exec>(taskName) {
        group = "build"
        description = "Builds the OpenCV native library for $abi."

        dependsOn(prepareOpenCV)
        workingDir = projectDir

        inputs.file("../build-native.sh")
        inputs.property("modulesToInclude", modulesToInclude)
        inputs.property("openCVVersion", openCVVersion)
        outputs.file("src/main/jniLibs/$abi/libopencv_java4.so")

        doFirst {
            commandLine("bash", "../build-native.sh", modulesToInclude.get(), abi, openCVVersion.get())
        }
    }
}

// =============================================================================
// buildOpenCVNative  (aggregate)
//   Depends only on the per-ABI tasks for the currently requested ABIs.
//   ABIs outside that set are neither built nor cleaned.
// =============================================================================
tasks.register("buildOpenCVNative") {
    group = "build"
    description = "Builds OpenCV native libraries for all requested ABIs."

    dependsOn(requestedAbis.mapNotNull { perAbiTasks[it] })
}

// =============================================================================
// cleanOpenCVNative
//   Wipes all native outputs so the next build starts from scratch.
// =============================================================================
tasks.register<Delete>("cleanOpenCVNative") {
    delete(
        layout.projectDirectory.dir("src/main/jniLibs"),
        layout.projectDirectory.file("../libs/opencv-classes.jar"),
    )
}

tasks.named("clean") {
    dependsOn("cleanOpenCVNative")
}

tasks.named("preBuild") {
    dependsOn("buildOpenCVNative")
}

dependencies {
    // The JAR is produced by prepareOpenCV; builtBy ensures Gradle wires the
    // task dependency automatically whenever this configuration is resolved.
    api(files("../libs/opencv-classes.jar").builtBy(prepareOpenCV))
}
