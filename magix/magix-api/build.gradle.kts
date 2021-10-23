plugins {
    id("ru.mipt.npm.gradle.mpp")
    `maven-publish`
}

kscience {
    useCoroutines()
    useSerialization{
        json()
    }
}

