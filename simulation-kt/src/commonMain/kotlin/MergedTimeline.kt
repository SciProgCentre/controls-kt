package space.kscience.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


public class MergedTimeline<E : TimelineEvent>(
    timelineScope: CoroutineScope,
    private val timelines: List<Timeline<E>>
) : AbstractTimeline<E>(timelineScope, timelines.minOf { it.time.value }) {

    override fun events(): Flow<E> = flow {
        val buffer = TODO()
//
//        timelines.forEach { timeline ->
//            timeline.observe {
//                collect{
//                    buffer.add(it)
//                }
//            }
//        }
    }

}