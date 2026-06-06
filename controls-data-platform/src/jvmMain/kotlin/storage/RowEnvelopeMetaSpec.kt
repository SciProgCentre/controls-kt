package space.kscience.controls.dataplatform.storage

import space.kscience.controls.instant
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MetaRef
import space.kscience.dataforge.meta.MetaSpec
import kotlin.time.Instant

public object RowEnvelopeMetaSpec: MetaSpec() {
    public val startTime: MetaRef<Instant> by item(MetaConverter.instant)
    public val endTime: MetaRef<Instant> by item(MetaConverter.instant)
}