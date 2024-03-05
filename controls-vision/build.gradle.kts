plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Dashboard and visualization extensions for devices
""".trimIndent()

val visionforgeVersion: String by rootProject.extra

kscience {
    fullStack("js/controls-vision.js")
    useKtor()
    useContextReceivers()
    dependencies {
        api(projects.controlsCore)
        api(projects.controlsConstructor)
        api("space.kscience:visionforge-plotly:$visionforgeVersion")
        api("space.kscience:visionforge-markdown:$visionforgeVersion")
//        api("space.kscience:tables-kt:0.2.1")
//        api("space.kscience:visionforge-tables:$visionforgeVersion")
    }

    jvmMain{
        api("space.kscience:visionforge-server:$visionforgeVersion")
        api("io.ktor:ktor-server-cio")
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}