plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    An extension for creating composite devices
""".trimIndent()

kscience{
    jvm()
    js()
    native()
    wasm()
    useCoroutines()
    useSerialization()
    commonMain {
        api(projects.controlsCore)
    }

    commonTest{
        implementation(spclibs.logback.classic)
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
