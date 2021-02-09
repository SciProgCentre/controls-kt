plugins {
    id("ru.mipt.npm.project")
    kotlin("jvm") apply false
    kotlin("js") apply false
}

val dataforgeVersion: String by extra("0.3.0")
val ktorVersion: String by extra(ru.mipt.npm.gradle.KScienceVersions.ktorVersion)
val rsocketVersion by extra("0.12.0")

allprojects {
    repositories {
        mavenLocal()
        //maven("http://maven.jzy3d.org/releases")
        maven("https://kotlin.bintray.com/js-externals")
        maven("https://dl.bintray.com/rsocket-admin/RSocket")
        //maven("https://maven.pkg.github.com/altavir/ktor-client-sse")
    }

    group = "ru.mipt.npm"
    version = "0.1.0"
}

ksciencePublish {
    githubProject = "controls.kt"
    bintrayRepo = "kscience"
}

apiValidation {
    validationDisabled = true
}