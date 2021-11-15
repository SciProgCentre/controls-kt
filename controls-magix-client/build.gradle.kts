plugins {
    id("ru.mipt.npm.gradle.mpp")
    `maven-publish`
}

kscience{
    useSerialization {
        json()
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":magix:magix-rsocket"))
                implementation(project(":controls-core"))
            }
        }
        jvmMain {
            dependencies {
                implementation("org.jetbrains.xodus:xodus-openAPI:1.3.232")
                implementation(project(":controls-xodus"))
            }
        }
    }
}
