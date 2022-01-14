package ru.mipt.npm.controls.storage.synchronous

import kotlinx.io.core.Closeable
import ru.mipt.npm.controls.api.PropertyChangedMessage
import kotlin.reflect.KClass

public interface SynchronousStorageClient : Closeable {
    public fun <T : Any> storeValue(value: T, storageKind: StorageKind, clazz: KClass<T>)

    public fun getPropertyHistory(sourceDeviceName: String, propertyName: String): List<PropertyChangedMessage>
}

public inline fun <reified T : Any> SynchronousStorageClient.storeValue(value: T, storageKind: StorageKind): Unit =
    storeValue(value, storageKind, T::class)
