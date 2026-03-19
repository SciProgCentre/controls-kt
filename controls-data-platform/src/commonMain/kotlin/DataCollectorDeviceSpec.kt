package space.kscience.controls.timeseries

import space.kscience.controls.spec.DeviceBySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.dataforge.context.Context


public class DataCollectorDevice(
    context: Context,
    spec: DataCollectorDeviceSpec
) : DeviceBySpec<DataCollectorDevice>(spec, context) {


}


public class DataCollectorDeviceSpec : DeviceSpec<DataCollectorDevice>() {
}