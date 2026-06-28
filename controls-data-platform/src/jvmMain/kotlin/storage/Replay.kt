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
     * @param useOriginalTime If true, use the original timestamp of the messages, otherwise use the current time.
     * @param timeScale The scale factor for adjusting the playback speed.
     */
    public suspend fun play(
        from: Instant? = null,
        useOriginalTime: Boolean = false,
        timeScale: Double = 1.0,
    )

    /**
     * Stop playback
     */
    public suspend fun stop()
}