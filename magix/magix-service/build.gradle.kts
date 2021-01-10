plugins {
    id("ru.mipt.npm.mpp")
    id("ru.mipt.npm.publish")
}

kscience {
    useSerialization{
        json()
    }
}

val dataforgeVersion: String by rootProject.extra
val ktorVersion: String by rootProject.extra
val rsocketVersion: String by rootProject.extra

repositories{
    maven("https://maven.pkg.github.com/altavir/ktor-client-sse")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":magix:magix-api"))
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:$rsocketVersion")
            }
        }
    }
}