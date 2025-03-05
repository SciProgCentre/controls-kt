import space.kscience.gradle.Maturity

plugins {
    java
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
    Java API to work with magix endpoints without Kotlin
""".trimIndent()

dependencies {
    implementation(projects.magix.magixRsocket)
    implementation(spclibs.kotlinx.coroutines.jdk9)
}

readme {
    maturity = Maturity.EXPERIMENTAL
}