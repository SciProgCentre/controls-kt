plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

val ktorVersion: String by rootProject.extra

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":dataforge-device-core"))
                implementation(project(":ktor-sse"))
                implementation("io.ktor:ktor-client-core:$ktorVersion")
            }
        }
        jvmMain {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        jsMain {
            dependencies {

            }
        }
    }
}