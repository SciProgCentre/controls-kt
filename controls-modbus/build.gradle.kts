import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    A plugin for Controls-kt device server on top of modbus-rtu/modbus-tcp protocols
""".trimIndent()


dependencies {
    api(projects.controlsCore)
    api("com.ghgande:j2mod:3.1.1")
}

readme{
    maturity = Maturity.EXPERIMENTAL

    feature("modbusRegistryMap", ref = "src/main/kotlin/space/kscience/controls/modbus/ModbusRegistryMap.kt"){
        """
            Type-safe modbus registry map. Allows to define both single-register and multi-register entries (using DataForge IO). 
            Automatically checks consistency.
        """.trimIndent()
    }

    feature("modbusProcessImage", ref = "src/main/kotlin/space/kscience/controls/modbus/DeviceProcessImage.kt"){
        """
            Binding of slave (server) modbus device to Controls-kt device
        """.trimIndent()
    }

    feature("modbusDevice", ref = "src/main/kotlin/space/kscience/controls/modbus/ModbusDevice.kt"){
        """
            A device with additional methods to work with modbus registers.
        """.trimIndent()
    }
}