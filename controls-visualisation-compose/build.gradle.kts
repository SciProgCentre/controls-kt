plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.compose.compiler)
    alias(spclibs.plugins.compose.jb)
    `maven-publish`
}

description = """
    Visualisation extension using compose-multiplatform
""".trimIndent()

kscience {
    jvm()
    useSerialization()
    commonMain {
        api(projects.controlsConstructor)
        api(libs.lets.plot.kotlin.kernel)
        api(libs.lets.plot.common)
        api(libs.lets.plot.compose)
        api("org.jetbrains.compose.foundation:foundation")
        api("org.jetbrains.compose.material3:material3:1.9.0")
        api("org.jetbrains.compose.material:material-icons-extended:1.7.3")
        api("org.jetbrains.compose.components:components-splitpane:1.11.1")
    }
}


readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}