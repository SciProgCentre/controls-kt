package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.newCoroutineContext
import space.kscience.controls.time.clock
import space.kscience.controls.time.simulationDispatcher
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

public abstract class ModelConstructor(
    final override val context: Context
) : Model, MutableConstructor {

    public open val modelType: Name
        get() = NameToken("model", hashCode().toHexString()).asName()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext by lazy {
        context.newCoroutineContext(
            Job(context.coroutineContext[Job]) +
                    context.simulationDispatcher +
                    CoroutineName(modelType.toString())
        )
    }


    private val _constructorElements: MutableSet<ConstructorElement> = mutableSetOf<ConstructorElement>()

    override val constructorElements: Set<ConstructorElement> get() = _constructorElements

    override fun registerElement(constructorElement: ConstructorElement) {
        _constructorElements.add(constructorElement)
    }

    override fun unregisterElement(constructorElement: ConstructorElement) {
        _constructorElements.remove(constructorElement)
    }
}

public val Constructor.clock: Clock get() = context.clock