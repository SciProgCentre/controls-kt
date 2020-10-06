@file:OptIn(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class, FlowPreview::class)

package hep.dataforge.control.server


import hep.dataforge.control.api.get
import hep.dataforge.control.controllers.DeviceController.Companion.GET_PROPERTY_ACTION
import hep.dataforge.control.controllers.DeviceController.Companion.SET_PROPERTY_ACTION
import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.control.controllers.respondMessage
import hep.dataforge.meta.toJson
import hep.dataforge.meta.toMeta
import hep.dataforge.meta.toMetaItem
import hep.dataforge.meta.wrap
import io.ktor.application.*
import io.ktor.features.CORS
import io.ktor.features.StatusPages
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.getValue
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.html.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Create and start a web server for several devices
 */
@OptIn(KtorExperimentalAPI::class)
public fun CoroutineScope.startDeviceServer(
    manager: DeviceManager,
    port: Int = 8111,
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
        deviceModule(manager)
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

@OptIn(KtorExperimentalAPI::class)
public fun Application.deviceModule(
    manager: DeviceManager,
    deviceNames: Collection<String> = manager.devices.keys.map { it.toString() },
    route: String = "/",
) {
//    val controllers = deviceNames.associateWith { name ->
//        val device = manager.devices[name.toName()]
//        DeviceController(device, name, manager.context)
//    }
//
//    fun generateFlow(target: String?) = if (target == null) {
//        controllers.values.asFlow().flatMapMerge { it.output() }
//    } else {
//        controllers[target]?.output() ?: error("The device with target $target not found")
//    }

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
                                manager[deviceName] ?: error("The device with name $deviceName not found in $manager")
                            div {
                                id = deviceName
                                h2 { +deviceName }
                                h3 { +"Properties" }
                                ul {
                                    device.propertyDescriptors.forEach { property ->
                                        li {
                                            a(href = "../$deviceName/${property.name}/get") { +"${property.name}: " }
                                            code {
                                                +property.config.toJson().toString()
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
                                                +action.config.toJson().toString()
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
                                add(descriptor.config.toJson())
                            }
                        })
                        put("actions", buildJsonArray {
                            device.actionDescriptors.forEach { actionDescriptor ->
                                add(actionDescriptor.config.toJson())
                            }
                        })
                    }
                }
            }
            //Check if application supports websockets and if it does add a push channel
            if (this.application.featureOrNull(WebSockets) != null) {
                webSocket("ws") {
                    //subscribe on device
                    val target: String? by call.request.queryParameters

                    try {
                        application.log.debug("Opened server socket for ${call.request.queryParameters}")

                        manager.controller.envelopeOutput().collect {
                            outgoing.send(it.toFrame())
                        }

                    } catch (ex: Exception) {
                        application.log.debug("Closed server socket for ${call.request.queryParameters}")
                    }
                }
            }

            post("message") {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body) as? JsonObject
                    ?: throw IllegalArgumentException("The body is not a json object")
                val meta = json.toMeta()

                val request = DeviceMessage.wrap(meta)

                val response = manager.respondMessage(request)
                call.respondMessage(response)
            }

            route("{target}") {
                //global route for the device

                route("{property}") {
                    get("get") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val request = DeviceMessage {
                            action = GET_PROPERTY_ACTION
                            sourceName = WEB_SERVER_TARGET
                            this.targetName = target
                            key = property
                        }

                        val response = manager.respondMessage(request)
                        call.respondMessage(response)
                    }
                    post("set") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val body = call.receiveText()
                        val json = Json.parseToJsonElement(body)

                        val request = DeviceMessage {
                            action = SET_PROPERTY_ACTION
                            sourceName = WEB_SERVER_TARGET
                            this.targetName = target
                            key = property
                            value = json.toMetaItem()

                        }

                        val response = manager.respondMessage(request)
                        call.respondMessage(response)
                    }
                }
            }
        }
    }
}