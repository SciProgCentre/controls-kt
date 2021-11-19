package ru.mipt.npm.controls.xodus.util

import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.datetime.Instant
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.xodus.toPropertyChangedMessage

public fun StoreTransaction.fromTo(from: Instant, to: Instant): List<PropertyChangedMessage> {
    return find(
        "PropertyChangedMessage",
        "time",
        from.toEpochMilliseconds(),
        to.toEpochMilliseconds()
    ).map { it -> it.toPropertyChangedMessage() }.filterNotNull()
}
