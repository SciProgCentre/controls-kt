package space.kscience.controls.demo.proto

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.ports.KtorPortsPlugin
import space.kscience.controls.ports.Ports
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.invoke

private const val POST_GET_DELAY_MS = 500L
private const val CYCLE_DELAY_MS = 1_000L
private const val DEMO_DURATION_MS = 2000_000L

fun main() = runBlocking {
    val context = Context("ProtoDemo") {
        plugin(Ports)
        plugin(KtorPortsPlugin)
    }

    val deviceManager = context.request(DeviceManager)

    // Configuration for the device. 
    val device = deviceManager.install("demo", DemoDevice, Meta {
        "port" put {
            "type" put "controls.ports.ktor.udp"
            "remoteHost" put "10.0.0.2"
            "remotePort" put 1337
            "localHost" put "10.0.0.1"
            "localPort" put 4242
        }
    })

    println("Device started: $device")

    // Send data periodically
    launch {
        var counter = 10
        var cycle = 1

        while (true) {
            try {
                logCycle(cycle, "set channel=$counter, set/read stabilizationProfile, read voltage")

                // POST: Update 'channel' property
                val postMeta = Meta {
                    "method" put "POST"
                    "channel" put counter
                }
                val postPacketSize = device.packetSize(postMeta)
                logPacket("->", "POST channel=$counter", packetSize = postPacketSize, meta = postMeta)
                val postResponse = device.requestWithData(postMeta)
                logPacket(
                    direction = "<-",
                    label = "POST response",
                    packetSize = postResponse.packetSize,
                    payloadSize = postResponse.data?.size ?: 0,
                    meta = postResponse.meta,
                )

                delay(POST_GET_DELAY_MS)

                // POST: Update 'stabilizationProfile' structured property
                val profileMeta = stabilizationProfilePostMeta(counter)
                val profilePacketSize = device.packetSize(profileMeta)
                logPacket("->", "POST stabilizationProfile", packetSize = profilePacketSize, meta = profileMeta)
                val profileResponse = device.requestWithData(profileMeta)
                logPacket(
                    direction = "<-",
                    label = "POST response",
                    packetSize = profileResponse.packetSize,
                    payloadSize = profileResponse.data?.size ?: 0,
                    meta = profileResponse.meta,
                )

                delay(POST_GET_DELAY_MS)

                // GET: Read 'stabilizationProfile' structured property
                val getProfileMeta = Meta {
                    "method" put "GET"
                    "stabilizationProfile" put ""
                }
                
                logPacket("->", "GET stabilizationProfile", packetSize = device.packetSize(getProfileMeta), meta = getProfileMeta)
                val getProfileResponse = device.requestWithData(getProfileMeta)
                logPacket(
                    direction = "<-",
                    label = "GET response",
                    packetSize = getProfileResponse.packetSize,
                    payloadSize = getProfileResponse.data?.size ?: 0,
                    meta = getProfileResponse.meta,
                )

                delay(POST_GET_DELAY_MS)

                // GET: Read 'voltage' property
                val getMeta = Meta {
                    "method" put "GET"
                    "voltage" put ""
                }
                val getPacketSize = device.packetSize(getMeta)
                logPacket("->", "GET voltage", packetSize = getPacketSize, meta = getMeta)
                val getResponse = device.requestWithData(getMeta)
                logPacket(
                    direction = "<-",
                    label = "GET response",
                    packetSize = getResponse.packetSize,
                    payloadSize = getResponse.data?.size ?: 0,
                    meta = getResponse.meta,
                )

                println("Cycle ${cycle.formatCycle()} complete")
                counter++
                cycle++
            } catch (e: Exception) {
                println("Cycle ${cycle.formatCycle()} failed: ${e.message}")
            }
            delay(CYCLE_DELAY_MS)
        }
    }

    // Keep the demo running
    delay(DEMO_DURATION_MS)
}

private fun stabilizationProfilePostMeta(seed: Int): Meta = Meta {
    "method" put "POST"
    "stabilizationProfile" put {
        "pid" put {
            "p" put (0.1 + seed * 0.01)
            "i" put 0.01
            "d" put 0.001
        }
        "sensor" put {
            "quaternion" put {
                "w" put 1.0
                "x" put 0.0
                "y" put 0.0
                "z" put 0.0
            }
            "healthy" put true
        }
    }
}

private fun logCycle(cycle: Int, description: String) {
    println()
    println("=== Cycle ${cycle.formatCycle()} | $description ===")
}

private fun logPacket(
    direction: String,
    label: String,
    packetSize: Int,
    payloadSize: Int = 0,
    meta: Meta,
) {
    println("$direction $label")
    println("   payload=$payloadSize B, packet=$packetSize B")
    println("   meta: ${meta.summary()}")
}

private fun Meta.summary(): String {
    val children = items.map { (key, child) ->
        val value = child.value?.toString()
        if (value == null) {
            "$key={${child.items.size} field(s)}"
        } else {
            "$key=$value"
        }
    }
    return children.joinToString(separator = ", ").ifBlank { value?.toString() ?: "empty" }
}

private fun Int.formatCycle(): String = toString().padStart(3, '0')
