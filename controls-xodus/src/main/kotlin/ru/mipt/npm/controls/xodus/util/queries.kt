package ru.mipt.npm.controls.xodus.util

import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.datetime.Instant
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.xodus.defaultPersistentStoreFactory
import ru.mipt.npm.controls.xodus.toPropertyChangedMessage
import ru.mipt.npm.xodus.serialization.json.decodeFromEntity
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta

public fun StoreTransaction.selectPropertyChangedMessagesFromRange(
    range: ClosedRange<Instant>
): List<PropertyChangedMessage> = find(
    "PropertyChangedMessage",
    "time",
    range.start.toEpochMilliseconds(),
    range.endInclusive.toEpochMilliseconds()
).mapNotNull { it.toPropertyChangedMessage() }

/**
 * @return the list of deviceMessages that describes changes of specified property of specified device sorted by time
 * @param sourceDeviceName a name of device, history of which property we want to get
 * @param propertyName a name of property, history of which we want to get
 * @param factory a factory that produce mongo clients
 */
public fun getPropertyHistory(
    sourceDeviceName: String,
    propertyName: String,
    factory: Factory<PersistentEntityStore> = defaultPersistentStoreFactory,
    meta: Meta = Meta.EMPTY
): List<PropertyChangedMessage> {
    return factory(meta).use { store ->
        store.computeInTransaction { txn ->
            txn.find("DeviceMessage", "type", "property.changed").asSequence()
                .filter { it?.getProperty("sourceDevice")?.let { it == sourceDeviceName } ?: false &&
                        it?.getProperty("property")?.let { it == propertyName } ?: false
                }.sortedByDescending { it?.getProperty("time")?.let { timeStr -> Instant.parse(timeStr as String) } }
                .toList().map { txn.decodeFromEntity(it) }
        }
    }
}
