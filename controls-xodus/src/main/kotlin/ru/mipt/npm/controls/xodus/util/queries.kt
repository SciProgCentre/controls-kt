package ru.mipt.npm.controls.xodus.util

import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.datetime.Instant
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.xodus.toPropertyChangedMessage

//selectDeviceMessagesInRange
public fun StoreTransaction.fromTo(
    range: ClosedRange<Instant>,
//    from: Instant,
//    to: Instant,
): List<PropertyChangedMessage> = find(
    "PropertyChangedMessage",
    "time",
    range.start.toEpochMilliseconds(),
    range.endInclusive.toEpochMilliseconds()
).mapNotNull { it.toPropertyChangedMessage() }
