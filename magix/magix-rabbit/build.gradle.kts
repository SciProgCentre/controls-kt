plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

description = """
   RabbitMQ client magix endpoint
""".trimIndent()

dependencies {
    api(projects.magix.magixApi)
    implementation(libs.rabbitmq.amqp.client)
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
