plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Device timed data platform
""".trimIndent()

kscience {
    jvm()
    useCoroutines()
    useSerialization()

    commonMain {
        api(projects.controlsCore)
        api(projects.controlsConstructor)
        api(projects.controlsStorage)

        api(projects.controlsPlc4x)
        api(projects.controlsOpcua)
        api(projects.controlsModbus)

        api(libs.kmath.stat)
        api(libs.tables.kt)
    }

    commonTest {
        implementation(spclibs.logback.classic)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.EXPERIMENTAL
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}