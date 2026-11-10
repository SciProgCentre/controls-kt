package space.kscience.controls.tagtable

import kotlinx.serialization.json.Json
import org.apache.plc4x.java.api.types.PlcValueType
import space.kscience.controls.tagtable.storage.DataPlatformFileSplit
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/*
 * LLM generated code: Unit tests for TagTableConfiguration JSON serialization and deserialization.
 */
class TagTableConfigurationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testJsonRoundtrip() {
        val configuration = TagTableConfiguration(
            sources = mapOf(
                "opcServer" to OpcTagSourceConfig(
                    host = "opc.tcp://localhost:4840",
                    maxAge = 500.milliseconds,
                    reconnectionDelay = 5.seconds
                ),
                "modbusTcp" to ModbusTcpTagSourceConfig(
                    addr = "192.168.1.100",
                    port = 502,
                    timeout = 3000,
                    reconnect = true,
                    reconnectionDelay = 5.seconds
                ),
                "modbusRtu" to ModbusRtuTagSourceConfig(
                    portName = "COM3",
                    baudRate = 19200,
                    reconnectionDelay = 2.seconds
                ),
                "plc4xConn" to Plc4xTagSourceConfig(
                    address = "s7://192.168.0.1",
                    reconnectionDelay = 5.seconds
                )
            ),
            timers = mapOf(
                "fastTimer" to FixedRateTimer(tick = 100.milliseconds),
                "slowTimer" to FixedRateTimer(tick = 1.seconds)
            ),
            tags = mapOf(
                "opcTemperature" to TagTableColumn.opc(
                    source = "opcServer",
                    timer = "fastTimer",
                    nodeId = "ns=2;s=Temperature",
                    compression = ColumnCompression(skipUnchangedValues = true, numericDelta = 0.1),
                    maxAge = 10.seconds,
                    maxDelta = 5.0
                ),
                "modbusPressure" to TagTableColumn.modbus(
                    source = "modbusTcp",
                    timer = "slowTimer",
                    reader = ModbusDoubleReader,
                    address = 100,
                    unitId = 1
                ),
                "plcSpeed" to TagTableColumn.plc4x(
                    source = "plc4xConn",
                    timer = "fastTimer",
                    address = "%DB1.DBD0",
                    plcValueType = PlcValueType.REAL
                ),
                "controlSensor" to TagTableColumn.controls(
                    timer = "fastTimer",
                    deviceName = Name.parse("generator"),
                    propertyName = "voltage"
                )
            ),
            storage = TagTableStorageConfiguration(
                path = "data/storage",
                readInterval = 100.milliseconds,
                withTagState = true,
                writeOldValues = true,
                maxRowsPerEnvelope = 5000,
                maxDuration = 10.minutes,
                compression = RowsCompression(
                    skipUnchangedRows = true,
                    skipUnchangedValues = false,
                    numericDelta = 0.05
                ),
                splitStrategy = DataPlatformFileSplit.ByDate(timeZoneId = "UTC"),
                separateMeta = true
            ),
            messageStorage = MessageStorageConfiguration(
                path = "data/messages",
                sourceDevicePrefixFilter = "@plcTagTable",
                maxMessagesPerFile = 5000
            )
        )

        val serialized = json.encodeToString(TagTableConfiguration.serializer(), configuration)
        val deserialized = json.decodeFromString(TagTableConfiguration.serializer(), serialized)

        assertEquals(4, deserialized.sources.size)
        assertEquals(2, deserialized.timers.size)
        assertEquals(4, deserialized.tags.size)
        assertNotNull(deserialized.storage)
        assertNotNull(deserialized.messageStorage)
    }

    @Test
    fun testParseManualJson() {
        val jsonText = """
        {
          "sources": {
            "opc1": {
              "type": "opc",
              "host": "opc.tcp://127.0.0.1:4840",
              "maxAge": "PT0.5S",
              "reconnectionDelay": "PT5S"
            },
            "modbus1": {
              "type": "modbus-tcp",
              "addr": "127.0.0.1",
              "port": 502,
              "timeout": 3000,
              "reconnect": false,
              "reconnectionDelay": "PT5S"
            },
            "plc1": {
              "type": "plc4x",
              "address": "s7://192.168.0.1",
              "reconnectionDelay": "PT5S"
            }
          },
          "timers": {
            "scan1": {
              "type": "fixed-rate",
              "tick": "PT0.1S"
            }
          },
          "tags": {
            "temp": {
              "source": "opc1",
              "timer": "scan1",
              "config": {
                "nodeId": "ns=2;s=Temperature"
              },
              "timeout": "PT1S",
              "compression": {
                "skipUnchangedValues": true,
                "numericDelta": 0.1
              },
              "removeAuxiliaryValues": true,
              "maxAge": "PT10S",
              "maxDelta": 5.0,
              "meta": {}
            },
            "ctrl": {
              "source": "@controls",
              "timer": "scan1",
              "config": {
                "deviceName": "dev1",
                "propertyName": "prop1"
              }
            }
          },
          "storage": {
            "path": "data/storage",
            "readInterval": "PT0.1S",
            "withTagState": false,
            "writeOldValues": true,
            "maxRowsPerEnvelope": 10000,
            "maxDuration": "PT3H",
            "compression": {
              "skipUnchangedRows": true,
              "skipUnchangedValues": false,
              "numericDelta": null,
              "columns": {}
            },
            "splitStrategy": {
              "type": "byDate",
              "timeZoneId": "UTC"
            },
            "rowsConverterType": "controls.table.zip",
            "separateMeta": true
          },
          "messageStorage": {
            "path": "data/messages",
            "sourceDevicePrefixFilter": "@plcTagTable",
            "maxMessagesPerFile": 10000
          }
        }
        """.trimIndent()

        val parsed = json.decodeFromString(TagTableConfiguration.serializer(), jsonText)
        assertEquals(3, parsed.sources.size)
        assertEquals(1, parsed.timers.size)
        assertEquals(2, parsed.tags.size)
        assertEquals("opc.tcp://127.0.0.1:4840", (parsed.sources["opc1"] as OpcTagSourceConfig).host)
        assertEquals("scan1", parsed.tags["temp"]?.timer)
        assertEquals("ns=2;s=Temperature", parsed.tags["temp"]?.config?.get("nodeId".parseAsName())?.string)
        assertNotNull(parsed.storage)
        assertEquals("data/storage", parsed.storage?.path)
    }
}
