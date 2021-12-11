plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

val xodusVersion = "1.3.232"

//TODO to be moved to DataForge

kscience {
    useSerialization {
        json()
    }
}

dependencies {
    implementation(projects.magix.magixApi)
    implementation(projects.controlsCore)
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")
}
