package space.kscience.controls.spec

import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.api.metaDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

//read only delegates

public fun <D : DeviceBase<D>> DeviceSpec<D>.booleanProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> Boolean
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Boolean>>> = property(
    MetaConverter.boolean,
    {
        metaDescriptor {
            type(ValueType.BOOLEAN)
        }
        descriptorBuilder()
    },
    name,
    read
)

private inline fun numberDescriptor(
    crossinline descriptorBuilder: PropertyDescriptor.() -> Unit = {}
): PropertyDescriptor.() -> Unit = {
    metaDescriptor {
        type(ValueType.NUMBER)
    }
    descriptorBuilder()
}

public fun <D : DeviceBase<D>> DeviceSpec<D>.numberProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Number
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Number>>> = property(
    MetaConverter.number,
    numberDescriptor(descriptorBuilder),
    name,
    read
)

public fun <D : DeviceBase<D>> DeviceSpec<D>.doubleProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> Double
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Double>>> = property(
    MetaConverter.double,
    numberDescriptor(descriptorBuilder),
    name,
    read
)

public fun <D : DeviceBase<D>> DeviceSpec<D>.stringProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> String
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, String>>> = property(
    MetaConverter.string,
    {
        metaDescriptor {
            type(ValueType.STRING)
        }
        descriptorBuilder()
    },
    name,
    read
)

public fun <D : DeviceBase<D>> DeviceSpec<D>.metaProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> Meta
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Meta>>> = property(
    MetaConverter.meta,
    {
        metaDescriptor {
            type(ValueType.STRING)
        }
        descriptorBuilder()
    },
    name,
    read
)

//read-write delegates

public fun <D : DeviceBase<D>> DeviceSpec<D>.booleanProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> Boolean,
    write: suspend D.(Boolean) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Boolean>>> =
    mutableProperty(
        MetaConverter.boolean,
        {
            metaDescriptor {
                type(ValueType.BOOLEAN)
            }
            descriptorBuilder()
        },
        name,
        read,
        write
    )


public fun <D : DeviceBase<D>> DeviceSpec<D>.numberProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> Number,
    write: suspend D.(Number) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Number>>> =
    mutableProperty(MetaConverter.number, numberDescriptor(descriptorBuilder), name, read, write)

public fun <D : DeviceBase<D>> DeviceSpec<D>.doubleProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> Double,
    write: suspend D.(Double) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Double>>> =
    mutableProperty(MetaConverter.double, numberDescriptor(descriptorBuilder), name, read, write)

public fun <D : DeviceBase<D>> DeviceSpec<D>.stringProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> String,
    write: suspend D.(String) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, String>>> =
    mutableProperty(MetaConverter.string, descriptorBuilder, name, read, write)

public fun <D : DeviceBase<D>> DeviceSpec<D>.metaProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend D.() -> Meta,
    write: suspend D.(Meta) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Meta>>> =
    mutableProperty(MetaConverter.meta, descriptorBuilder, name, read, write)