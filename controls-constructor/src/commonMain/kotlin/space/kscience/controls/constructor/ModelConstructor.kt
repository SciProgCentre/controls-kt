package space.kscience.controls.constructor

import kotlinx.coroutines.*
import space.kscience.controls.time.clock
import space.kscience.controls.time.coroutineDispatcher
import space.kscience.dataforge.context.Context
import kotlin.coroutines.CoroutineContext

public abstract class ModelConstructor(
    final override val context: Context,
    vararg dependencies: DeviceState<*>,
) : StateContainer, CoroutineScope {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = context.newCoroutineContext(
        SupervisorJob(context.coroutineContext[Job]) +
                CoroutineName("Model ${toString()}") +
                context.coroutineDispatcher
//                CoroutineExceptionHandler { _, throwable ->
//                    launch {
//                        sharedMessageFlow.emit(
//                            DeviceErrorMessage(
//                                time = clock.now(),
//                                errorMessage = throwable.message,
//                                errorType = throwable::class.simpleName,
//                                errorStackTrace = throwable.stackTraceToString()
//                            )
//                        )
//                    }
//                    logger.error(throwable) { "Exception in device $id" }
//                }
    )


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

public val ModelConstructor.clock get() = context.clock