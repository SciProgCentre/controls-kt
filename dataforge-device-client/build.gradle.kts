plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

val ktorVersion: String by extra("1.4.0")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":dataforge-device-core"))
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
                implementation("io.ktor:ktor-client-js:$ktorVersion")
                implementation(npm("text-encoding", "0.7.0"))
                implementation(npm("abort-controller", "3.0.0"))
            }
        }
    }
}