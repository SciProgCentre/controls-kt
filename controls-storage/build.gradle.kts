plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra

description = """
    An API for stand-alone Controls-kt device or a hub.
""".trimIndent()

kscience{
    jvm()
    js()
    dependencies {
        api(projects.controlsCore)
    }
    dependencies(jvmMain){
        api(projects.magix.magixApi)
        api(projects.controlsMagix)
        api(projects.magix.magixServer)
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
