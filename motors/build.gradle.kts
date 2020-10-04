plugins {
    id("ru.mipt.npm.jvm")
    id("ru.mipt.npm.publish")
}

//TODO to be moved to a separate project

kotlin{
    explicitApi = null
}

dependencies {
    implementation(project(":dataforge-device-core"))
}
