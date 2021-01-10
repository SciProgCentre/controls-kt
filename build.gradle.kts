plugins {
    id("ru.mipt.npm.project")
    kotlin("jvm") apply false
    kotlin("js") apply false
}

val dataforgeVersion: String by extra("0.2.1-dev-2")
val ktorVersion: String by extra("1.5.0")
val rsocketVersion by extra("0.12.0")

allprojects {
    repositories {
        mavenLocal()
        //maven("https://dl.bintray.com/pdvrieze/maven")
        //maven("http://maven.jzy3d.org/releases")
        maven("https://kotlin.bintray.com/js-externals")
        maven("https://maven.pkg.github.com/altavir/kotlin-logging/")
        //maven("https://dl.bintray.com/rsocket-admin/RSocket")
        //maven("https://maven.pkg.github.com/altavir/ktor-client-sse")
    }

    group = "hep.dataforge"
    version = "0.1.0"
}

ksciencePublish {
    githubProject = "controls.kt"
    bintrayRepo = "dataforge"
}

apiValidation {
    validationDisabled = true
}