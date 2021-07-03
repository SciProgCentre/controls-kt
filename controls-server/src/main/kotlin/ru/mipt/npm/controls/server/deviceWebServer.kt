package ru.mipt.npm.controls.server


import io.ktor.application.*
import io.ktor.features.CORS
import io.ktor.features.StatusPages
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.util.getValue
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.html.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyGetMessage
import ru.mipt.npm.controls.api.PropertySetMessage
import ru.mipt.npm.controls.api.getOrNull
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.respondMessage
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.server.GenericMagixMessage
import ru.mipt.npm.magix.server.launchMagixServerRawRSocket
import ru.mipt.npm.magix.server.magixModule
import space.kscience.dataforge.meta.toJson
import space.kscience.dataforge.meta.toMetaItem

/**
 * Create and start a web server for several devices
 */
public fun CoroutineScope.startDeviceServer(
    manager: DeviceManager,
    port: Int = MagixEndpoint.DEFAULT_MAGIX_HTTP_PORT,
    host: String = "localhost",
): ApplicationEngine {

    return this.embeddedServer(CIO, port, host) {
        install(WebSockets)
        install(CORS) {
            anyHost()
        }
        install(StatusPages) {
            exception<IllegalArgumentException> { cause ->
                call.respond(HttpStatusCode.BadRequest, cause.message ?: "")
            }
        }
        deviceManagerModule(manager)
        routing {
            get("/") {
                call.respondRedirect("/dashboard")
            }
        }
    }.start()
}

public fun ApplicationEngine.whenStarted(callback: Application.() -> Unit) {
    environment.monitor.subscribe(ApplicationStarted, callback)
}


public const val WEB_SERVER_TARGET: String = "@webServer"

public fun Application.deviceManagerModule(
    manager: DeviceManager,
    deviceNames: Collection<String> = manager.devices.keys.map { it.toString() },
    route: String = "/",
    rawSocketPort: Int = MagixEndpoint.DEFAULT_MAGIX_RAW_PORT,
    buffer: Int = 100,
) {
    if (featureOrNull(WebSockets) == null) {
        install(WebSockets)
    }

    if (featureOrNull(CORS) == null) {
        install(CORS) {
            anyHost()
        }
    }

    routing {
        route(route) {
            get("dashboard") {
                call.respondHtml {
                    head {
                        title("Device server dashboard")
                    }
                    body {
                        h1 {
                            +"Device server dashboard"
                        }
                        deviceNames.forEach { deviceName ->
                            val device =
                                manager.getOrNull(deviceName)
                                    ?: error("The device with name $deviceName not found in $manager")
                            div {
                                id = deviceName
                                h2 { +deviceName }
                                h3 { +"Properties" }
                                ul {
                                    device.propertyDescriptors.forEach { property ->
                                        li {
                                            a(href = "../$deviceName/${property.name}/get") { +"${property.name}: " }
                                            code {
                                                +property.toMeta().toJson().toString()
                                            }
                                        }
                                    }
                                }
                                h3 { +"Actions" }
                                ul {
                                    device.actionDescriptors.forEach { action ->
                                        li {
                                            +("${action.name}: ")
                                            code {
                                                +action.toMeta().toJson().toString()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            get("list") {
                call.respondJson {
                    manager.devices.forEach { (name, device) ->
                        put("target", name.toString())
                        put("properties", buildJsonArray {
                            device.propertyDescriptors.forEach { descriptor ->
                                add(descriptor.toMeta().toJson())
                            }
                        })
                        put("actions", buildJsonArray {
                            device.actionDescriptors.forEach { actionDescriptor ->
                                add(actionDescriptor.toMeta().toJson())
                            }
                        })
                    }
                }
            }

            post("message") {
                val body = call.receiveText()

                val request: DeviceMessage = MagixEndpoint.magixJson.decodeFromString(DeviceMessage.serializer(), body)

                val response = manager.respondMessage(request)
                call.respondMessage(response)
            }

            route("{target}") {
                //global route for the device

                route("{property}") {
                    get("get") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val request = PropertyGetMessage(
                            sourceDevice = WEB_SERVER_TARGET,
                            targetDevice = target,
                            property = property,
                        )

                        val response = manager.respondMessage(request)
                        call.respondMessage(response)
                    }
                    post("set") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val body = call.receiveText()
                        val json = Json.parseToJsonElement(body)

                        val request = PropertySetMessage(
                            sourceDevice = WEB_SERVER_TARGET,
                            targetDevice = target,
                            property = property,
                            value = json.toMetaItem()
                        )

                        val response = manager.respondMessage(request)
                        call.respondMessage(response)
                    }
                }
            }
        }
    }

    val magixFlow = MutableSharedFlow<GenericMagixMessage>(
        buffer,
        extraBufferCapacity = buffer
    )

    launchMagixServerRawRSocket(magixFlow, rawSocketPort)
    magixModule(magixFlow)
}