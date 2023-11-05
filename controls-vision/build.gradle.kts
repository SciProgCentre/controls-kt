plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Dashboard and visualization extensions for devices
""".trimIndent()

val visionforgeVersion = "0.3.0-dev-10"

kscience {
    fullStack("js/controls-vision.js", development = true)
    useKtor()
    useContextReceivers()
    dependencies {
        api(projects.controlsCore)
        api(projects.controlsConstructor)
        api("space.kscience:visionforge-plotly:$visionforgeVersion")
        api("space.kscience:visionforge-markdown:$visionforgeVersion")
        api("space.kscience:visionforge-tables:$visionforgeVersion")
    }

    jvmMain{
        api("space.kscience:visionforge-server:$visionforgeVersion")
        api("io.ktor:ktor-server-cio")
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
