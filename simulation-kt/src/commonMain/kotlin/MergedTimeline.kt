package space.kscience.simulation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


public class MergedTimeline<E : TimelineEvent>(
    private val timelines: List<Timeline<E>>,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : AbstractTimeline<E>(timelines.minOf { it.time.value }, coroutineContext) {

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