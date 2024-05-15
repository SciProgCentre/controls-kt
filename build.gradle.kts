import space.kscience.gradle.useApache2Licence
import space.kscience.gradle.useSPCTeam

plugins {
    id("space.kscience.gradle.project")
}

allprojects {
    group = "space.kscience"
    version = "0.4.0-dev-1"
    repositories{
        maven("https://maven.pkg.jetbrains.space/spc/p/sci/dev")
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

task("generateKTStemplates") {
    doLast {
        exec {
            commandLine("kotlin", "ktstemplate_generator.kts")
        }
    }
}