plugins {
    id("scientifik.mpp")
    id("scientifik.publish")
}

val ktorVersion: String by extra("1.3.2")


kotlin {
    sourceSets {
        commonMain{
            dependencies {
                implementation(project(":dataforge-device-core"))
                implementation("io.ktor:ktor-client-core:$ktorVersion")
            }
        }
        jvmMain{
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
    }
}