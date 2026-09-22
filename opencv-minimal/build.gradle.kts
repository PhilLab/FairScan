// Shared by :opencv-minimal:android and :opencv-minimal:java. They're
// otherwise independent leaf modules; the OpenCV classes jar just happens to
// be needed by both, so the task that produces it lives here instead of in
// either child.

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

// =============================================================================
// prepareOpenCV
//   Downloads the OpenCV source tarball and extracts opencv-classes.jar from
//   the official AAR.  Runs once; skipped as long as the JAR output exists.
// =============================================================================
tasks.register<Exec>("prepareOpenCV") {
    group = "build"
    description = "Downloads OpenCV source and extracts the Java classes JAR."

    workingDir = projectDir
    inputs.file("prepare-opencv.sh")
    inputs.property("openCVVersion", openCVVersion)
    inputs.property("openCVSourceSha256", openCVSourceSha256)
    inputs.property("openCVAarSha256", openCVAarSha256)
    outputs.file("libs/opencv-classes.jar")

    commandLine(
        "bash", "prepare-opencv.sh",
        openCVVersion.get(),
        openCVSourceSha256.get(),
        openCVAarSha256.get(),
    )
}
