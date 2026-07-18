package space.kscience.controls.tagtable.storage

import kotlinx.coroutines.Job
import kotlin.time.Instant


/**
 * An interface for replaying data from storage
 */
public interface Replay  {

    /**
     * Start playback.
     *
     * This method uses caller scope for delays, so if it is called from a simulation scope, it could work in virtual time.
     *
     * @param from The time to start playback from. If null, playback starts from the beginning.
     * @param startTime The time corresponding to the first message in the replay. If null, use the original time.
     * @param timeScale The scale factor for adjusting the playback speed.
     */
    public suspend fun play(
        from: Instant = Instant.DISTANT_PAST,
        to: Instant = Instant.DISTANT_FUTURE,
        startTime: Instant? = null,
        timeScale: Double = 1.0,
    ): Job

    /**
     * Stop playback
     */
    public suspend fun stop()
}