plugins {
    id("space.kscience.gradle.mpp")
    id("space.kscience.gradle.native")
    `maven-publish`
}

kscience {
    useCoroutines()
    useSerialization{
        json()
    }
}

