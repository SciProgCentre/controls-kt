package ru.mipt.npm.controls.storage

import io.ktor.utils.io.core.Closeable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import ru.mipt.npm.controls.api.PropertyChangedMessage

public interface EventStorage : Closeable {
    public suspend fun <T : Any> storeValueInDeviceHub(value: T, serializer: KSerializer<T>)

    public suspend fun <T : Any> storeValueInMagixServer(value: T, serializer: KSerializer<T>)

    public suspend fun getPropertyHistory(sourceDeviceName: String, propertyName: String): List<PropertyChangedMessage>
}

public suspend inline fun <reified T : Any> EventStorage.storeValueInDeviceHub(value: T): Unit =
    storeValueInDeviceHub(value, serializer())

public suspend inline fun <reified T : Any> EventStorage.storeValueInMagixServer(value: T): Unit =
    storeValueInMagixServer(value, serializer())
