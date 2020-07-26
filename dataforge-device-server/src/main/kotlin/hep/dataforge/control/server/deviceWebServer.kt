@file:OptIn(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class, FlowPreview::class, UnstableDefault::class)

package hep.dataforge.control.server

import hep.dataforge.control.api.getDevice
import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.control.controllers.MessageController
import hep.dataforge.control.controllers.MessageController.Companion.GET_PROPERTY_ACTION
import hep.dataforge.control.controllers.MessageController.Companion.SET_PROPERTY_ACTION
import hep.dataforge.control.controllers.data
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.html.*
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/**
 * Create and start a web server for several devices
 */
fun CoroutineScope.startDeviceServer(
    manager: DeviceManager,
    port: Int = 8111,
    host: String = "localhost"
): ApplicationEngine {

    return this.embeddedServer(CIO, port, host) {
        install(WebSockets)
        install(CORS) {
            anyHost()
        }
//        install(ContentNegotiation) {
//            json()
//        }
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

fun ApplicationEngine.whenStarted(callback: Application.() -> Unit) {
    environment.monitor.subscribe(ApplicationStarted, callback)
}


const val WEB_SERVER_TARGET = "@webServer"

private suspend fun ApplicationCall.message(target: MessageController) {
    val body = receiveText()
    val json = Json.parseJson(body) as? JsonObject
        ?: throw IllegalArgumentException("The body is not a json object")
    val meta = json.toMeta()

    val request = DeviceMessage.wrap(meta)

    val response = target.respondMessage(request)
    respondMessage(response)
}

private suspend fun ApplicationCall.getProperty(target: MessageController) {
    val property: String by parameters
    val request = DeviceMessage {
        type = GET_PROPERTY_ACTION
        source = WEB_SERVER_TARGET
        this.target = target.deviceTarget
        data {
            name = property
        }
    }

    val response = target.respondMessage(request)
    respondMessage(response)
}

private suspend fun ApplicationCall.setProperty(target: MessageController) {
    val property: String by parameters
    val body = receiveText()
    val json = Json.parseJson(body)

    val request = DeviceMessage {
        type = SET_PROPERTY_ACTION
        source = WEB_SERVER_TARGET
        this.target = target.deviceTarget
        data {
            name = property
            value = json.toMetaItem()
        }
    }

    val response = target.respondMessage(request)
    respondMessage(response)
}

@OptIn(KtorExperimentalAPI::class)
fun Application.deviceModule(
    manager: DeviceManager,
    deviceNames: Collection<String> = manager.devices.keys.map { it.toString() },
    route: String = "/"
) {
    val controllers = deviceNames.associateWith { name ->
        val device = manager.getDevice(name)
        MessageController(device, name, manager.context)
    }

    fun generateFlow(target: String?) = if (target == null) {
        controllers.values.asFlow().flatMapMerge { it.output() }
    } else {
        controllers[target]?.output() ?: error("The device with target $target not found")
    }

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
                            val device = controllers[deviceName]!!.device
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
                    controllers.values.forEach { controller ->
                        "target" to controller.deviceTarget
                        val device = controller.device
                        "properties" to jsonArray {
                            device.propertyDescriptors.forEach { descriptor ->
                                +descriptor.config.toJson()
                            }
                        }
                        "actions" to jsonArray {
                            device.actionDescriptors.forEach { actionDescriptor ->
                                +actionDescriptor.config.toJson()
                            }
                        }
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

                        generateFlow(target).collect {
                            outgoing.send(it.toFrame())
                        }

                    } catch (ex: Exception) {
                        application.log.debug("Closed server socket for ${call.request.queryParameters}")
                    }
                }
            }

            post("message") {
                val target: String by call.request.queryParameters
                val controller =
                    controllers[target] ?: throw IllegalArgumentException("Target $target not found in $controllers")
                call.message(controller)
            }

            route("{target}") {
                //global route for the device

                route("{property}") {
                    get("get") {
                        val target: String by call.parameters
                        val controller = controllers[target]
                            ?: throw IllegalArgumentException("Target $target not found in $controllers")

                        call.getProperty(controller)
                    }
                    post("set") {
                        val target: String by call.parameters
                        val controller =
                            controllers[target]
                                ?: throw IllegalArgumentException("Target $target not found in $controllers")

                        call.setProperty(controller)
                    }
                }
            }
        }
    }
}