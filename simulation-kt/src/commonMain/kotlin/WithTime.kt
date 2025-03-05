package space.kscience.simulation

import kotlinx.datetime.Instant

public interface WithTime {
    public val time: Instant
}