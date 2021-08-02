package ru.mipt.npm.controls.properties

import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

//read only delegates

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.booleanProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Boolean
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Boolean>>> =
    property(MetaConverter.boolean, name, descriptorBuilder, read)


public fun <D : DeviceBySpec<D>> DeviceSpec<D>.numberProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Number
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Number>>> =
    property(MetaConverter.number, name, descriptorBuilder, read)

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.doubleProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Double
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Double>>> =
    property(MetaConverter.double, name, descriptorBuilder, read)

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.stringProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> String
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, String>>> =
    property(MetaConverter.string, name, descriptorBuilder, read)

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.metaProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Meta
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, Meta>>> =
    property(MetaConverter.meta, name, descriptorBuilder, read)

//read-write delegates

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.booleanProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Boolean,
    write: suspend D.(Boolean) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Boolean>>> =
    property(MetaConverter.boolean, name, descriptorBuilder, read, write)


public fun <D : DeviceBySpec<D>> DeviceSpec<D>.numberProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Number,
    write: suspend D.(Number) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Number>>> =
    property(MetaConverter.number, name, descriptorBuilder, read, write)

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.doubleProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Double,
    write: suspend D.(Double) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Double>>> =
    property(MetaConverter.double, name, descriptorBuilder, read, write)

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.stringProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> String,
    write: suspend D.(String) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, String>>> =
    property(MetaConverter.string, name, descriptorBuilder, read, write)

public fun <D : DeviceBySpec<D>> DeviceSpec<D>.metaProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    read: suspend D.() -> Meta,
    write: suspend D.(Meta) -> Unit
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, Meta>>> =
    property(MetaConverter.meta, name, descriptorBuilder, read, write)