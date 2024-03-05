package space.kscience.controls.ports

import space.kscience.dataforge.io.Binary

public fun Binary.readShort(position: Int): Short = read(position) { readShort() }