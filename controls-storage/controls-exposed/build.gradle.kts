plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    An implementation of controls-storage on top of JetBrains Exposed JDBC.
""".trimIndent()

kscience {
    jvm()
    jvmMain {
        api(projects.controlsStorage)
        implementation(libs.exposed.core)
        implementation(libs.exposed.dao)
        implementation(libs.exposed.jdbc)
        implementation(libs.exposed.kotlin.datetime)
    }
    jvmTest {
        implementation(spclibs.logback.classic)
        implementation(spclibs.kotlinx.coroutines.test)
        implementation(libs.h2)
        implementation(libs.postgresql)
        implementation(libs.testcontainers.postgresql)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
