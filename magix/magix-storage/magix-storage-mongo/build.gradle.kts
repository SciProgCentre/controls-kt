plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

dependencies {
    implementation(projects.controlsStorage)
    implementation(libs.kmongo.coroutine.serialization)
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
