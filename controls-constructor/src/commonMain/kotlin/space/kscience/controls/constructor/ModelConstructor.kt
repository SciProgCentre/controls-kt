package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.newCoroutineContext
import space.kscience.dataforge.context.Context
import kotlin.coroutines.CoroutineContext

public abstract class ModelConstructor(
    final override val context: Context,
    vararg dependencies: DeviceState<*>,
) : StateContainer, CoroutineScope {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = context.newCoroutineContext(SupervisorJob())


    private val _constructorElements: MutableSet<ConstructorElement> = mutableSetOf<ConstructorElement>().apply {
        dependencies.forEach {
            add(StateConstructorElement(it))
        }
    }

    override val constructorElements: Set<ConstructorElement> get() = _constructorElements

    override fun registerElement(constructorElement: ConstructorElement) {
        _constructorElements.add(constructorElement)
    }

    override fun unregisterElement(constructorElement: ConstructorElement) {
        _constructorElements.remove(constructorElement)
    }
}