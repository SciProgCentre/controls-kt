plugins{
    kotlin("jvm") version "1.4.0" apply false
    kotlin("js") version "1.4.0" apply false
}

val dataforgeVersion by extra("0.1.9-dev-2")

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

val githubProject by extra("dataforge-control")
val bintrayRepo by extra("dataforge")