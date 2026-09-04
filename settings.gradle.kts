pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KmpInspector"
include(":library")
include(":library-noop")
include(":library-ktor")
include(":library-room")
include(":sample:shared")
include(":sample:androidApp")
