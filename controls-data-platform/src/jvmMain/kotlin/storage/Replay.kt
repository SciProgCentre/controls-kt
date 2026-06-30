package space.kscience.controls.dataplatform.storage

import kotlin.time.Instant


/**
 * An interface for replaying data from storage
 */
public interface Replay  {

    /**
     * Start playback.
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
    )

    /**
     * Stop playback
     */
    public suspend fun stop()
}