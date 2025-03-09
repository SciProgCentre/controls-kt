package space.kscience.magix.services

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.send
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class WatcherEndpointWrapper(
    private val scope: CoroutineScope,
    private val endpointName: String,
    private val endpoint: MagixEndpoint,
    private val meta: Meta,
) : MagixEndpoint {

    private val watchDogJob: Job = scope.launch {
        val filter = MagixMessageFilter(
            format = listOf(MAGIX_WATCHDOG_FORMAT),
            target = listOf(null, endpointName)
        )
        endpoint.subscribe(filter).filter {
            it.payload.jsonPrimitive.content == MAGIX_PING
        }.onEach { request ->
            endpoint.send(
                MagixMessage(
                    MAGIX_WATCHDOG_FORMAT,
                    JsonPrimitive(MAGIX_PONG),
                    sourceEndpoint = endpointName,
                    targetEndpoint = request.sourceEndpoint,
                    parentId = request.id
                )
            )
        }.collect()
    }

    private val heartBeatDelay: Duration = meta["heartbeat.period"].string?.let { Duration.parse(it) } ?: 10.seconds
    //TODO add update from registry

    private val heartBeatJob = scope.launch {
        while (isActive){
            delay(heartBeatDelay)
            endpoint.send(
                MagixMessage(
                    MAGIX_HEARTBEAT_FORMAT,
                    JsonNull, //TODO consider adding timestamp
                    endpointName
                )
            )
        }
    }

    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> = endpoint.subscribe(filter)

    override suspend fun broadcast(message: MagixMessage) {
        endpoint.broadcast(message)
    }

    override fun close() {
        endpoint.close()
        watchDogJob.cancel()
        heartBeatJob.cancel()
    }

    public companion object {
        public const val MAGIX_WATCHDOG_FORMAT: String = "magix.watchdog"
        public const val MAGIX_PING: String = "ping"
        public const val MAGIX_PONG: String = "pong"
        public const val MAGIX_HEARTBEAT_FORMAT: String = "magix.heartbeat"
    }
}