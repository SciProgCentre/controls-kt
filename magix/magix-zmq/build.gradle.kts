import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    ZMQ client endpoint for Magix
""".trimIndent()

kscience {
    jvm()
    jvmMain {
        api(projects.magix.magixApi)
        api("org.slf4j:slf4j-api:2.0.6")
        api("org.zeromq:jeromq:0.5.3")
    }
}

readme {
    maturity = Maturity.EXPERIMENTAL
}