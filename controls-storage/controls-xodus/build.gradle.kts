plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

description = """
    An implementation of controls-storage on top of JetBrains Xodus.
""".trimIndent()

kscience {
    jvm()
    jvmMain {
        api(projects.controlsStorage)
        implementation(libs.xodus.entity.store)
//    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
//    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")

    }
    jvmTest{
        implementation(spclibs.kotlinx.coroutines.test)
    }
}

readme{
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
