plugins {
    id("ru.mipt.npm.gradle.project")
    kotlin("jvm") apply false
    kotlin("js") apply false
}

val dataforgeVersion: String by extra("0.4.0-dev-2")
val ktorVersion: String by extra(ru.mipt.npm.gradle.KScienceVersions.ktorVersion)
val rsocketVersion by extra("0.12.0")

allprojects {
    repositories {
        mavenLocal()
        //maven("http://maven.jzy3d.org/releases")
        maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
        maven("https://kotlin.bintray.com/js-externals")
        maven("https://dl.bintray.com/rsocket-admin/RSocket")
        //maven("https://maven.pkg.github.com/altavir/ktor-client-sse")
    }

    group = "ru.mipt.npm"
    version = "0.1.0"
}

ksciencePublish {
    github("controls.kt")
}

apiValidation {
    validationDisabled = true
}