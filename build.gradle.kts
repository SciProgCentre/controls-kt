plugins {
    id("ru.mipt.npm.gradle.project")
}

val dataforgeVersion: String by extra("0.6.0-dev-12")
val ktorVersion: String by extra(ru.mipt.npm.gradle.KScienceVersions.ktorVersion)
val rsocketVersion by extra("0.15.4")

allprojects {
    group = "space.kscience"
    version = "0.1.1-SNAPSHOT"
}

ksciencePublish {
    github("controls.kt")
    space("https://maven.pkg.jetbrains.space/mipt-npm/p/controls/maven")
}

apiValidation {
    validationDisabled = true
}