// Root build file for JitPack
// The actual Android library module is at android/vyx-sdk

plugins {
    id("com.android.library") version "8.12.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
