package hep.dataforge.control.client

public data class TangoPayload(
    val host: String,
    val device: String,
    val name: String,
    val value: Any? = null,
    val timestamp: Long? = null,
    val quality: String = "VALID",
    val event: String? = null,
    val input: Any? = null,
    val output: Any? = null,
    val errors: Iterable<Any?>?,
)