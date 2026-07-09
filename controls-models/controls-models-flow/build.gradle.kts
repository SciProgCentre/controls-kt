plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Models for continuous and discrete flow systems
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