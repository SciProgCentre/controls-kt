package hep.dataforge.control.controlers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.PropertyChangeListener
import hep.dataforge.meta.MetaItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch


@ExperimentalCoroutinesApi
suspend fun Device.valueFlow(): Flow<Pair<String, MetaItem<*>>> = callbackFlow {
    val listener = object : PropertyChangeListener {
        override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
            if (value != null) {
                launch {
                    send(propertyName to value)
                }
            }
        }
    }
    registerListener(listener, listener)
    awaitClose {
        removeListener(listener)
    }
}