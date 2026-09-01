import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "com.waqas028"
version = "1.0.0"

kotlin {
    jvm()
    androidLibrary {
        namespace = "com.waqas028.kmpinspector"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

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
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "kmp-inspector", version.toString())

    pom {
        name = "KmpInspector"
        description = "A Kotlin Multiplatform library."
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
