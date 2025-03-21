# Module controls-constructor

The CompositeControlComponents module is an extension for creating composite devices with a declarative hierarchy description, lifecycle management, and error handling.

## Usage

Spec creation:
```kotlin
object TemperatureSensorSpec : CompositeControlComponentSpec<TemperatureSensor>() {
    // read only property
    val temperature by doubleProperty(
        descriptorBuilder = {
            description = "Current temperature in degrees Celsius"
        },
        read = { readTemperature() }
    )

    // Read and write property
    val units by stringProperty(
        descriptorBuilder = {
            description = "Temperature measurement units (C or F)"
        },
        read = { getUnits() },
        write = { _, value -> setUnits(value) }
    )

    // Action without parameters
    val reset by unitAction(
        descriptorBuilder = {
            description = "Reset the sensor to the initial values"
        },
        execute = { resetSensor() }
    )

    // Child component
    val calibrationDevice by childSpec(
        fallbackSpec = CalibrationDeviceSpec,
        configBuilder = {
            linked() // Lifecycle mode
            onError = ChildDeviceErrorHandler.RESTART // Error handling strategy
        }
    )
}
```

Device creation:
```kotlin
class TemperatureSensor(
    context: Context,
    meta: Meta = Meta.EMPTY
) : ConfigurableCompositeControlComponent<TemperatureSensor>(
    spec = TemperatureSensorSpec,
    context = context,
    meta = meta
) {
    private var currentTemperature = 25.0
    private var currentUnits = "C"
    
    // Simulation
    suspend fun readTemperature(): Double = currentTemperature
    
    fun getUnits(): String = currentUnits
    
    suspend fun setUnits(units: String) {
        currentUnits = units
    }
    
    suspend fun resetSensor() {
        currentTemperature = 25.0
        currentUnits = "C"
    }
    
    // Child device access
    val calibrationDevice by childDevice<CalibrationDevice>()
}
```

## Artifact:


```
