package ru.mipt.npm.controls.storage.asynchronous

import kotlinx.io.core.Closeable
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.storage.synchronous.StorageKind
import ru.mipt.npm.controls.storage.synchronous.SynchronousStorageClient
import kotlin.reflect.KClass

public interface AsynchronousStorageClient : Closeable {
    public suspend fun <T : Any> storeValue(value: T, storageKind: StorageKind, clazz: KClass<T>)

    public suspend fun getPropertyHistory(sourceDeviceName: String, propertyName: String): List<PropertyChangedMessage>
}

public suspend inline fun <reified T : Any> AsynchronousStorageClient.storeValue(value: T, storageKind: StorageKind): Unit =
    storeValue(value, storageKind, T::class)
