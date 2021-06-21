plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}


dependencies {
    api(projects.magix.magixApi)
    implementation("org.zeromq:jeromq:0.5.2")
}
