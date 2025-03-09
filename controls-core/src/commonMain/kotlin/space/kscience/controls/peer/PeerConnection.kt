package space.kscience.controls.peer

import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta

/**
 * A manager that allows direct synchronous sending and receiving binary data
 */
public interface PeerConnection {
    /**
     * Receive an [Envelope] from a device on a given [address] with given [contentId].
     *
     * The address depends on the specifics of given [PeerConnection]. For example, it could be a TCP/IP port or
     * magix endpoint name.
     *
     * Depending on [PeerConnection] implementation, the resulting [Envelope] could be lazy loaded
     *
     * Additional metadata in [requestMeta] could be required for authentication.
     */
    public suspend fun receive(
        address: String,
        contentId: String,
        requestMeta: Meta = Meta.EMPTY,
    ): Envelope?

    /**
     * Send an [envelope] to a device on a given [address]
     *
     * The address depends on the specifics of given [PeerConnection]. For example, it could be a TCP/IP port or
     * magix endpoint name.
     *
     * Additional metadata in [requestMeta] could be required for authentication.
     */
    public suspend fun send(
        address: String,
        envelope: Envelope,
        requestMeta: Meta = Meta.EMPTY,
    )
}