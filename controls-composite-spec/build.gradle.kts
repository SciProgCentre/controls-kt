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
        api(projects.controlsConstructor)
        implementation(projects.magix.magixApi)
        implementation(projects.magix.magixServer)
        implementation(projects.magix.magixRsocket)
        implementation(projects.magix.magixZmq)
        implementation(projects.controlsMagix)
        implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
    }

    commonTest{
        implementation(spclibs.logback.classic)
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
