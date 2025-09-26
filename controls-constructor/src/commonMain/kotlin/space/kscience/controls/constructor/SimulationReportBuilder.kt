package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.time.simulationDispatcher
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import kotlin.time.Clock

public class SimulationReportBuilder(
    public val scope: CoroutineScope,
    public val clock: Clock
) {

    internal val events: MutableList<PropertyChangedMessage> = mutableListOf<PropertyChangedMessage>()

    private val channel = Channel<PropertyChangedMessage>(10000)

    private val dataPutJob = scope.launch {
        for (message in channel) {
            events.add(message)
        }
    }

    public suspend fun emit(change: PropertyChangedMessage) {
        channel.send(change)
    }
}

public class SimulationReport(public val data: List<PropertyChangedMessage>)

public fun <T> SimulationReportBuilder.collectState(
    state: DeviceState<T>,
    converter: MetaConverter<T>,
    source: Name,
    property: String
): Job = state.useValue(scope) { value ->
    emit(
        PropertyChangedMessage(
            time = clock.now(),
            property = property,
            value = converter.convert(value),
            sourceDevice = source
        )
    )
}

/**
 * Run simulation using context simulation dispatcher
 */
public suspend fun <M : Model> M.runSimulationWithReport(
    block: suspend M.(reportBuilder: SimulationReportBuilder) -> Unit
): SimulationReport = withContext(context.simulationDispatcher) {
    val reportBuilder = SimulationReportBuilder(this, clock)
    block(reportBuilder)
    SimulationReport(reportBuilder.events)
}