package ru.mipt.npm.devices.pimotionmaster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import space.kscience.controls.api.Device
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.propertyFlow


@Composable
fun <D : Device, T : Any> D.composeState(
    spec: DevicePropertySpec<D, T>,
    initialState: T,
): State<T> = propertyFlow(spec).collectAsState(initialState)