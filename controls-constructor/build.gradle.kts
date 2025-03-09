plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    A low-code constructor for composite devices simulation
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
