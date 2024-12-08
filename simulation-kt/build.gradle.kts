import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Core interfaces for building a device server
""".trimIndent()

kscience {
    jvm()
    js()
    native()
    wasm()
    useCoroutines()
    useContextReceivers()

    commonMain {
        api(spclibs.kotlinx.datetime)
    }

    jvmTest{
        implementation(spclibs.logback.classic)
    }
}


readme{
    maturity = Maturity.PROTOTYPE
}