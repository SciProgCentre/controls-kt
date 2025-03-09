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
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
