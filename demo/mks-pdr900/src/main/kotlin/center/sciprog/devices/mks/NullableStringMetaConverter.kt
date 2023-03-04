package center.sciprog.devices.mks

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.meta.transformations.MetaConverter

object NullableStringMetaConverter : MetaConverter<String?> {
    override fun metaToObject(meta: Meta): String? = meta.string
    override fun objectToMeta(obj: String?): Meta = Meta {}
}