plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Dashboard and visualization extensions for devices
""".trimIndent()

val visionforgeVersion = "0.3.0-dev-10"

kscience {
    jvm()
    js()
    dependencies {
        api(projects.controlsCore)
        api(projects.controlsConstructor)
        api("space.kscience:visionforge-plotly:$visionforgeVersion")
        api("space.kscience:visionforge-markdown:$visionforgeVersion")
    }

    jvmMain{
        api("space.kscience:visionforge-server:$visionforgeVersion")
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
