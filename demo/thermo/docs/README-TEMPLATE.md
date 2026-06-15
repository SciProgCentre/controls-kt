# Module thermo

The thermo module demonstrates a temperature monitoring system using the controls-kt framework. It provides a real-time dashboard for monitoring multiple temperature sensors with visualization capabilities.

## Features

* Real-time temperature monitoring via Modbus protocol
* Visual dashboard with color-coded temperature display
* Real-time temperature trend graphs
* Status monitoring with different states (Normal, Warning, Alarm, NotConnected)
* OPC UA server for exposing sensor data to external systems
* Simulated sensors for demonstration purposes

## Architecture

The module consists of several key components:

1. **ThermoSensor** - Interface for temperature sensors with Modbus implementation
2. **ThermoSensorHub** - Manages multiple sensors and provides access to their data
3. **Dashboard UI** - Compose-based UI for visualizing sensor data

### Device Tree

The hierarchy of devices in the thermo module is as follows:

- **ModbusThermoSensorHub** (Root)
    - **ThermoSensorAnalyzer** (Individual sensor analyzer)
        - **ModbusThermoSensor** (Physical Modbus device)
    - **ThermoSensorGroupAnalyzer** (Aggregates multiple sensors)

A detailed diagram of the device tree can be found in [device-tree.drawio](./device-tree.drawio).

## Running the Demo

The demo creates a simulated Modbus TCP server with 100 virtual temperature sensors (10 units × 10 addresses). The sensors generate random temperature values that fluctuate around the warning threshold.

To run the demo, use the Gradle task:

```bash
./gradlew :demo:thermo:run
```

When the application starts:
1. A Modbus TCP slave is started on port 9090
2. An OPC UA server is started on port 9091
3. The dashboard UI displays all sensors with their current temperatures
4. Sensors can be selected to display their temperature trends on the graph

The main application class is `center.sciprog.controls.demo.thermo.PanelKt`.

## Future Enhancements

* Add spatial position of sensors
* Average spatial temperature analyzer
* 3D visualization for sensors
