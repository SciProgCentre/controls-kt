package space.kscience.controls.dataplatform.timeseries

import space.kscience.kmath.operations.Group
import space.kscience.kmath.series.Series
import space.kscience.kmath.streaming.RingBuffer
import space.kscience.kmath.structures.Buffer
import space.kscience.kmath.structures.last
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.plusAssign

/**
 * A serries that allows pushing and rolls position if new item is added beyond [size]
 */
@OptIn(ExperimentalAtomicApi::class)
public class RollingSeries<T>(
    size: Int,
    algebra: Group<T>,
    startPosition: Int = 0
) : Series<T> {

    private val ringBuffer: RingBuffer<T> = RingBuffer(size, algebra)

    override val origin: Buffer<T> get() = ringBuffer

    private val _position = AtomicInt(startPosition)

    override val position: Int get() = _position.load()

    override val size: Int get() = ringBuffer.size

    override fun get(index: Int): T = ringBuffer[index]

    /**
     * Push value to the next series position and rotate inner buffer if needed
     */
    public suspend fun push(item: T) {
        //if the ring buffer is full, rotate position
        val roll = ringBuffer.isFull()
        ringBuffer.push(item)
        if (roll) {
            _position.plusAssign(1)
        }
    }


    /**
     * Skip the next series position. Reapply the last value if it is present or simply rotate the buffer if it is not.
     */
    public suspend fun skip() {
        if (ringBuffer.size > 0) {
            push(ringBuffer.last())
        } else {
            _position.plusAssign(1)
        }
    }

    override fun toString(): String {
        TODO("Not yet implemented")
    }
}