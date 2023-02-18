plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

kscience{
    jvm()
    js()
    useSerialization {
        json()
    }
    dependencies {
        implementation(project(":magix:magix-rsocket"))
        implementation(project(":controls-core"))
    }
}