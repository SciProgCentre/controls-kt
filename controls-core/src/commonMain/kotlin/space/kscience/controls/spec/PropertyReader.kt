package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta

public interface PropertyReader<T> {
    public val spec: DevicePropertySpec<T>

    context(device: DeviceBase)
    public suspend fun read(): T
}

context(device: DeviceBase)
public suspend fun <T> PropertyReader<T>.readMeta(): Meta = spec.converter.convert(read())

public val PropertyReader<*>.descriptor: PropertyDescriptor get() = spec.descriptor

public interface PropertyWriter<T> {
    public val spec: DevicePropertySpec<T>

    context(device: DeviceBase)
    public suspend fun write(value: T)
}

public val PropertyWriter<*>.descriptor: PropertyDescriptor get() = spec.descriptor

context(device: DeviceBase)
public suspend fun <T> PropertyWriter<T>.writeMeta(item: Meta) {
    write(spec.converter.read(item))
}

public interface ActionExecutor<I, O> {
    public val spec: DeviceActionSpec<I, O>

    context(device: DeviceBase)
    public suspend fun execute(input: I): O
}

public val ActionExecutor<*, *>.descriptor: ActionDescriptor get() = spec.descriptor

context(device: DeviceBase)
public suspend fun <I, O> ActionExecutor<I, O>.executeWithMeta(
    item: Meta,
): Meta? {
    val arg: I = spec.inputConverter.read(item)
    val res = execute(arg)
    return res?.let { spec.outputConverter.convert(res) }
}