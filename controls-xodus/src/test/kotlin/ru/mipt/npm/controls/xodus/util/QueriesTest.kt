package ru.mipt.npm.controls.xodus.util

import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.xodus.toEntity
import space.kscience.dataforge.meta.Meta
import java.io.File

internal class QueriesTest {
    companion object {
        private val storeName = ".queries_test"
        private val entityStore = PersistentEntityStores.newInstance(storeName)

        private val propertyChangedMessages = listOf(
            PropertyChangedMessage(
                "",
                Meta.EMPTY,
                time = Instant.fromEpochMilliseconds(1000)
            ),
            PropertyChangedMessage(
                "",
                Meta.EMPTY,
                time = Instant.fromEpochMilliseconds(1500)
            ),
            PropertyChangedMessage(
                "",
                Meta.EMPTY,
                time = Instant.fromEpochMilliseconds(2000)
            )
        )

        @BeforeAll
        @JvmStatic
        fun createEntities() {
            entityStore.executeInTransaction { transaction ->
                propertyChangedMessages.forEach {
                    it.toEntity(transaction)
                }
            }
        }

        @AfterAll
        @JvmStatic
        fun deleteDatabase() {
            entityStore.close()
            File(storeName).deleteRecursively()
        }
    }

    @Test
    fun testFromTo() {
        assertEquals(propertyChangedMessages.subList(0, 2).toSet(), entityStore.computeInReadonlyTransaction {
            it.selectPropertyChangedMessagesFromRange( Instant.fromEpochMilliseconds(1000)..Instant.fromEpochMilliseconds(1500))
        }.toSet())
    }

}