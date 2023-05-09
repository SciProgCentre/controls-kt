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

But this approach does not solve the observability problem. Neither it exposes the property to be automatically collected from the outside of the device

The next stop is to use Kotlin delegates:

```kotlin
var property by property(
    read = { device.read(...)},
    write = {value-> device.write(..., value)}
)
```

Delegate solves almost all problems: it allows reading and writing the hardware, also it allows registering observation handles to listen to property changes externally (one needs to use [delegate providers](https://kotlinlang.org/docs/delegated-properties.html#providing-a-delegate) to register properties eagerly on instance creation. The only problem left is that properties registered this way are created on object instance creation and not accessible without creating the device instance.

In order to solve this problem `Controls-kt` allows to separate device properties specification from the device itself.

Check [DemoDevice](../demo/all-things/src/main/kotlin/space/kscience/controls/demo/DemoDevice.kt) for an example of a device with a specification.

```kotlin
interface IDemoDevice: Device {
    var timeScaleState: Double
    var sinScaleState: Double
    var cosScaleState: Double

    fun time(): Instant = Instant.now()
    fun sinValue(): Double
    fun cosValue(): Double
}

class DemoDevice(context: Context, meta: Meta) : DeviceBySpec<IDemoDevice>(Companion, context, meta), IDemoDevice {
    override var timeScaleState = 5000.0
    override var sinScaleState = 1.0
    override var cosScaleState = 1.0

    override fun sinValue(): Double = sin(time().toEpochMilli().toDouble() / timeScaleState) * sinScaleState

    override fun cosValue(): Double = cos(time().toEpochMilli().toDouble() / timeScaleState) * cosScaleState

    companion object : DeviceSpec<IDemoDevice>(), Factory<DemoDevice> {

        override fun build(context: Context, meta: Meta): DemoDevice = DemoDevice(context, meta)

        // register virtual properties based on actual object state
        val timeScale by mutableProperty(MetaConverter.double, IDemoDevice::timeScaleState) {
            metaDescriptor {
                type(ValueType.NUMBER)
            }
            info = "Real to virtual time scale"
        }

        val sinScale by mutableProperty(MetaConverter.double, IDemoDevice::sinScaleState)
        val cosScale by mutableProperty(MetaConverter.double, IDemoDevice::cosScaleState)

        val sin by doubleProperty(read = IDemoDevice::sinValue)
        val cos by doubleProperty(read = IDemoDevice::cosValue)

        val coordinates by metaProperty(
            descriptorBuilder = {
                metaDescriptor {
                    value("time", ValueType.NUMBER)
                }
            }
        ) {
            Meta {
                "time" put time().toEpochMilli()
                "x" put read(sin)
                "y" put read(cos)
            }
        }


        val resetScale by action(MetaConverter.meta, MetaConverter.meta) {
            write(timeScale, 5000.0)
            write(sinScale, 1.0)
            write(cosScale, 1.0)
            null
        }

        override suspend fun IDemoDevice.onOpen() {
            launch {
                read(sinScale)
                read(cosScale)
                read(timeScale)
            }
            doRecurring(50.milliseconds) {
                read(sin)
                read(cos)
                read(coordinates)
            }
        }
    }
}
```

## Device body

Device inherits the class `DeviceBySpec` and takes the specification as an argument. The device itself contains hardware logic, but not communication logic. For example, it does not define properties exposed to the external observers. In the given example, it stores states for virtual properties (states) and contains logic to request current values for two properties.

States for logical properties could also be stored via device mechanics without explicit state variables.

## Device specification

Specification is an object (singleton) that defines property scheme for external communication. Specification could define the following components:

* Properties specifications via `property` delegate or specialized delegate variants. 
* Action specification via `action` delegate or specialized delegates.
* Initialization logic (override `onOpen`).
* Finalization logic (override `onClose`).
  
Properties can reference properties and method of the device. They also could contain device-independent logic or manipulate properties (like `coordinates` property in the example does). It is not recommended to implement direct device integration from the spec (yet it is possible).

## Device specification abstraction

In the example, the specification is a companion for `DemoDevice` and could be used as a factory for the device. Yet it works with the abstraction `IDemoDevice`. It is done to demonstrate that the device logic could be separated from the hardware logic. For example, one could swap a real device or a virtual device anytime without changing integrations anywhere. There could be also layers of abstractions for a device.

## Access to properties

In order to access property values, one needs to use both the device instance and property descriptor from the spec like follows:
```kotlin
val device = DemoDevice.build()

val res = device.read(DemoDevice.sin)

```

## Other ways to create a device

It is not obligatory to use `DeviceBySpec` to define a `Device`. One could directly implement the `Device` interface or use intermediate abstraction `DeviceBase`, which uses properties' schema but allows to define it manually.

