plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Magix endpoint (client) based on RSocket
""".trimIndent()

val ktorVersion: String by rootProject.extra
val rsocketVersion: String by rootProject.extra

kscience {
    jvm()
    js()
    native()
    useSerialization {
        json()
    }
    dependencies {
        api(projects.magix.magixApi)
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.rsocket.kotlin:rsocket-ktor-client:$rsocketVersion")
    }
    dependencies(jvmMain) {
        implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
    }
}

kotlin {
    sourceSets {
        getByName("linuxX64Main") {
            dependencies {
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-tcp:$rsocketVersion")
            }
        }
    }
}