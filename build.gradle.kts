import space.kscience.gradle.isInDevelopment
import space.kscience.gradle.useApache2Licence
import space.kscience.gradle.useSPCTeam

plugins {
    id("space.kscience.gradle.project")
}

val dataforgeVersion: String by extra("0.6.1-dev-4")
val ktorVersion: String by extra(space.kscience.gradle.KScienceVersions.ktorVersion)
val rsocketVersion by extra("0.15.4")
val xodusVersion by extra("2.0.1")

allprojects {
    group = "space.kscience"
    version = "0.1.1-SNAPSHOT"
    repositories{
        mavenCentral()
        mavenLocal()
    }
}

ksciencePublish {
    pom("https://github.com/SciProgCentre/controls.kt") {
        useApache2Licence()
        useSPCTeam()
    }
    github("controls.kt", "SciProgCentre")
    space(
        if (isInDevelopment) {
            "https://maven.pkg.jetbrains.space/spc/p/sci/dev"
        } else {
            "https://maven.pkg.jetbrains.space/spc/p/sci/release"
        }
    )
    space("https://maven.pkg.jetbrains.space/spc/p/controls/maven")
}

apiValidation {
    validationDisabled = true
}