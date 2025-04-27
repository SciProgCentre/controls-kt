plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
   MQTT client magix endpoint
""".trimIndent()

kscience {
    jvm()
    jvmMain {
        api(projects.magix.magixApi)
        implementation(libs.hivemq.mqtt.client)
        implementation(spclibs.kotlinx.coroutines.jdk8)
    }
    jvmTest {
        implementation(spclibs.kotlinx.coroutines.test)
        implementation(libs.testcontainers)
        implementation(libs.testcontainers.junit)
        implementation(libs.testcontainers.hivemq)
        implementation(spclibs.logback.classic)
    }
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
