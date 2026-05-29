# Device Builder

`DeviceBuilder` provides a DSL for creating devices dynamically. It is useful when you want to create a device without defining a full `DeviceFactory`, or when you want to implement a `DeviceSpec` on the fly.

## Basic usage

You can create a device using the `Device` builder function:

```kotlin
val device = Device(context) {
    var state = 0.0
    
    reader(MyDeviceSpec.value) {
        state
    }
    
    writer(MyDeviceSpec.value) {
        state = it
    }
}
```

## Implementing a DeviceSpec

If you have a `DeviceSpec`, you can use `DeviceBuilder` to provide the implementation for its properties and actions:

```kotlin
object MySpec : AbstractDeviceSpec() {
    val value by doubleProperty()
}

val device = Device(context, spec = MySpec) {
    var internalState = 0.0
    
    reader(MySpec.value) {
        internalState
    }
    
    writer(MySpec.value) {
        internalState = it
    }
}
```

When a `spec` is provided to the builder, it validates that all properties and actions defined in the spec have been implemented in the builder.

## Logical properties

`DeviceBuilder` allows defining "logical" properties which are just wrappers around state with automatic change notification:

```kotlin
val device = Device(context) {
    val myProperty = logical(MetaConverter.double, PropertyDescriptor("prop"), 0.0)
    
    onStart {
        doRecurring(1.seconds) {
            write(myProperty, read(myProperty) + 1.0)
        }
    }
}
```

## Lifecycle management

You can register blocks to be executed when the device starts or stops:

```kotlin
val device = Device(context) {
    onStart {
        println("Device started")
    }
    
    onStop {
        println("Device stopped")
    }
}
```

## DeviceHubBuilder

`DeviceHubBuilder` uses `DeviceBuilder` internally to create a hierarchy of devices:

```kotlin
val hub = DeviceHub(context) {
    device("sensor1") {
        // ...
    }
    device("sensor2") {
        // ...
    }
}
```
