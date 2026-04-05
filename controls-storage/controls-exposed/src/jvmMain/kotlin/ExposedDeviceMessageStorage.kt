package space.kscience.controls.storage.exposed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.dataforge.names.Name
import kotlin.time.Instant

/**
 * A concrete implementation of [DeviceMessageStorage] that uses Exposed as an ORM framework to store
 * and retrieve device messages from a relational database.
 *
 * This implementation enables writing [DeviceMessage]s to the database and provides capabilities to read
 * them based on various filtering criteria. It leverages a flow-based API for efficient and asynchronous
 * data streaming.
 *
 * @param database The Exposed [Database] instance used to interact with the underlying database.
 * @param pageSize The number of records fetched in a single batch during flow-based querying. Defaults to 1000.
 */
public class ExposedDeviceMessageStorage(
    public val database: Database,
    private val pageSize: Int = 1000,
) : DeviceMessageStorage {

    private val stringFormat: StringFormat = Json

    public object DeviceMessages : Table("DeviceMessages") {
        public val time: Column<Instant> = timestamp("time").index()
        public val sourceDevice: Column<String?> = varchar("sourceDevice", 255).nullable()
        public val targetDevice: Column<String?> = varchar("targetDevice", 255).nullable()
        public val type: Column<String> = varchar("type", 255)
        public val content: Column<String> = text("content")
    }

    init {
        transaction(database) {
            SchemaUtils.create(DeviceMessages)
        }
    }

    override suspend fun writeAll(events: Iterable<DeviceMessage>) {
        suspendTransaction(database) {
            DeviceMessages.batchInsert(events) {event->
                this[DeviceMessages.type] = DeviceMessage.serialNameFor(event)
                this[DeviceMessages.time] = event.time
                this[DeviceMessages.sourceDevice] = event.sourceDevice.toString()
                this[DeviceMessages.targetDevice] = event.targetDevice?.toString()
                this[DeviceMessages.content] = stringFormat.encodeToString(DeviceMessage.serializer(), event)
            }

        }
    }

    override suspend fun write(event: DeviceMessage) {
        suspendTransaction(database) {
            DeviceMessages.insert {
                it[DeviceMessages.type] = DeviceMessage.serialNameFor(event)
                it[DeviceMessages.time] = event.time
                it[DeviceMessages.sourceDevice] = event.sourceDevice.toString()
                it[DeviceMessages.targetDevice] = event.targetDevice?.toString()
                it[DeviceMessages.content] = stringFormat.encodeToString(DeviceMessage.serializer(), event)
            }
        }
    }

    private fun ResultRow.readDeviceMessage(): DeviceMessage {
        val content = this[DeviceMessages.content]

        return stringFormat.decodeFromString(DeviceMessage.serializer(), content)
    }

    private fun flowQuery(queryBase: Query) = flow {
        var lastpageBottomTime: Instant? = null

        while (true) {
            val page = suspendTransaction(db = database, readOnly = true) {
                queryBase.copy().orderBy(DeviceMessages.time, SortOrder.DESC).limit(pageSize).apply {
                    lastpageBottomTime?.let {
                        andWhere { DeviceMessages.time less it }
                    }
                }.map { it.readDeviceMessage() }
            }

            page.forEach {
                emit(it)
            }

            if (page.size < pageSize) {
                break
            } else {
                lastpageBottomTime = page.last().time
            }

        }
    }

    override fun readAll(): Flow<DeviceMessage> = flowQuery(DeviceMessages.selectAll())

    override fun read(
        eventType: String,
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?
    ): Flow<DeviceMessage> = flowQuery(
        DeviceMessages.selectAll().apply {
            if (range != null) {
                andWhere { DeviceMessages.time.between(range.start, range.endInclusive) }
            }
            if (sourceDevice != null) {
                andWhere { DeviceMessages.sourceDevice eq sourceDevice.toString() }
            }
            if (targetDevice != null) {
                andWhere { DeviceMessages.targetDevice eq targetDevice.toString() }
            }
        }
    )


    override fun close() {
        // No-op for now.
    }
}