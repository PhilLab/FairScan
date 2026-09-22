plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
dependencies {
    // opencv-classes.jar is produced by the parent project's prepareOpenCV
    // task (shared with :opencv-minimal:android). builtBy wires this into the
    // task graph so Gradle builds the jar before compiling anything that
    // consumes this module, instead of racing it.
    api(files("../libs/opencv-classes.jar").builtBy(project(":opencv-minimal").tasks.named("prepareOpenCV")))
}
