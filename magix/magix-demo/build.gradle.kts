plugins {
    id("ru.mipt.npm.gradle.jvm")
}


dependencies{
    implementation(projects.magix.magixServer)
    implementation(projects.magix.magixZmq)
    implementation(projects.magix.magixRsocket)
    implementation("ch.qos.logback:logback-classic:1.2.3")
}

kotlin{
    explicitApi = null
}