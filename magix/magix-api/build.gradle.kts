plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

kscience {
    jvm()
    js()
    native()
    useCoroutines()
    useSerialization{
        json()
    }
}

