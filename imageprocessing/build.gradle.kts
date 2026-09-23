plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    `maven-publish`
}

// :app is published standalone (see app/build.gradle.kts) and depends on this module,
// so this module needs to be published too, under the same group so :app's POM
// dependency on it resolves.
group = "org.fairscan"
version = "${libs.versions.fairscanVersionName.get()}-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
dependencies {
    implementation(libs.opencvjava)

    testImplementation(kotlin("test"))
    testImplementation(libs.assertj)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])
        }
    }
}
