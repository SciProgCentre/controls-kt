val dataforgeVersion by extra("0.1.9-dev")


allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/pdvrieze/maven")
        maven("http://maven.jzy3d.org/releases")
        maven("https://kotlin.bintray.com/js-externals")
    }

    group = "hep.dataforge"
    version = "0.0.1"
}

val githubProject by extra("dataforge-control")
val bintrayRepo by extra("dataforge")