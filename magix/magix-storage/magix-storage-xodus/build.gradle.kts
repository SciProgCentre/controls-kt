plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

val xodusVersion: String by rootProject.extra

kscience {
    useCoroutines()
}

dependencies {
    api(projects.magix.magixStorage)
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
//    implementation("org.jetbrains.xodus:dnq:2.0.0")

    testImplementation(spclibs.kotlinx.coroutines.test)
}

readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
