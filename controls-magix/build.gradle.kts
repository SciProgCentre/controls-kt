plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Magix service for binding controls devices (both as RPC client and server)
""".trimIndent()

kscience {
    jvm()
    js()
    useSerialization {
        json()
    }
    dependencies {
        api(projects.magix.magixApi)
        api(projects.controlsCore)
        api("com.benasher44:uuid:0.8.0")
    }
}

readme {

}