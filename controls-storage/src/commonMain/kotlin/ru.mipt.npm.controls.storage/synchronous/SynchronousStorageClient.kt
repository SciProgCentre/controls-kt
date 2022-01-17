package ru.mipt.npm.controls.storage.synchronous

import kotlinx.io.core.Closeable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import ru.mipt.npm.controls.api.PropertyChangedMessage

public interface SynchronousStorageClient : Closeable {
    public fun <T : Any> storeValueInDeviceHub(value: T, serializer: KSerializer<T>)

    public fun <T : Any> storeValueInMagixServer(value: T, serializer: KSerializer<T>)

    public fun getPropertyHistory(sourceDeviceName: String, propertyName: String): List<PropertyChangedMessage>
}

public inline fun <reified T : Any> SynchronousStorageClient.storeValueInDeviceHub(value: T): Unit =
    storeValueInDeviceHub(value, serializer())

public inline fun <reified T : Any> SynchronousStorageClient.storeValueInMagixServer(value: T): Unit =
    storeValueInMagixServer(value, serializer())
