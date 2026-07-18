import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

kscience {
    jvm()
    js()
    native()
    wasmJs()
    useCoroutines()

    commonMain {
        api(spclibs.kotlinx.datetime)
    }

    jvmTest {
        implementation(spclibs.logback.classic)
    }
}


readme {
    maturity = Maturity.PROTOTYPE
    description = """
        A framework for combination of asynchronous simulations.        
    """.trimIndent()

    feature("timeline") { "Timeline is an ordered discrete history containing TimeLineEvent" }
}