package space.kscience.controls.spec

import kotlinx.coroutines.runBlocking

/**
 * Blocking property get call
 */
public operator fun <D : DeviceBase<D>, T : Any> D.get(
    propertySpec: DevicePropertySpec<D, T>
): T? = runBlocking { read(propertySpec) }