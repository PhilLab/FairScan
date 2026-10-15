import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
}

val abiCodes = mapOf(
    "arm64-v8a" to 0,
    "armeabi-v7a" to -1,
    "x86_64" to -2,
)

android {
    namespace = "org.fairscan.standalone"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.fairscan.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 96 // increment by 3 because of ABI-specific APKs
        versionName = libs.versions.fairscanVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val hasSigning = listOf(
        "RELEASE_STORE_FILE",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD"
    ).all { project.hasProperty(it) }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(project.property("RELEASE_STORE_FILE") as String)
                storePassword = project.property("RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // App-specific rules come from the :app library via consumerProguardFiles
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // See https://developer.android.com/build/configure-apk-splits
    val isBuildingBundle = gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
    splits {
        abi {
            // Disable split ABIs when building appBundle: https://issuetracker.google.com/issues/402800800
            isEnable = !isBuildingBundle
            reset()
            include(*abiCodes.keys.toTypedArray())
            isUniversalApk = false
        }
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
}

dependencies {
    implementation(project(":app"))
    // :app only depends on this as `implementation`, so it isn't exposed transitively - declared
    // directly here since this module's own manifest references androidx.core.content.FileProvider.
    implementation(libs.androidx.core.ktx)
}

// See https://developer.android.com/build/configure-apk-splits
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            output.outputFileName = "FairScan-${output.versionName.get()}-${abi}.apk"
            val baseAbiCode = abiCodes[abi]
            if (baseAbiCode != null) {
                output.versionCode.set(output.versionCode.get() + baseAbiCode)
            }
        }
    }
}
