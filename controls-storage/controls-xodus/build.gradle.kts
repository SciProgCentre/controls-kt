plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

val xodusVersion: String by rootProject.extra

description = """
    An implementation of controls-storage on top of JetBrains Xodus.
""".trimIndent()

dependencies {
    api(projects.controlsStorage)
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
//    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
//    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")

    testImplementation(spclibs.kotlinx.coroutines.test)
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
