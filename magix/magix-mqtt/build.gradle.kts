plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
   MQTT client magix endpoint
""".trimIndent()

dependencies {
    api(projects.magix.magixApi)
    implementation("com.hivemq:hivemq-mqtt-client:1.3.1")
    implementation(spclibs.kotlinx.coroutines.jdk8)
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
