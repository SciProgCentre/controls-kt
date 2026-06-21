package space.kscience.controls.plcemu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.meta.Meta

/**
 * Represents the interface for a programmable logic controller (PLC) state.
 * This interface allows interaction with registers, external values,
 * and functions in a synchronous or asynchronous context. It also supports
 * subscribing to external value changes for real-time updates.
 */
public interface PlcState : ContextAware, CoroutineScope {
    /**
     * A clock manager for delays and virtual time
     */
    public val clockManager: ClockManager

    override val context: Context get() = clockManager.context

    /**
     * Read a register or external value
     */
    public suspend fun read(identifier: String): Meta

    /**
     * Write a register or external value
     */
    public suspend fun write(identifier: String, value: Meta)

    /**
     * Call a function with given identifier and arguments
     */
    public suspend fun call(identifier: String, arguments: Meta): Meta

    /**
     * Subscribe to external changes of identifier value
     */
    public fun subscribe(identifier: String): Flow<Meta>
}