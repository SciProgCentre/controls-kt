package ru.mipt.npm.magix.storage.xodus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessageFilter
import java.nio.file.Path

public class XodusMagixStorage(
    private val scope: CoroutineScope,
    private val path: Path,
    private val endpoint: MagixEndpoint<JsonElement>,
    private val filter: MagixMessageFilter = MagixMessageFilter(),
) : AutoCloseable {

    private val subscriptionJob = endpoint.subscribe(filter).onEach {
        TODO()
    }.launchIn(scope)

    override fun close() {
        subscriptionJob.cancel()
    }
}