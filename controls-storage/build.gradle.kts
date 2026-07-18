plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    An API for stand-alone Controls-kt device or a hub.
""".trimIndent()

kscience {
    jvm()
    js()
    dependencies {
        api(projects.controlsCore)
        api(spclibs.kotlinx.serialization.json)
        api(libs.tables.kt)
    }
    jvmMain {
//        api(projects.magix.magixApi)
//        api(projects.controlsMagix)
//        api(projects.magix.magixServer)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
