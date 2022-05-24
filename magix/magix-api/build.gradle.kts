plugins {
    id("ru.mipt.npm.gradle.mpp")
    id("ru.mipt.npm.gradle.native")
    `maven-publish`
}

kscience {
    useCoroutines()
    useSerialization{
        json()
    }
}

