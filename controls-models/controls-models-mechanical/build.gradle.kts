plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Models for mechanical devices
""".trimIndent()

kscience {
    jvm()
    js()
    native()
//    wasm()
    useCoroutines()
    useSerialization()

    commonMain {
        api(projects.controlsConstructor)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.EXPERIMENTAL
}