package space.kscience.controls.tagtable

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.readProperty
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.required
import space.kscience.dataforge.names.parseAsName

/**
 *
 */
public class ControlsTagSource(
    public val deviceManager: DeviceManager
) : TagSource {

    override suspend fun readTag(address: Meta): ValueWithTime<Meta> {
        val deviceName = address[ColumnSpec.deviceName]?.parseAsName() ?: error("Device name is not specified")
        val propertyName = address[ColumnSpec.propertyName] ?: error("Property name is not specified")
        val value = deviceManager.readProperty(deviceName, propertyName)
        return ValueWithTime(value, deviceManager.context.clock.now())
    }

    override val state: StateFlow<SourceState> = MutableStateFlow(
        SourceState(
        Meta {
            set(SourceState.lifeCycle, LifecycleState.STARTED)
        }
    ))


    override suspend fun start() {
        // does nothing
    }

    override suspend fun stop() {
        // does nothing
    }

    override val lifecycleState: LifecycleState get() = LifecycleState.STARTED

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