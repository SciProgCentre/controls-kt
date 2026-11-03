package space.kscience.controls.tagtable.storage

import kotlinx.coroutines.Job
import space.kscience.controls.api.DeviceMessageSource
import kotlin.time.Instant


/**
 * An interface for controlling replay data from storage
 */
public interface Replay  {

    /**
     * Start playback.
     *
     * This method uses caller scope for delays, so if it is called from a simulation scope, it could work in virtual time.
     *
     * @param from The time in original timeline to start playback from. If null, playback starts from the beginning.
     * @param to The time in original timeline to stop playback at. If null, playback continues until the end.
     * @param startTimeOverride The time in replay timeline corresponding to the [from] of the replay. By default, use the original time.
     * @param timeScale The scale factor for adjusting the playback speed.
     */
    public suspend fun play(
        from: Instant = Instant.DISTANT_PAST,
        to: Instant = Instant.DISTANT_FUTURE,
        startTimeOverride: Instant = from,
        timeScale: Double = 1.0,
    ): Job

    /**
     * Stop playback
     */
    public suspend fun pause()
}

public interface ReplayMessageSource: Replay, DeviceMessageSource