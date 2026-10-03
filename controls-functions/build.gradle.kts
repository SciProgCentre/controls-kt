plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Mathematical expressions and functions.
""".trimIndent()

kscience {

    jvm {
        compilerOptions {
            optIn.add("space.kscience.dataforge.misc.DFExperimental")
        }
    }

    js()

    useCoroutines()
    useSerialization()

    commonMain {
        api(projects.controlsConstructor)

        api(libs.kmath.ast)
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
        optIn.add("space.kscience.dataforge.misc.DFExperimental")
    }
}