package hep.dataforge.magix.api

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

public interface MagixProcessor {
    public fun process(endpoint: MagixEndpoint): Job
}

public class MagixConverter(
    public val filter: MagixMessageFilter,
    public val transformer: (JsonElement) -> JsonElement,
) : MagixProcessor {
    override fun process(endpoint: MagixEndpoint): Job = endpoint.scope.launch {
        endpoint.subscribe(JsonElement.serializer(), filter).onEach {
            TODO()
        }
    }
}