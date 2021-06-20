package ru.mipt.npm.controls.api

import space.kscience.dataforge.meta.Scheme
import space.kscience.dataforge.meta.string

/**
 * A descriptor for property
 */
public class PropertyDescriptor(name: String) : Scheme() {
    public val name: String by string(name)
    public var info: String? by string()
}

/**
 * A descriptor for property
 */
public class ActionDescriptor(name: String) : Scheme() {
    public val name: String by string(name)
    public var info: String? by string()
    //var descriptor by spec(ItemDescriptor)
}

