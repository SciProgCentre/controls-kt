plugins {
    id("space.kscience.gradle.jvm")
}

description = """
    A plugin for Controls-kt device server on top of modbus-rtu/modbus-tcp protocols
""".trimIndent()


dependencies {
    api(projects.controlsCore)
    api("com.ghgande:j2mod:3.1.1")
}
