import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.waqas028"
version = providers.gradleProperty("libraryVersion").getOrElse("1.0.0-SNAPSHOT")

/**
 * The same public API as :library with empty bodies, so an app can depend on the real artifact in
 * debug and this one in release:
 *
 *     debugImplementation("io.github.waqas028:kmp-inspector:<version>")
 *     releaseImplementation("io.github.waqas028:kmp-inspector-no-op:<version>")
 *
 * Release builds then carry no inspector code, no fonts and no collectors. The model classes are
 * compiled from :library's own source so the two artifacts can never drift apart.
 */
kotlin {
    jvm()
    androidLibrary {
        namespace = "com.waqas028.kmpinspector.noop"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir("../library/src/commonMain/kotlin/com/waqas028/kmpinspector/domain/model")
            dependencies {
                api(compose.runtime)
            }
        }
        androidMain.dependencies {
            compileOnly(libs.okhttp)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates(group.toString(), "kmp-inspector-no-op", version.toString())
    pom {
        name = "KmpInspector no-op"
        description = "Empty implementation of the KmpInspector API for release builds: same " +
            "classes and signatures, no inspector code."
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
