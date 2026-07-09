package space.kscience.controls.demo

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.manager.install
import space.kscience.controls.spec.name
import space.kscience.controls.tagtable.*
import space.kscience.controls.tagtable.storage.ReplayTagTable
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.SlfLogManager
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime

suspend fun main(): Unit = coroutineScope {

    val context = Context {
        plugin(IOPlugin)
        plugin(TagTablePlugin)
        plugin(SlfLogManager)
    }

    val tagTablePlugin = context.request(TagTablePlugin)

    val deviceManager = tagTablePlugin.deviceManager

    val generatorDevice = deviceManager.install("generator", RandomGeneratorDevice)

    val start = Clock.System.now()

    val timers = mapOf("default" to FixedRateTimer(2.milliseconds))

    val platformProperties = buildMap<Name, TagTableColumn> {
        repeat(200) { index ->
            put(
                key = "property[$index]".parseAsName(),
                value = InternalTagTableColumn(
                    timer = "default",
                    deviceName = "generator".asName(),
                    propertyName = RandomGeneratorDevice.random.name
                )
            )
        }
    }

    val dataDirectory = Path("data")
    dataDirectory.createDirectories()

    val configuration = TagTableConfiguration(
        sources = emptyMap(),
        timers = timers,
        properties = platformProperties.mapKeys { it.key.toString() },
        storage = TagTableStorageConfiguration(
            path = dataDirectory.toString(),
            readInterval = 2.milliseconds,
            maxRowsPerEnvelope = 1000,
            compression = RowsCompression(skipUnchangedRows = true, skipUnchangedValues = true, numericDelta = 0.05),
            separateMeta = false
        )
    )

    //create and start table processing
    val tagTable = tagTablePlugin.install(configuration)

    delay(30.seconds)

    tagTable.stop()

    val storageIndex = TableStorageIndex(tagTablePlugin.storage, dataDirectory)

    measureTime {
        val rows = storageIndex.selectRows(start..Instant.DISTANT_FUTURE).rowSequence().count()
        println("Total rows: $rows")
    }.also {
        println("Read time: $it")
    }

    val readerTable = ReplayTagTable(
        storageIndex = storageIndex,
        tags = platformProperties.entries.associate { it.key.toString() to MetaDescriptor { valueType(ValueType.NUMBER) } }
    )

    var counter = 0

    readerTable.readTimeSeries(2.milliseconds).subscribe().onEach {
        counter++
    }.launchIn(this)

    readerTable.play()

    delay(30.seconds)

    println(counter)


    readerTable.stop()
    context.close()
}