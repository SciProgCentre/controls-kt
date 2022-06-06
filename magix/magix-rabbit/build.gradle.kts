plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

description = """
   RabbitMQ client magix endpoint
""".trimIndent()

dependencies {
    api(projects.magix.magixApi)
    implementation("com.rabbitmq:amqp-client:5.14.2")
}

readme{
    maturity = ru.mipt.npm.gradle.Maturity.PROTOTYPE
}
