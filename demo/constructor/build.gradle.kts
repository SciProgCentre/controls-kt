plugins {
    id("space.kscience.gradle.mpp")
    application
}

kscience {
    fullStack("js/constructor.js")
    useKtor()
    dependencies {
        api(projects.controlsVision)
    }
    jvmMain {
        implementation("io.ktor:ktor-server-cio")
        implementation(spclibs.logback.classic)
    }
}

application {
    mainClass.set("space.kscience.controls.demo.constructor.MainKt")
}