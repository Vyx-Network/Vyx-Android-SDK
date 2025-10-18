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
            url = uri("${rootDir}/local-maven")
        }
    }
}

rootProject.name = "Vyx Android SDK"
include(":vyx-sdk")
include(":example-app")
include(":vyxclient-publisher")
