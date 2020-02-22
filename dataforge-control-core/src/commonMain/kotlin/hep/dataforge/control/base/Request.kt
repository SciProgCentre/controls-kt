package hep.dataforge.control.base

import hep.dataforge.control.api.RequestDescriptor
import hep.dataforge.meta.MetaItem

interface Request {
    val name: String
    val descriptor: RequestDescriptor
    suspend operator fun invoke(arg: MetaItem<*>?): MetaItem<*>?
}

class SimpleRequest(
    override val name: String,
    override val descriptor: RequestDescriptor,
    val block: suspend (MetaItem<*>?)->MetaItem<*>?
): Request{
    override suspend fun invoke(arg: MetaItem<*>?): MetaItem<*>? = block(arg)
}