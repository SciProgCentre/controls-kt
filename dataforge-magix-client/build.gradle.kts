plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

val ktorVersion: String by rootProject.extra

repositories{
    maven("https://maven.pkg.github.com/altavir/ktor-client-sse")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":dataforge-device-core"))
                implementation(project(":dataforge-device-tcp"))
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("ru.mipt.npm:ktor-client-sse:0.1.0")
            }
        }
        jvmMain {
            dependencies {

            }
        }
        jsMain {
            dependencies {

            }
        }
    }
}