val dataforgeVersion by extra("0.1.5-dev-9")

allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/pdvrieze/maven")
        maven("http://maven.jzy3d.org/releases")
        maven("https://kotlin.bintray.com/js-externals")
    }

    group = "hep.dataforge"
    version = "0.1.0-dev"
}

val githubProject by extra("dataforge-control")
val bintrayRepo by extra("dataforge")