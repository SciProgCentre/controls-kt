package center.sciprog.controls.demo.thermo

import space.kscience.controls.api.DeviceTree
import space.kscience.dataforge.context.ContextAware

/**
 * An interface representing a hub for managing and analyzing thermal sensors,
 * providing both individual and group-level insights.
 *
 * This interface combines capabilities from DeviceTree and ContextAware,
 * allowing the integration and organization of thermal sensors and their
 * associated analyzers into a hierarchical structure.
 *
 * Functionalities:
 * - Organizes and manages a collection of individual thermal sensors and groups of sensors.
 * - Supports hierarchical device tracking through the children property from DeviceTree.
 * - Enables context awareness for the sensors and groups, adhering to ContextAware.
 *
 * Properties:
 * - `sensors`: A map where the keys are unique identifiers of thermal sensors,
 *   and the values are instances of `ThermoSensorAnalyzer`.
 *   These analyzers provide individual sensor metrics such as temperature,
 *   averaged temperature, and operational status.
 * - `groups`: A map where the keys are identifiers for sensor groups,
 *   and the values are instances of `ThermoSensorGroupAnalyzer`.
 *   Group analyzers offer aggregated metrics for multiple sensors,
 *   including group status and temperature discrepancy.
 *
 * The `children` property includes all individual sensors and groups,
 * combining them into a unified hierarchical structure.
 * Group names in the children map are prefixed with "group[]" for differentiation.
 */
interface ThermoSensorHub : DeviceTree, ContextAware {
    val sensors: Map<String, ThermoSensorAnalyzer>
    val groups: Map<String, ThermoSensorGroupAnalyzer>

    override val children: Map<String, DeviceTree> get() = sensors + groups.mapKeys { "group[${it.key}]" }
}