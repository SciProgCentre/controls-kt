package space.kscience.controls.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Asynchronous time provider. The clock could provide virtual time that could be synchronized with other
 * clocks in a virtual environment. Namely, it could suspend until this clock time is behind
 * the global time frontier.
 */
public fun interface AsyncClock {
    public suspend fun now(): Instant

    public companion object {
        public fun real(clock: Clock): AsyncClock = AsyncClock {
            clock.now()
        }
    }
}

public interface AsyncTimeProvider{
    public val clock: AsyncClock
}