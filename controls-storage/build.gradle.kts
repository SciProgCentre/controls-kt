plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra

kscience{
    jvm()
    js()
    dependencies {
        api(projects.controlsCore)
    }
    dependencies(jvmMain){
        api(projects.magix.magixApi)
        api(projects.controlsMagix)
        api(projects.magix.magixServer)
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
