package hep.dataforge.control.client

public sealed class TangoPayload(
    val host: String,
    val device: String,
    val name: String,
    val timestamp: Long? = null,
    val quality: String = "VALID",
    val event: String? = null,
//    val input: Any? = null,
//    val output: Any? = null,
//    val errors: Iterable<Any?>?,
)

public class TangoAttributePayload(
    host: String,
    device: String,
    name: String,
    val value: Any? = null,
    timestamp: Long? = null,
    quality: String = "VALID",
    event: String? = null,
    errors: Iterable<Any?>?,
) : TangoPayload(host, device, name, timestamp, quality, event)