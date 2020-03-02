package hep.dataforge.control.base

import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.meta.Meta

interface Action {
    val name: String
    val descriptor: ActionDescriptor
    suspend operator fun invoke(arg: Meta?): Meta?
}

class SimpleAction(
    override val name: String,
    override val descriptor: ActionDescriptor,
    val block: suspend (Meta?)->Meta?
): Action{
    override suspend fun invoke(arg: Meta?): Meta? = block(arg)
}