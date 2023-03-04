plugins {
    id("space.kscience.gradle.jvm")
    `maven-publish`
}

val kmongoVersion = "4.5.1"

dependencies {
    implementation(projects.controlsStorage)
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:$kmongoVersion")
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
