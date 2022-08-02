plugins {
    id("space.kscience.gradle.mpp")
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
                implementation(project(":magix:magix-rsocket"))
                implementation(project(":controls-core"))
            }
        }
    }
}
