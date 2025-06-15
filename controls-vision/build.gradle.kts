plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Dashboard and visualization extensions for devices
""".trimIndent()

kscience {
    fullStack("js/controls-vision.js")
    useSerialization()
    useContextReceivers()
    commonMain {
        api(projects.controlsCore)
        api(projects.controlsConstructor)
        api(libs.plotlykt.core)
        api(libs.visionforge.markdown)
//        api("space.kscience:tables-kt:0.2.1")
//        api("space.kscience:visionforge-tables:$visionforgeVersion")
    }

    jsMain{
        //FIXME remove after VisionForge 0.5
//        api("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.823")
    }

    jvmMain{
        api(libs.visionforge.server)
        api(spclibs.ktor.server.cio)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}