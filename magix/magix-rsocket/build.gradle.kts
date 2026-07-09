import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Magix endpoint (client) based on RSocket
""".trimIndent()

kscience {
    jvm()
    js()
    native()
    useSerialization {
        json()
    }
    commonMain {
        api(projects.magix.magixApi)
        api(spclibs.kotlinx.io.core)
        api("io.ktor:ktor-client-core")
        api(libs.rsocket.ktor.client)
    }
    jvmMain {
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