plugins {
    id("ru.mipt.npm.mpp")
}

val ktorVersion: String by rootProject.extra

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":dataforge-device-core"))
                api("io.ktor:ktor-network:$ktorVersion")
            }
        }

    }
}