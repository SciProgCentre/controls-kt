plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    Magix history database API
""".trimIndent()


kscience {
    jvm()
    js()
    native()
    useSerialization {
        json()
    }
    dependencies {
        api(projects.magix.magixApi)
        api(spclibs.kotlinx.datetime)
    }
}
