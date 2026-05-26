import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.atomicfu)

    `maven-publish`
}

description = """
    Core interfaces for building a device server
""".trimIndent()

kscience {
    jvm()
    js()
    native()
    wasmJs()
    useCoroutines()
    useSerialization {
        json()
    }
    useContextParameters()

    commonMain {
        api(libs.dataforge.io)
        //temporary fix for lets-plot
        api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0-0.6.x-compat")
        //api(spclibs.kotlinx.datetime)
    }

    jvmTest {
        implementation(spclibs.logback.classic)
    }
}


readme {
    maturity = Maturity.EXPERIMENTAL

    feature("device", ref = "src/commonMain/kotlin/space/kscience/controls/api/Device.kt") {
        """
            Device API with subscription (asynchronous and pseudo-synchronous properties)
        """.trimIndent()
    }

    feature("deviceMessage", ref = "src/commonMain/kotlin/space/kscience/controls/api/DeviceMessage.kt") {
        """
            Specification for messages used to communicate between Controls-kt devices.
        """.trimIndent()
    }

    feature("deviceHub", ref = "src/commonMain/kotlin/space/kscience/controls/api/DeviceHub.kt") {
        """
            Grouping of devices into local tree-like hubs.
        """.trimIndent()
    }

    feature("deviceSpec", ref = "src/commonMain/kotlin/space/kscience/controls/spec") {
        """
            Mechanics and type-safe builders for devices. Including separation of device specification and device state.
        """.trimIndent()
    }

    feature("deviceManager", ref = "src/commonMain/kotlin/space/kscience/controls/manager") {
        """
            DataForge DI integration for devices. Includes device builders.
        """.trimIndent()
    }

    feature("ports", ref = "src/commonMain/kotlin/space/kscience/controls/ports") {
        """
            Working with asynchronous data sending and receiving raw byte arrays
        """.trimIndent()
    }

    feature("time", ref = "docs/time-management.md") {
        """
            Time management utilities: ClockManager, VirtualTimeDispatcher, and ValueWithTime.
        """.trimIndent()
    }

    feature("frames", ref = "docs/binary-frames.md") {
        """
            Binary frames utilities.
        """.trimIndent()
    }
}