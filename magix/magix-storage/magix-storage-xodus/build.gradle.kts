plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

val xodusVersion = "2.0.1"

kscience{
    useCoroutines()
}

dependencies {
    api(projects.magix.magixApi)
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")

    testImplementation(npmlibs.kotlinx.coroutines.test)
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
