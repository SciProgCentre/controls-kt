plugins {
    id("ru.mipt.npm.gradle.mpp")
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
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:$rsocketVersion")
            }
        }
    }
}