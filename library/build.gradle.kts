import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.waqas028"
// CI derives this from the release tag (-PlibraryVersion=1.2.3); local builds fall back to a
// snapshot so an accidental publish can never overwrite a released version. Do NOT rename the
// property to VERSION_NAME -- the vanniktech plugin consumes that name itself and finalises the
// version, after which the coordinates(...) call below fails with "property 'version' is final".
version = providers.gradleProperty("libraryVersion").getOrElse("1.0.0-SNAPSHOT")

kotlin {
    jvm()
    androidLibrary {
        namespace = "com.waqas028.kmpinspector"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // AGP defaults Android resource processing to OFF for multiplatform library modules, which
        // also leaves variant.sources.assets null. Compose wires its
        // copyAndroidMainComposeResourcesToAndroidAssets task through exactly that property
        // (sources.assets?.addGeneratedSourceDirectory(...)), so with it null the task is
        // registered but never given an output directory and composeResources/ never reaches the
        // AAR -- the bundled fonts silently vanish for Android consumers only. Turning resource
        // processing on restores the assets pipeline Compose's own wiring waits for. Do not remove.
        androidResources {
            enable = true
        }

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // api, because KmpInspector's signature is @Composable and callers need the runtime.
            api(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.lifecycle.runtime)
            compileOnly(libs.okhttp)
            compileOnly(libs.room.runtime)
            compileOnly(libs.androidx.work.runtime.ktx)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    // Internal to the library: consumers should never see a generated Res class from a dependency.
    publicResClass = false
    packageOfResClass = "com.waqas028.kmpinspector.resources"
    generateResClass = always
}

mavenPublishing {
    publishToMavenCentral()

    // Maven Central requires signed artifacts, but publishToMavenLocal does not — and signing
    // there fails outright without a GPG key, which breaks local verification. Sign only when a
    // key is actually configured; CI supplies one via ORG_GRADLE_PROJECT_signingInMemoryKey.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "kmp-inspector", version.toString())

    pom {
        name = "KmpInspector"
        description = "In-app debugging overlay for Compose Multiplatform: inspect network " +
            "traffic, database contents, background work, logs and crashes from inside a " +
            "running app on Android, iOS and desktop."
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
