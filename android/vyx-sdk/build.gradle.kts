plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.vyx.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // SDK version
        buildConfigField("String", "SDK_VERSION", "\"1.1.0\"")
    }

    testOptions {
        targetSdk = 36
    }

    lint {
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Go Mobile QUIC client (published to local Maven)
    // Build: cd sdk/gomobile && ./build.sh
    // Publish: cd sdk/android && publish-vyxclient.bat
    // Using api() instead of implementation() to expose to consuming apps
    api("com.vyx:vyxclient:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.vyx"
            artifactId = "vyx-sdk"
            version = "1.1.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

// Task to create fat AAR by merging vyxclient.aar into vyx-sdk.aar
tasks.register("createFatAar") {
    dependsOn("assembleRelease")

    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val vyxclientAar = file("${rootProject.projectDir}/local-maven/com/vyx/vyxclient/1.0.0/vyxclient-1.0.0.aar")
        val sdkAar = file("${buildDir}/outputs/aar/vyx-sdk-release.aar")
        val fatAar = file("${buildDir}/outputs/aar/vyx-sdk-fat.aar")
        val tempDir = file("${buildDir}/tmp/fat-aar")
        // Clean temp directory
        delete(tempDir)
        tempDir.mkdirs()

        // Extract SDK AAR
        val sdkExtractDir = file("${tempDir}/sdk")
        sdkExtractDir.mkdirs()
        copy {
            from(zipTree(sdkAar))
            into(sdkExtractDir)
        }

        // Extract vyxclient AAR
        val vyxclientExtractDir = file("${tempDir}/vyxclient")
        vyxclientExtractDir.mkdirs()
        copy {
            from(zipTree(vyxclientAar))
            into(vyxclientExtractDir)
        }

        // Merge directories
        val mergedDir = file("${tempDir}/merged")
        mergedDir.mkdirs()

        // Copy SDK contents EXCEPT classes.jar and libs/ directory
        copy {
            from(sdkExtractDir) {
                exclude("classes.jar")
                exclude("libs/**")
            }
            into(mergedDir)
        }

        // Make sure consumer ProGuard rules are included
        copy {
            from("${projectDir}/consumer-rules.pro")
            into("${mergedDir}/META-INF")
            rename { "proguard/com.vyx.sdk.pro" }
        }

        // Merge JNI libraries from vyxclient
        copy {
            from("${vyxclientExtractDir}/jni")
            into("${mergedDir}/jni")
        }

        // Merge classes from both AARs into a single classes.jar
        val tempClasses = file("${tempDir}/classes")
        tempClasses.mkdirs()

        // Extract SDK classes.jar
        copy {
            from(zipTree("${sdkExtractDir}/classes.jar"))
            into(tempClasses)
        }

        // Extract and merge vyxclient classes.jar
        copy {
            from(zipTree("${vyxclientExtractDir}/classes.jar"))
            into(tempClasses)
        }

        // Create merged classes.jar (contains both SDK and vyxclient classes)
        ant.withGroovyBuilder {
            "jar"("destfile" to "${mergedDir}/classes.jar", "basedir" to tempClasses)
        }

        println("✓ Merged classes from both AARs into single classes.jar")

        // Create fat AAR
        ant.withGroovyBuilder {
            "zip"("destfile" to fatAar, "basedir" to mergedDir)
        }

        println("✅ Fat AAR created: ${fatAar.absolutePath}")
        println("   Size: ${fatAar.length() / 1024 / 1024} MB")
    }
}
