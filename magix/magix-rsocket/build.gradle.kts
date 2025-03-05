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
        api(spclibs.kotlinx.io.core)
        api(spclibs.ktor.client.core)
        api(libs.rsocket.ktor.client)
    }
    dependencies(jvmMain) {
        api(libs.rsocket.transport.ktor.tcp)
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