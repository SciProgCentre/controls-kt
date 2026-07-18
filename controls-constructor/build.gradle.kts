plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    A low-code constructor for composite devices simulation
""".trimIndent()

kscience {
    jvm()
    js()
    native()
//    wasm()
    useCoroutines()
    useSerialization()

    commonMain {
        api(projects.controlsCore)
        api(projects.simulationKt)
    }

    commonTest {
        implementation(spclibs.logback.classic)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.EXPERIMENTAL

    feature("constructor", ref = "src/commonMain/kotlin/space/kscience/controls/constructor/Constructor.kt") {
        """
            A low-code DSL for composing complex devices and simulations from basic components and models.
        """.trimIndent()
    }

    feature("valueState", ref = "src/commonMain/kotlin/space/kscience/controls/constructor/ValueState.kt") {
        """
            Reactive state containers used to represent device properties, internal variables, and simulation parameters.
        """.trimIndent()
    }

    feature("models", ref = "src/commonMain/kotlin/space/kscience/controls/constructor/models") {
        """
            A library of physical and logical models, including PID regulators, inertia, and mechanical components.
        """.trimIndent()
    }

    feature("flowModels", ref = "src/commonMain/kotlin/space/kscience/controls/constructor/models/continuous") {
        """
            Simulation models for continuous and discrete flows of material, energy, or information.
        """.trimIndent()
    }

    feature("simulatedDevices", ref = "src/commonMain/kotlin/space/kscience/controls/constructor/devices") {
        """
            Pre-defined simulated devices like drives, encoders, and limit switches ready to be used in constructions.
        """.trimIndent()
    }

    feature("expressions", ref = "src/commonMain/kotlin/space/kscience/controls/constructor/expressions") {
        """
            A type-safe DSL for creating reactive expressions and bindings between different states.
        """.trimIndent()
    }

    feature("units", ref = "src/commonMain/kotlin/space/kscience/controls/constructor/units") {
        """
            Support for physical quantities and units of measurement in simulations and device properties.
        """.trimIndent()
    }
}