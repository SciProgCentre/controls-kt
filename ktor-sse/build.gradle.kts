plugins {
    id("ru.mipt.npm.mpp")
}

group = "ru.mipt.npm"

val ktorVersion: String by rootProject.extra

kscience{
    useCoroutines()
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api("io.ktor:ktor-io:$ktorVersion")
            }
        }
        jvmTest{
            dependencies{
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("ch.qos.logback:logback-classic:1.2.3")
            }
        }
    }
}