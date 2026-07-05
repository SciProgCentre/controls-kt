import space.kscience.gradle.Maturity

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
    maturity = Maturity.PROTOTYPE
    description = """
        An interpreter for IEC 61131-3 PLC programs. It includes a parser for Instruction List (IL), 
        a compiler for Structured Text (ST), and a runtime for executing IL programs.
    """.trimIndent()

    feature("ILRuntime", ref = "src/commonMain/kotlin/space/kscience/controls/plcemu/IlRuntime.kt") {
        """
            A runtime for the IEC 61131-3 Instruction List (IL) language.
        """.trimIndent()
    }

    feature("STCompiler", ref = "src/commonMain/kotlin/space/kscience/controls/plcemu/StCompiler.kt") {
        """
            A compiler for the IEC 61131-3 Structured Text (ST) language.
        """.trimIndent()
    }

    feature("ILParser", ref = "src/commonMain/kotlin/space/kscience/controls/plcemu/IlParser.kt") {
        """
            A parser for the IEC 61131-3 Instruction List (IL) language based on the Parsus library.
        """.trimIndent()
    }

    feature("PlcState", ref = "src/commonMain/kotlin/space/kscience/controls/plcemu/PlcState.kt") {
        """
            A state interface for the PLC emulator, allowing interaction with registers and external values.
        """.trimIndent()
    }
}