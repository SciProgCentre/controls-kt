package space.kscience.controls.tagtable

import space.kscience.controls.api.readProperty
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaRef
import space.kscience.dataforge.meta.MetaSpec
import space.kscience.dataforge.meta.descriptors.required
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.meta.validate
import space.kscience.dataforge.names.parseAsName

/**
 *
 */
public class ControlsTagSource(
    public val deviceManager: DeviceManager
): TagSource {
    override suspend fun readTag(address: Meta): ValueWithTime<Meta> {
        val deviceName = address[ColumnSpec.deviceName]?.parseAsName() ?: error("Device name is not specified")
        val propertyName = address[ColumnSpec.propertyName] ?: error("Property name is not specified")
        val value = deviceManager.readProperty(deviceName, propertyName)
        return ValueWithTime(value, deviceManager.context.clock.now())
    }


    override suspend fun open() {
        // does nothing
    }

    override fun close() {
        // does nothing
    }
    override fun validate(column: TagTableColumn) {
        if (!ColumnSpec.validate(column.config)) error("Invalid OPC address for column $column")
    }


    public object ColumnSpec : MetaSpec() {
        public val deviceName: MetaRef<String> by string {
            required()
        }

        public val propertyName: MetaRef<String> by string {
            required()
        }
    }
}