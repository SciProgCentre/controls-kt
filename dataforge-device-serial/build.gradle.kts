plugins {
    id("kscience.jvm")
    id("kscience.publish")
}

dependencies{
    api(project(":dataforge-device-core"))
    implementation("org.scream3r:jssc:2.8.0")
}