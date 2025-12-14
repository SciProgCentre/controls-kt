package space.kscience.controls.constructor

import kotlinx.coroutines.withContext
import space.kscience.controls.time.simulationDispatcher
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.provider.Provider


/**
 *  An interface for models used for simulation and virtual devices
 */
public interface Model : Constructor, Provider {
    public val models: Map<Name, Model>
        get() = constructorElements.filterIsInstance<ModelConstructorElement>()
            .filter { it.name != null }
            .associate { it.name!! to it.model }

    public val states: Map<Name, ValueState<*>>
        get() = constructorElements.filterIsInstance<StateConstructorElement<*>>()
            .filter { it.name != null }
            .associate { it.name!! to it.state }


    override val defaultTarget: String get() = ValueState.TYPE


    override val defaultChainTarget: String get() = TYPE

    override fun content(target: String): Map<Name, Any> {
        when (target) {
            TYPE -> models

            ValueState.TYPE -> states
        }
        return super.content(target)
    }

    public companion object {
        public const val TYPE: String = "model"
    }
}


/**
 * Run simulation using context simulation dispatcher
 */
public suspend fun <M : Model> M.runSimulation(
    block: suspend M.() -> Unit
) {
    withContext(context.simulationDispatcher) {
        block()
    }
}


public fun <T : ModelConstructor> MutableConstructor.model(model: T): T {
    registerElement(ModelConstructorElement(model))
    return model
}
