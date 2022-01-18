plugins {
    id("ru.mipt.npm.gradle.jvm")
    `maven-publish`
}

val kmongoVersion = "4.4.0"

dependencies {
    implementation(projects.controlsStorage)
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:$kmongoVersion")
}

readme{
    maturity = ru.mipt.npm.gradle.Maturity.PROTOTYPE
}
