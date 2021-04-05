plugins {
    id("ru.mipt.npm.gradle.mpp")
    `maven-publish`
}

kscience{
    useSerialization {
        json()
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":magix:magix-service"))
                implementation(project(":controls-core"))
            }
        }
    }
}