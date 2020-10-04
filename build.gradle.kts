plugins {
    id("ru.mipt.npm.project")
    kotlin("jvm") apply false
    kotlin("js") apply false
}

val dataforgeVersion: String by extra("0.2.0-dev-3")
val ktorVersion: String by extra("1.4.1")

allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/pdvrieze/maven")
        maven("http://maven.jzy3d.org/releases")
        maven("https://kotlin.bintray.com/js-externals")
        maven("https://maven.pkg.github.com/altavir/kotlin-logging/")
    }

    group = "hep.dataforge"
    version = "0.0.1"
}

ksciencePublish {
    githubProject = "dataforge-control"
    bintrayRepo = "dataforge"
}

apiValidation {
    validationDisabled = true
}