plugins {
    id("space.kscience.gradle.mpp")
    `maven-publish`
}

val dataforgeVersion: String by rootProject.extra

kscience {
    jvm()
    js()
    native()
    useCoroutines()
    useSerialization{
        json()
    }
    dependencies {
        api("space.kscience:dataforge-io:$dataforgeVersion")
        api(spclibs.kotlinx.datetime)
    }
}


readme{
    feature("device", ref = "src/commonMain/kotlin/space/kscience/controls/api/Device.kt"){
        """
            Device API with subscription (asynchronous and pseudo-synchronous properties)
        """.trimIndent()
    }
}

readme{
    feature("deviceMessage", ref = "src/commonMain/kotlin/space/kscience/controls/api/DeviceMessage.kt"){
        """
            Specification for messages used to communicate between Controls-kt devices.
        """.trimIndent()
    }
}

readme{
    feature("deviceHub", ref = "src/commonMain/kotlin/space/kscience/controls/api/DeviceHub.kt"){
        """
            Grouping of devices into local tree-like hubs.
        """.trimIndent()
    }
}