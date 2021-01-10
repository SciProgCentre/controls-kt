plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

val ktorVersion: String by rootProject.extra

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
                implementation(project(":dataforge-device-core"))
            }
        }
    }
}