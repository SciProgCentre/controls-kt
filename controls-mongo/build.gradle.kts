plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

val kmongoVersion = "4.4.0"

dependencies {
    implementation(projects.controlsCore)
    implementation(projects.magix.magixApi)
    implementation(projects.controlsMagixClient)
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:$kmongoVersion")
}
