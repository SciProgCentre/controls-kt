plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Utility devices
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
        api(projects.controlsExpressions)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.EXPERIMENTAL
}