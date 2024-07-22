import space.kscience.gradle.useApache2Licence
import space.kscience.gradle.useSPCTeam

plugins {
    id("space.kscience.gradle.project")
}

allprojects {
    group = "space.kscience"
    version = "0.4.0-dev-5"
    repositories{
        google()
    }
}

ksciencePublish {
    pom("https://github.com/SciProgCentre/controls-kt") {
        useApache2Licence()
        useSPCTeam()
    }
    repository("spc","https://maven.sciprog.center/kscience")
    sonatype("https://oss.sonatype.org")
}

readme.readmeTemplate = file("docs/templates/README-TEMPLATE.md")