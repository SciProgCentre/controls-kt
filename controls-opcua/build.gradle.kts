plugins {
    id("space.kscience.gradle.jvm")
}

val ktorVersion: String by rootProject.extra

val miloVersion: String = "0.6.9"

dependencies {
    api(projects.controlsCore)
    api(spclibs.kotlinx.coroutines.jdk8)

    api("org.eclipse.milo:sdk-client:$miloVersion")
    api("org.eclipse.milo:bsd-parser:$miloVersion")
    api("org.eclipse.milo:sdk-server:$miloVersion")

    testImplementation(spclibs.kotlinx.coroutines.test)
}
