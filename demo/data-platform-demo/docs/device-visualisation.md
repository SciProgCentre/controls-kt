# UI Specification for Device Visualization

This document specifies the requirements for a Compose Multiplatform-based user interface for visualizing and monitoring devices within a `DeviceHub`.

Visualisation components must be separate from the device management logic. The entry function must take a single `DeviceHub` instance as input and dynamically check if it is a `DeviceParent`. Children device and property information should be extracted from  `DeviceHub` and `Device` instances.

## 1. Device Navigation (Left Panel)

The navigation panel provides a hierarchical view of the devices and their properties.

- **Tree Structure**:
    - The UI shall display a collapsible tree representing the hierarchy of devices in the `DeviceHub`. The root node could be a `DeviceParent` thus being device itself.
    - `DeviceHub` nodes shall be expandable to show their child devices.
    - For `ParentDevice` instances, the node shall display both the root device's own properties and its child devices.
    - Each `Device` node (including leaf devices and root devices of `ParentDevice` nodes) shall be expandable to show its available properties (based on its `propertyDescriptors`).
- **Property Leafs**:
    - Properties are the leaf nodes of the tree.
    - Each property node shall display the property's name.
    - **Selection**: Each property shall be selectable (e.g., via a checkbox). Selecting a property adds it to the time plot in the central area.
    - The selection state shall be maintained regardless of whether the parent device node is expanded or collapsed.

## 2. Central Visualization Area

The central area is dedicated to data visualization, primarily through a time-based plot.

- **Time Plot**:
    - A multi-series time plot shall be displayed in the central area.
    - Use controls-visualisation-compose PlotDeviceProperty if possible.
    - **X-Axis**: Represents time, usually handled automatically by the plotting library.
    - **Y-Axis**: Represents the values of the selected properties.
    - **Series Management**: 
        - For each selected property in the navigation tree, a corresponding time series shall be added to the plot.
        - When a property is deselected, its series shall be removed from the plot.
    - **Property Naming**: 
        - Series labels in the legend and tooltips shall use the **full name** of the property, including the device path (e.g., `platform.opc[0].temperature`).
    - **Real-time Updates**: The plot shall update dynamically as new values are received via the device's property change flow.

## 3. Implementation Notes (Compose Multiplatform)

- Use `LazyColumn` or a specialized tree component for the device navigation.
- Use a plotting library compatible with Compose Multiplatform (e.g., [Let's Plot](https://github.com/JetBrains/lets-plot-kotlin)).
- The `TimeSeriesPlot` provided in `space.kscience.controls.compose.letsplot` should be used for the X-axis.
