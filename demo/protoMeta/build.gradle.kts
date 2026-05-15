
plugins {
    id("space.kscience.gradle.jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    explicitApi = null
    jvmToolchain(21)
}

dependencies {
    implementation(projects.controlsCore)
    implementation(projects.controlsProto)
    implementation(projects.controlsPortsKtor)
    implementation(libs.dataforge.io.proto)
    implementation(libs.logback.classic)
    implementation("space.kscience:dataforge-io-proto:0.10.3")
}

application {
    mainClass.set("space.kscience.controls.demo.proto.ProtoDemoKt")
}

tasks.register<JavaExec>("generateProtocol") {
    group = "code generation"
    description = "Generate protocol source files using the project-local ProtocolCodegen configuration."
    mainClass.set("space.kscience.controls.demo.proto.ProtocolCodegenKt")
    classpath = sourceSets["main"].runtimeClasspath
}
