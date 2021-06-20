plugins {
    id("ru.mipt.npm.gradle.project")
}

val dataforgeVersion: String by extra("0.4.3")
val ktorVersion: String by extra(ru.mipt.npm.gradle.KScienceVersions.ktorVersion)
val rsocketVersion by extra("0.12.0")

allprojects {
    group = "ru.mipt.npm"
    version = "0.1.0"
    repositories{
        jcenter()
    }
}

ksciencePublish {
    github("controls.kt")
    space()
}

apiValidation {
    validationDisabled = true
}