plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

kscience {
    jvm()
    useCoroutines()
    jvmMain {
        api(projects.magix.magixStorage)
        implementation(libs.xodus.entity.store)
//    implementation("org.jetbrains.xodus:dnq:2.0.0")

    }

    jvmTest{
        implementation(spclibs.kotlinx.coroutines.test)
    }
}


readme {
    maturity = space.kscience.gradle.Maturity.PROTOTYPE
}
