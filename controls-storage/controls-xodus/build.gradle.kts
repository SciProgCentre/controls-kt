plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

val xodusVersion = "2.0.1"

dependencies {
    api(projects.controlsStorage)
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
//    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
//    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")

    testImplementation(npmlibs.kotlinx.coroutines.test)
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
