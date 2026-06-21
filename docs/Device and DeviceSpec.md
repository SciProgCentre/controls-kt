# Device and DeviceSpec - what is the difference?

One of the problems with creating device servers is that one needs device properties to be accessible both in static and dynamic mode. For example, consider a property:

```kotlin
var property: Double = 1.0

```

We can change the state of the property, but neither propagate this change to the device, nor observe changes made to the property value by the device. The propagation to the device state could be added via custom getters and setters:

```kotlin
var property: Double
    get() = device.read(...)
    set(value){
        device.write(..., value)
    }
```

But this approach does not solve the observability problem. Neither it exposes the property to be automatically collected from the outside of the device.

The next stop is to use Kotlin delegates:

```kotlin
var property by property(
    read = { device.read(...)},
    write = {value-> device.write(..., value)}
)
```

Delegate solves almost all problems: it allows reading and writing the hardware, also it allows registering observation handles to listen to property changes externally. The only problem left is that properties registered this way are created on object instance creation and not accessible without creating the device instance.

To solve this problem `Controls-kt` allows to separate device properties specification from the device itself.

Check [DemoDevice](../demo/all-things/src/main/kotlin/space/kscience/controls/demo/DemoDevice.kt) for an example of a device with a specification.

```kotlin
class DemoDeviceState(
    var timeScale: Double = 5000.0,
    var sinScale: Double = 1.0,
    var cosScale: Double = 1.0,
    var comment: String = "",
) {
    fun time(): Instant = Instant.now()

    fun sinValue(): Double = sin(time().toEpochMilli().toDouble() / timeScale) * sinScale

    fun cosValue(): Double = cos(time().toEpochMilli().toDouble() / timeScale) * cosScale
}

object DemoDevice : DeviceWithStateFactory<DemoDeviceState>() {

    val timeScale by mutableDoubleProperty(
        descriptorBuilder = {
            description = "Real to virtual time scale"
        },
        read = { timeScale },
        write = { timeScale = it },
    )

    val sinScale by mutableDoubleProperty(
        descriptorBuilder = {
            description = "The scale of sin plot"
        },
        read = { sinScale },
        write = { sinScale = it },
    )

    val cosScale by mutableDoubleProperty(
        read = { cosScale },
        write = { cosScale = it },
    )

    val sin by doubleProperty { sinValue() }
    val cos by doubleProperty { cosValue() }

    val coordinates by metaProperty(
        descriptorBuilder = {
            metaDescriptor {
                value("time", ValueType.NUMBER)
            }
        }
    ) {
        Meta {
            "time" put time().toEpochMilli()
            "x" put read(DemoDevice.sin)
            "y" put read(DemoDevice.cos)
        }
    }

    val comment by mutableStringProperty(
        read = { comment },
        write = { comment = it }
    )

    val resetScale by action(MetaConverter.unit, MetaConverter.unit) {
        write(DemoDevice.timeScale, 5000.0)
        write(DemoDevice.sinScale, 1.0)
        write(DemoDevice.cosScale, 1.0)
    }

    context(device: DeviceBase)
    override suspend fun createState(): DemoDeviceState = DemoDeviceState().also {
        device.launch {
            device.read(sinScale)
            device.read(cosScale)
            device.read(timeScale)
        }
        device.doRecurring(50.milliseconds) {
            device.read(sin)
            device.read(cos)
            device.read(coordinates)
        }
    }
}
```

## Device specification

`DeviceSpec` is an interface (usually implemented as an `object`) that defines the property scheme for external communication. It contains only the description of properties and actions, not the implementation.

Specification can define the following components:

* Properties specifications via `property` delegate or specialized delegate variants. 
* Action specification via `action` delegate or specialized delegates.

Implementation of these properties and actions is provided when the `Device` is created.

## Device Factory

A factory combines `DeviceSpec` with the implementation logic. In the example above, `DemoDevice` is a factory that uses `DemoDeviceState` as its internal state.

See [Device Factory and DeviceWithStateBuilder](DeviceFactory.md) for more details.

## Access to properties

To access property values, one needs to use both the device instance and property descriptor from the spec like follows:
```kotlin
val device = DemoDevice.build(context, meta)

val res = device.read(DemoDevice.sin)
```

## Other ways to create a device

It is not obligatory to use `DeviceWithStateFactory` to define a `Device`. One could use `DeviceBuilder` to create a device from an existing `DeviceSpec` or even without one.

See [Device Builder](DeviceBuilder.md) for more details.

