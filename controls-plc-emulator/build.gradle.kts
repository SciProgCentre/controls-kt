plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    An interpreter for IEC 61131-3 PLC programs.
""".trimIndent()

kscience {
    jvm()
    js()
//    native()
//    wasm()
    useCoroutines()
    useSerialization()

    commonMain {
        api(projects.controlsCore)
        api(projects.simulationKt)
        implementation(libs.parsus)
    }

    commonTest {
        implementation(spclibs.logback.classic)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}