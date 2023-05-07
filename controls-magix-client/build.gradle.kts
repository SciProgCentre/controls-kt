plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Magix service for binding controls devices (both as RPC client and server
""".trimIndent()

kscience {
    jvm()
    js()
    useSerialization {
        json()
    }
    dependencies {
        implementation(projects.magix.magixApi)
        implementation(projects.controlsCore)
        implementation("com.benasher44:uuid:0.7.0")
    }
}

readme {

}