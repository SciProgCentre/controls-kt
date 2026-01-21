import space.kscience.gradle.Maturity

plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = "Protobuf support for Controls-kt"

kscience {
    jvm()
    commonMain {
        api(projects.controlsCore)
        api(libs.dataforge.io.proto)
    }
}

readme{
    maturity = Maturity.PROTOTYPE
}
