package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import space.kscience.controls.instant
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.names.Name
import java.nio.file.Path
import java.util.*
import kotlin.time.Instant


public class DataPlatformStorageIndex(
    public val io: IOPlugin,
    public val dataDirectory: Path,
    private val operations: FileEnvelopeOperations = NativeFileEnvelopeOperations(io),
    private val scope: CoroutineScope = io.context,
) : ContextAware {

    override val context: Context get() = io.context

    private class IndexEntry(
        val name: Name,
        val envelope: Envelope,
        val startTime: Instant,
        var endTime: Instant? = null,
    )

    private val index = TreeMap<Instant, IndexEntry>()

    //TODO add pre-indexed data

    private val indexJob = scope.launch(Dispatchers.IO) {
        operations.iterate(dataDirectory).forEach { (name, envelope) ->
            val time = envelope.meta["startTime"]?.instant ?: envelope.meta["@envelope.time"]?.instant
            if (time == null) {
                logger.warn { "Time is not defined for envelope $name" }
                return@forEach
            }
            val endTime = envelope.meta["endTime"]?.instant

            index[time] = IndexEntry(
                name = name,
                envelope = envelope,
                startTime = time,
                endTime = endTime
            )
        }
    }
}