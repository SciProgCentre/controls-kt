plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    ZMQ client endpoint for Magix
""".trimIndent()

dependencies {
    api(projects.magix.magixApi)
    api("org.slf4j:slf4j-api:2.0.6")
    api("org.zeromq:jeromq:0.5.2")
}
