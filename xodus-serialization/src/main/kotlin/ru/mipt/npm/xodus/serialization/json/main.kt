package ru.mipt.npm.xodus.serialization.json

import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.magix.api.MagixMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import java.nio.file.Paths

internal fun main() {
    val expectedMessage = MagixMessage(
        "dataforge",
        "dataforge",
        PropertyChangedMessage(
            "acceleration",
            Meta {
                "x" put 3.0
                "y" put 9.0
            },
            Name.parse("virtual-car"),
            Name.parse("magix-virtual-car"),
            time = Instant.fromEpochMilliseconds(1337)
        ),
        "magix-virtual-car",
        user = JsonObject(content = mapOf(Pair("name", JsonPrimitive("SCADA"))))
    )

    val entityStore = PersistentEntityStores.newInstance(Paths.get("xodus_serialization").toString())
    entityStore.executeInTransaction { txn ->
        txn.encodeToEntity(expectedMessage, "MagixMessage")
    }

    entityStore.executeInTransaction { txn ->
        txn.getAll("MagixMessage").first?.let { println(txn.decodeFromEntity<MagixMessage<PropertyChangedMessage>>(it) == expectedMessage) }
    }
}