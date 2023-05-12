plugins {
    id("space.kscience.gradle.jvm")
    application
}


dependencies{
    implementation(projects.magix.magixServer)
    implementation(projects.magix.magixZmq)
    implementation(projects.magix.magixRsocket)
    implementation(spclibs.logback.classic)
}

kotlin{
    explicitApi = null
}

application{
    mainClass.set("ZmqKt")
}