import org.jetbrains.compose.ExperimentalComposeLibrary

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
    useKtor()
    useSerialization()
    useContextReceivers()
    commonMain {
        api(projects.controlsConstructor)
        api(libs.koala.plots)
        api(compose.foundation)
        api(compose.material3)
        @OptIn(ExperimentalComposeLibrary::class)
        api(compose.desktop.components.splitPane)
    }
}


readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}