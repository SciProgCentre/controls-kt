package space.kscience.controls.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.getOrReadProperty
import space.kscience.controls.spec.DeviceActionSpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.MutableDevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.dataforge.meta.Meta


/**
 * An accessor that allows DeviceClient to connect to any property without type checks
 */
public suspend fun <T> DeviceClient.read(propertySpec: DevicePropertySpec<*, T>): T =
    propertySpec.converter.readOrNull(readProperty(propertySpec.name)) ?: error("Property read result is not valid")


public suspend fun <T> DeviceClient.request(propertySpec: DevicePropertySpec<*, T>): T =
    propertySpec.converter.read(getOrReadProperty(propertySpec.name))

public suspend fun <T> DeviceClient.write(propertySpec: MutableDevicePropertySpec<*, T>, value: T) {
    writeProperty(propertySpec.name, propertySpec.converter.convert(value))
}

public fun <T> DeviceClient.writeAsync(propertySpec: MutableDevicePropertySpec<*, T>, value: T): Job = launch {
    write(propertySpec, value)
}

public fun <T> DeviceClient.propertyFlow(spec: DevicePropertySpec<*, T>): Flow<T> = messageFlow
    .filterIsInstance<PropertyChangedMessage>()
    .filter { it.property == spec.name }
    .mapNotNull { spec.converter.readOrNull(it.value) }

public fun <T> DeviceClient.onPropertyChange(
    spec: DevicePropertySpec<*, T>,
    scope: CoroutineScope = this,
    callback: suspend PropertyChangedMessage.(T) -> Unit,
): Job = messageFlow
    .filterIsInstance<PropertyChangedMessage>()
    .filter { it.property == spec.name }
    .onEach { change ->
        val newValue = spec.converter.readOrNull(change.value)
        if (newValue != null) {
            change.callback(newValue)
        }
    }.launchIn(scope)

public fun <T> DeviceClient.useProperty(
    spec: DevicePropertySpec<*, T>,
    scope: CoroutineScope = this,
    callback: suspend (T) -> Unit,
): Job = scope.launch {
    callback(read(spec))
    messageFlow
        .filterIsInstance<PropertyChangedMessage>()
        .filter { it.property == spec.name }
        .collect { change ->
            val newValue = spec.converter.readOrNull(change.value)
            if (newValue != null) {
                callback(newValue)
            }
        }
}

public suspend fun <I, O> DeviceClient.execute(actionSpec: DeviceActionSpec<*, I, O>, input: I): O {
    val inputMeta = actionSpec.inputConverter.convert(input)
    val res = execute(actionSpec.name, inputMeta)
    return actionSpec.outputConverter.read(res ?: Meta.EMPTY)
}

public suspend fun <O> DeviceClient.execute(actionSpec: DeviceActionSpec<*, Unit, O>): O {
    val res = execute(actionSpec.name, Meta.EMPTY)
    return actionSpec.outputConverter.read(res ?: Meta.EMPTY)
}