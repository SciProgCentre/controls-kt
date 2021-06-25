package ru.mipt.npm.controls.properties

import kotlinx.coroutines.runBlocking
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.reflect.KFunction

/**
 * Blocking property get call
 */
public operator fun <D : DeviceBySpec<D>, T : Any> D.get(
    propertySpec: DevicePropertySpec<D, T>
): T = runBlocking { getAsync(propertySpec).await() }