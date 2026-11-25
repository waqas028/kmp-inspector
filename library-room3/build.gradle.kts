import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.ksp) // only for the test database below; nothing generated ships
}

group = "io.github.waqas028"
version = providers.gradleProperty("libraryVersion").getOrElse("1.0.0-SNAPSHOT")

/**
 * Room 3 support. Room 3 lives in its own package (`androidx.room3`), so it cannot share a class
 * with the Room 2 module; apps pick the artifact that matches their Room. Room 3 has no
 * SupportSQLite path, so every platform, Android included, reads through the connection API.
 */
kotlin {
    jvm()
    androidLibrary {
        namespace = "com.waqas028.kmpinspector.room3"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":library"))
            implementation(libs.room3.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.sqlite.bundled)
        }
    }
}

// The desktop test builds a real Room 3 database, so Room's processor runs for that test only.
dependencies {
    add("kspJvmTest", libs.room3.compiler)
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates(group.toString(), "kmp-inspector-room3", version.toString())
    pom {
        name = "KmpInspector Room 3"
        description = "Shows a Room 3 database in the KmpInspector Database panel on Android, iOS and desktop."
        inceptionYear = "2026"
        url = "https://github.com/waqas028/kmp-inspector/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "waqas028"
                name = "Muhammad Waqas"
                url = "https://github.com/waqas028/"
            }
        }
        scm {
            url = "https://github.com/waqas028/kmp-inspector/"
            connection = "scm:git:git://github.com/waqas028/kmp-inspector.git"
            developerConnection = "scm:git:ssh://git@github.com/waqas028/kmp-inspector.git"
        }
    }
}
