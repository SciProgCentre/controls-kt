plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    ZMQ client endpoint for Magix
""".trimIndent()

dependencies {
    api(projects.magix.magixApi)
    implementation("org.zeromq:jeromq:0.5.2")
}
