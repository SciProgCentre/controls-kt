package space.kscience.controls.constructor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.simulationDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

internal data class StateChange<T>(
    val state: ValueState<T>,
    val time: Instant,
    val value: T,
)

public class SimulationReportBuilder(
    parentScope: CoroutineScope,
    public val clock: Clock
) : AutoCloseable, CoroutineScope {

    override val coroutineContext: CoroutineContext =
        parentScope.coroutineContext + Job(parentScope.coroutineContext[Job])

    internal val events = mutableListOf<StateChange<*>>()

    private val channel = Channel<StateChange<*>>(10000)

    private val dataPutJob = launch {
        for (message in channel) {
            events.add(message)
        }
    }

    public suspend fun <T> emit(state: ValueState<T>, value: T = state.value, time: Instant = clock.now()) {
        channel.send(StateChange(state, time, value))
    }

    override fun close() {
        cancel()
    }
}

public class SimulationReport internal constructor(internal val data: List<StateChange<*>>)

@Suppress("UNCHECKED_CAST")
public fun <T> SimulationReport.forState(state: ValueState<T>): List<ValueWithTime<T>> = data.filter {
    it.state == state
}.map {
    ValueWithTime(it.value as T, it.time)
}

public fun <T> SimulationReportBuilder.collectState(
    state: ValueState<T>
): Job = state.useValue(this) { value ->
    emit(state, value = value)
}

/**
 * Run simulation using context simulation dispatcher
 */
public suspend fun <M : Model> M.runSimulationWithReport(
    block: suspend M.(reportBuilder: SimulationReportBuilder) -> Unit
): SimulationReport = withContext(context.simulationDispatcher) {
    SimulationReportBuilder(this, clock).use { reportBuilder ->
        block(reportBuilder)
        SimulationReport(reportBuilder.events)
    }
}