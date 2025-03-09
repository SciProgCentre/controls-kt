import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Common utilities and services for Magix endpoints.   
""".trimIndent()

kscience {
    jvm()
    js()
    native()
    useSerialization()
    commonMain {
        api(projects.magix.magixApi)
        api(libs.dataforge.meta)
    }
}

readme {
    maturity = Maturity.EXPERIMENTAL
}