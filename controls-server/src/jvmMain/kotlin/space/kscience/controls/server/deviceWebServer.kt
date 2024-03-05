package space.kscience.controls.server


import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyGetMessage
import space.kscience.controls.api.PropertySetMessage
import space.kscience.controls.api.getOrNull
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.respondHubMessage
import space.kscience.dataforge.meta.toMeta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFlowPlugin
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.server.magixModule



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

/**
 * Create and start a web server for several devices
 */
public fun CoroutineScope.startDeviceServer(
    manager: DeviceManager,
    port: Int = MagixEndpoint.DEFAULT_MAGIX_HTTP_PORT,
    host: String = "localhost",
): ApplicationEngine = embeddedServer(CIO, port, host, module = { deviceServerModule(manager) }).start()

public fun ApplicationEngine.whenStarted(callback: Application.() -> Unit) {
    environment.monitor.subscribe(ApplicationStarted, callback)
}


public val WEB_SERVER_TARGET: Name = "@webServer".asName()

public fun Application.deviceManagerModule(
    manager: DeviceManager,
    vararg plugins: MagixFlowPlugin,
    deviceNames: Collection<String> = manager.devices.keys.map { it.toString() },
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

            get("list") {
                call.respondJson {
                    manager.devices.forEach { (name, device) ->
                        put("target", name.toString())
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
            }

            post("message") {
                val body = call.receiveText()
                val request: DeviceMessage = MagixEndpoint.magixJson.decodeFromString(DeviceMessage.serializer(), body)
                val response = manager.respondHubMessage(request)
                if (response.isNotEmpty()) {
                    call.respondMessages(response)
                } else {
                    call.respondText("No response")
                }
            }

            route("{target}") {
                //global route for the device

                route("{property}") {
                    get("get") {
                        val target: String by call.parameters
                        val property: String by call.parameters
                        val request = PropertyGetMessage(
                            sourceDevice = WEB_SERVER_TARGET,
                            targetDevice = Name.parse(target),
                            property = property,
                        )

                        val responses = manager.respondHubMessage(request)
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
                            sourceDevice = WEB_SERVER_TARGET,
                            targetDevice = Name.parse(target),
                            property = property,
                            value = json.toMeta()
                        )

                        val responses = manager.respondHubMessage(request)
                        if (responses.isNotEmpty()) {
                            call.respondMessages(responses)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
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