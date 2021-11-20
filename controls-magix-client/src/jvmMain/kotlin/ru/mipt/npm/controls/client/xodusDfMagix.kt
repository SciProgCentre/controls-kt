package ru.mipt.npm.controls.client

///**
// * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1) and dump messages at xodus entity store
// */
//public fun DeviceManager.connectToMagix(
//    endpoint: MagixEndpoint<DeviceMessage>,
//    endpointID: String = DATAFORGE_MAGIX_FORMAT,
//    entityStore: PersistentEntityStore
//): Job = connectToMagix(endpoint, endpointID) { message ->
//    if (message.payload is PropertyChangedMessage) {
//        entityStore.executeInTransaction {
//            message.toEntity(it)
//        }
//    }
//}
