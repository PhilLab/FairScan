import java.net.URL
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class DownloadTFLiteModelTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val modelVersion = "v1.2.0"
        val modelFileName = "fairscan-segmentation-model.tflite"
        val modelUrl = "https://github.com/pynicolas/fairscan-segmentation-model/releases/download/$modelVersion/$modelFileName"

        val outputFile = outputDirectory.file(modelFileName).get().asFile

        if (!outputFile.exists()) {
            println("Downloading $modelFileName from $modelUrl")
            outputFile.parentFile.mkdirs()

            URL(modelUrl).openStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            println("Model already downloaded: ${outputFile.absolutePath}")
        }
    }
}

val downloadTFLiteModel = tasks.register<DownloadTFLiteModelTask>("downloadTFLiteModel") {
    outputDirectory.set(layout.buildDirectory.dir("downloads/tflite"))
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutLibrariesAndroid)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "org.fairscan"
// Appending -SNAPSHOT (rather than reusing fairscanVersionName as-is) marks this a
// changing module, so consumers resolving it from mavenLocal always pick up a fresh
// republish instead of trusting a stale cached artifact under the same version.
version = "${libs.versions.fairscanVersionName.get()}-SNAPSHOT"

android {
    namespace = "org.fairscan.app"
    compileSdk = 37

    publishing {
        singleVariant("release")
    }

    defaultConfig {
        // Based on tests against virtual devices, the app works with Android 8.0 (API level 26).
        // It crashes because of LiteRT on earlier versions.
        // LiteRT documentation only states that version 1.2.0 requires Android 12:
        // https://ai.google.dev/edge/litert/android/index
        minSdk = 26
        // The About screen and PDF metadata read BuildConfig.VERSION_NAME from this
        // module. Library modules don't get a VERSION_NAME field automatically
        // (only apps) and don't inherit it from the consuming app, so it's added
        // explicitly here, sourced from the version catalog as single source of truth.
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.fairscanVersionName.get()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            downloadTFLiteModel,
            DownloadTFLiteModelTask::outputDirectory,
        )
    }
}

dependencies {

    implementation(project(":imageprocessing")) {
        exclude(group = "org.openpnp", module = "opencv")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.litert)
    implementation(libs.litert.support)
    implementation(libs.litert.metadata)
    implementation(libs.opencv)
    implementation(libs.pdfbox) {
        // To reduce APK size
        exclude("org.bouncycastle")
    }
    implementation(libs.icons.extended)
    implementation(libs.zoomable)
    implementation(libs.reorderable)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.tesseract4android)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

aboutLibraries {
    library {
        // Enable the duplication mode, allows to merge, or link dependencies which relate
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        // Configure the duplication rule, to match "duplicates" with
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

// components["release"] only exists after AGP finishes configuring the release variant,
// so this publication must be registered afterEvaluate.
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}
