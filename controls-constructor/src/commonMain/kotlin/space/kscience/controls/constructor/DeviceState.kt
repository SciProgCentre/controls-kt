package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter

/**
 * An observable state of a device
 */
public interface DeviceState<T> {
    public val converter: MetaConverter<T>
    public val value: T

    public val valueFlow: Flow<T>

    public val metaFlow: Flow<Meta>
}


/**
 * A mutable state of a device
 */
public interface MutableDeviceState<T> : DeviceState<T>{
    override var value: T
}