package space.kscience.controls.server


import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.getValue
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.html.*
import kotlinx.serialization.json.*
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.respondMessage
import space.kscience.dataforge.meta.toJson
import space.kscience.dataforge.meta.toMeta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.plus
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFlowPlugin
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.start
import space.kscience.magix.server.magixModule
import kotlin.time.Clock


private fun Application.deviceServerModule(manager: DeviceManager) {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "")
        }
    }
    deviceManagerModule(manager)
    routing {
        get("/") {
            call.respondRedirect("/dashboard")
        }
    }
}


public fun EmbeddedServer<*, *>.whenStarted(callback: Application.() -> Unit) {
    monitor.subscribe(ApplicationStarted, callback)
}


private fun JsonObjectBuilder.deviceTree(tree: DeviceTree, namePrefix: Name, expand: Boolean) {
    tree.device?.let { device ->
        put("name", namePrefix.toString())
        if (expand) {
            put("meta", device.meta.toJson())
            put("properties", buildJsonArray {
                device.propertyDescriptors.forEach { descriptor ->
                    add(Json.encodeToJsonElement(descriptor))
                }
            })
            put("actions", buildJsonArray {
                device.actionDescriptors.forEach { actionDescriptor ->
                    add(Json.encodeToJsonElement(actionDescriptor))
                }
            })
        }
    }

    tree.children.forEach { (childName, child) ->
        put(childName, buildJsonObject {
            deviceTree(child, namePrefix + childName, expand)
        })
    }
}

public val WEB_SERVER_TARGET: Name = "@webServer".asName()

public fun Application.deviceManagerModule(
    manager: DeviceManager,
    vararg plugins: MagixFlowPlugin,
    deviceNames: Collection<Name> = manager.descendantDevices().keys,
    route: String = "/",
    buffer: Int = 100,
) {
    if (pluginOrNull(WebSockets) == null) {
        install(WebSockets)
    }

//    if (pluginOrNull(CORS) == null) {
//        install(CORS) {
//            anyHost()
//        }
//    }

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
                        deviceNames.forEach { deviceName: Name ->
                            val device = manager.resolveDevice(deviceName)
                            div {
                                id = deviceName.toString()
                                h2 { +deviceName.toString() }
                                h3 { +"Properties" }
                                ul {
                                    device.propertyDescriptors.forEach { property ->
                                        li {
                                            a(href = "../$deviceName/${property.name}/get") { +"${property.name}: " }
                                            code {
                                                +Json.encodeToString(property)
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
                                                +Json.encodeToString(action)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // get the device tree with or without detalisation
            get("tree") {
                val expand = call.queryParameters["expand"]?.toBoolean() ?: false
                call.respondJson {
                    deviceTree(manager, Name.EMPTY, expand)
                }
            }

            // send a device message to deviceManager
            post("send") {
                val body = call.receiveText()
                val request: DeviceMessage = MagixEndpoint.magixJson.decodeFromString(
                    deserializer = DeviceMessage.serializer(),
                    string = body
                )
                val response = manager.respondMessage(request)
                if (response.isNotEmpty()) {
                    call.respondMessages(response)
                } else {
                    call.respondText("No response")
                }
            }

            route("devices/{target}") {
                //global route for the device

                route("{property}") {
                    get("get") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val request = PropertyGetMessage(
                            time = Clock.System.now(),
                            sourceDevice = WEB_SERVER_TARGET,
                            targetDevice = Name.parse(target),
                            property = property,
                        )

                        val responses = manager.respondMessage(request)
                        if (responses.isNotEmpty()) {
                            call.respondMessages(responses)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                    post("set") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val body = call.receiveText()
                        val json = Json.parseToJsonElement(body)

                        val request = PropertySetMessage(
                            time = Clock.System.now(),
                            sourceDevice = WEB_SERVER_TARGET,
                            targetDevice = Name.parse(target),
                            property = property,
                            value = json.toMeta()
                        )

                        val responses = manager.respondMessage(request)
                        if (responses.isNotEmpty()) {
                            call.respondMessages(responses)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }

                    webSocket("subscribe") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val device = manager.resolveDevice(target)

                        device.propertyMessageFlow(property).onEach {
                            outgoing.send(Frame.Text(MagixEndpoint.magixJson.encodeToString(it)))
                        }.launchIn(this)
                    }
                }
            }
            openAPI("/openAPI")
        }
    }

    val magixFlow = MutableSharedFlow<MagixMessage>(
        buffer,
        extraBufferCapacity = buffer
    )

    plugins.forEach {
        it.start(this, magixFlow)
    }

    magixModule(magixFlow)
}