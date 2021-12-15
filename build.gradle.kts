plugins {
    id("ru.mipt.npm.gradle.project")
}

val dataforgeVersion: String by extra("0.5.2")
val ktorVersion: String by extra(ru.mipt.npm.gradle.KScienceVersions.ktorVersion)
val rsocketVersion by extra("0.13.1")

allprojects {
    group = "ru.mipt.npm"
    version = "0.1.1"
}

ksciencePublish {
    github("controls.kt")
    space()
}

apiValidation {
    validationDisabled = true
}