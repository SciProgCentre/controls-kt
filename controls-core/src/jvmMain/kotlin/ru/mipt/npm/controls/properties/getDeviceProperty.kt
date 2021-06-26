package ru.mipt.npm.controls.properties

import kotlinx.coroutines.runBlocking

/**
 * Blocking property get call
 */
public operator fun <D : DeviceBySpec<D>, T : Any> D.get(
    propertySpec: DevicePropertySpec<D, T>
): T = runBlocking { read(propertySpec) }