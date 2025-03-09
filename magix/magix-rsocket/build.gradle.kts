import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Magix endpoint (client) based on RSocket
""".trimIndent()

val ktorVersion: String by rootProject.extra

kscience {
    jvm()
    js()
    native()
    useSerialization {
        json()
    }
    dependencies {
        api(projects.magix.magixApi)
        implementation(spclibs.ktor.client.core)
        implementation(libs.rsocket.ktor.client)
    }
    dependencies(jvmMain) {
        implementation(libs.rsocket.transport.ktor.tcp)
    }
}

kotlin {
    sourceSets {
        getByName("linuxX64Main") {
            dependencies {
                implementation(libs.rsocket.transport.ktor.tcp)
            }
        }
    }
}

readme {
    maturity = Maturity.EXPERIMENTAL
}