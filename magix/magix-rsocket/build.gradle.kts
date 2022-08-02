plugins {
    id("space.kscience.gradle.mpp")
    id("space.kscience.gradle.native")
    `maven-publish`
}

description = """
    Magix endpoint (client) based on RSocket
""".trimIndent()

kscience {
    useSerialization {
        json()
    }
}

val ktorVersion: String by rootProject.extra
val rsocketVersion: String by rootProject.extra

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.magix.magixApi)
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.rsocket.kotlin:rsocket-ktor-client:$rsocketVersion")
            }
        }
        jvmMain {
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
            }
        }
        linuxX64Main{
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
            }
        }
    }
}