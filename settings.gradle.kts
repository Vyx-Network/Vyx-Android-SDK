// Root settings for JitPack build
// The actual Android project is in the android/ subdirectory

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local Maven repository for vyxclient.aar
        maven {
            url = uri("${rootDir}/android/local-maven")
        }
    }
}

rootProject.name = "vyx-android-sdk"

// Include vyx-sdk module from android subdirectory
include(":vyx-sdk")
project(":vyx-sdk").projectDir = file("android/vyx-sdk")
