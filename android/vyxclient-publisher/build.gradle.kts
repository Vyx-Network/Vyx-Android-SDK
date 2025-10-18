plugins {
    id("maven-publish")
}

group = "com.vyx"
version = "1.0.0"

publishing {
    publications {
        create<MavenPublication>("vyxclient") {
            groupId = "com.vyx"
            artifactId = "vyxclient"
            version = "1.0.0"

            // Point to the AAR file
            artifact(file("../vyx-sdk/libs/vyxclient.aar"))
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri("../local-maven")
        }
    }
}
