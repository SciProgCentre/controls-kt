plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
   RabbitMQ client magix endpoint
""".trimIndent()

kscience{
    jvm()
    jvmMain{
        api(projects.magix.magixApi)
        implementation(libs.rabbitmq.amqp.client)
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
