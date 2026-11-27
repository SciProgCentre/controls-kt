package space.kscience.controls.demo

import kotlinx.coroutines.coroutineScope
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.tagtable.TagTablePlugin
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.SlfLogManager
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import kotlin.io.path.Path
import kotlin.time.Instant
import kotlin.time.measureTime

suspend fun main(): Unit = coroutineScope {

    val context = Context {
        plugin(IOPlugin)
        plugin(ControlsStoragePlugin)
        plugin(TagTablePlugin)
        plugin(SlfLogManager)
    }

    val dataDirectory = Path("data")

    val storageIndex = TableStorageIndex(context.request(ControlsStoragePlugin), dataDirectory)

    storageIndex.start()

    measureTime {
        val rows = storageIndex.selectRows(Instant.DISTANT_PAST..Instant.DISTANT_FUTURE).rowSequence().count()
        println("Read rows: $rows")
    }.also {
        println("Read time: $it")
    }

    context.close()
}