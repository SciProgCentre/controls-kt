import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    A kotlin API for magix standard and some zero-dependency magix services
""".trimIndent()

kscience {
    jvm()
    js()
    native()
    useCoroutines()
    useSerialization{
        json()
    }

    commonMain{
        implementation(spclibs.atomicfu)
    }
}

readme{
    maturity = Maturity.EXPERIMENTAL
}
