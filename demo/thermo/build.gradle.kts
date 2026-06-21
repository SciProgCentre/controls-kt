plugins {
    id("space.kscience.gradle.mpp")
    alias(spclibs.plugins.compose.compiler)
    alias(spclibs.plugins.compose.jb)
}

kotlin {
    explicitApi = null
}

kscience {
    fullStack(
        bundleName = "js/thermo-vision.js",
        jvmConfig = {
            binaries {
                executable {
                    mainClass = "center.sciprog.controls.demo.thermo.VisionPanelKt"
                }
            }
        },
        browserConfig = {
            commonWebpackConfig {
                cssSupport { enabled.set(true) }
                scssSupport { enabled.set(true) }
            }
        },
        jsConfig = {
            //otherwise compose-bootstrap does not work
            useCommonJs()
        }
//        development = true
    )

    useContextParameters()

    useSerialization {
        json()
    }

    commonMain {
        implementation(
            project.dependencies.platform(spclibs.kotlin.js.wrappers)
        )
        implementation(projects.controlsCore)
        implementation(projects.controlsConstructor)
        implementation(projects.controlsVision)
        implementation(compose.runtime)

        api(project.dependencies.platform(spclibs.ktor.bom))

        implementation(libs.plotlykt.core)
    }


    jvmMain {

        implementation(projects.controlsModbus)
        implementation(projects.controlsOpcua)

        implementation(libs.visionforge.server)
        implementation("org.jetbrains.kotlin-wrappers:kotlin-css")

        implementation(spclibs.logback.classic)
    }

    jsMain {
        implementation(libs.visionforge.compose.html)
    }
}

compose {
    desktop {
        application {
            from(kotlin.targets.getByName("jvm"))
            mainClass = "center.sciprog.controls.demo.thermo.VisionPanelKt"
        }
    }
}