package ru.mipt.npm.controls.storage

import io.ktor.utils.io.core.Closeable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import ru.mipt.npm.controls.api.PropertyChangedMessage

public interface EventStorage : Closeable {
    public suspend fun <T : Any> storeDeviceMessage(value: T, serializer: KSerializer<T>)

    public suspend fun <T : Any> storeMagixMessage(value: T, serializer: KSerializer<T>)

    public suspend fun getPropertyHistory(sourceDeviceName: String, propertyName: String): List<PropertyChangedMessage>
}

public suspend inline fun <reified T : Any> EventStorage.storeDeviceMessage(value: T): Unit =
    storeDeviceMessage(value, serializer())

public suspend inline fun <reified T : Any> EventStorage.storeMagixMessage(value: T): Unit =
    storeMagixMessage(value, serializer())
