import space.kscience.gradle.useApache2Licence
import space.kscience.gradle.useSPCTeam

plugins {
    id("space.kscience.gradle.project")
    alias(libs.plugins.versions)
}

allprojects {
    group = "space.kscience"
    version = "0.4.0-dev-9"
    repositories{
        google()
    }
    tasks.withType<AbstractTestTask>().configureEach {
        failOnNoDiscoveredTests = false
    }
}

ksciencePublish {
    pom("https://github.com/SciProgCentre/controls-kt") {
        useApache2Licence()
        useSPCTeam()
    }
    repository("spc","https://maven.sciprog.center/kscience")
    central()
}

readme.readmeTemplate = file("docs/templates/README-TEMPLATE.md")