package space.kscience.magix.storage.xodus

import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import java.nio.file.Files
import kotlin.time.measureTime

public suspend fun main() {
    val storeDirectory = Files.createTempDirectory("controls-xodus").toFile()
    println(storeDirectory)
    val store = PersistentEntityStores.newInstance(storeDirectory)
    val history = XodusMagixHistory(store)

    store.executeInTransaction { transaction ->
        for (value in 1..100) {
            for (source in 1..100) {
                for (target in 1..100) {
                    history.writeMessage(
                        transaction,
                        MagixMessage(
                            "test",
                            sourceEndpoint = "source$source",
                            targetEndpoint = "target$target",
                            payload = buildJsonObject {
                                put("value", JsonPrimitive(value))
                            }
                        )
                    )
                }
            }
        }
    }

    println("written million messages")


    val time = measureTime {
        history.useMessages(
            MagixMessageFilter(source = listOf("source12"), target = listOf("target12"))
        ) { sequence ->
            println(sequence.count())
        }
    }
    println("Finished query in $time")

    store.close()
}