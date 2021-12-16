# Description
This module allows you to store [DeviceMessages](/controls-core/src/commonMain/kotlin/ru/mipt/npm/controls/api/DeviceMessage.kt)
from certain [DeviceManager](/controls-core/src/commonMain/kotlin/ru/mipt/npm/controls/controllers/DeviceManager.kt) 
or [MagixMessages](magix/magix-api/src/commonMain/kotlin/ru/mipt/npm/magix/api/MagixMessage.kt) 
from [magix server](/magix/magix-server/src/main/kotlin/ru/mipt/npm/magix/server/server.kt)
in [xodus database](https://github.com/JetBrains/xodus).

# Usage

All usage examples can be found in [VirtualCarController](/demo/car/src/main/kotlin/ru/mipt/npm/controls/demo/car/VirtualCarController.kt).

## Storage from Device Manager

Just call connectMongo. For more details, you can see comments in [source code](/controls-xodus/src/main/kotlin/ru/mipt/npm/controls/xodus/connections.kt)

## Storage from Magix Server

Just pass such lambda as parameter to startMagixServer:
```kotlin
{ flow ->
    // some code
    storeInXodus(flow)
    // some code
}
```
For more details, you can see comments in [source code](/controls-xodus/src/main/kotlin/ru/mipt/npm/controls/xodus/connections.kt)
