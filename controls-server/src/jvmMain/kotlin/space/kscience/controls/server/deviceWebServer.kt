package space.kscience.controls.server


import io.ktor.http.HttpStatusCode
import io.ktor.openapi.KotlinxSerializerJsonSchemaInference
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.openapi.JsonSchemaAttributeKey
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.hide
import io.ktor.server.util.getValue
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.shareIn
import kotlinx.html.*
import kotlinx.serialization.json.*
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.messageFlow
import space.kscience.controls.manager.respondMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.toJson
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.plus
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFlowPlugin
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.start
import space.kscience.magix.server.magixModule
import kotlin.time.Clock


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

private fun deviceSnapshotNode(
    tree: DeviceTree,
    target: Name,
    includeValues: Boolean,
): DeviceSnapshotNode {
    val device = tree.device
    return DeviceSnapshotNode(
        target = target,
        meta = device?.meta ?: Meta.EMPTY,
        properties = device?.propertyDescriptors?.map { descriptor ->
            PropertySnapshot(
                descriptor = descriptor,
                value = if (includeValues) {
                    (device as? CachingDevice)?.getCachedProperty(descriptor.name)
                } else {
                    null
                },
            )
        }.orEmpty(),
        actions = device?.actionDescriptors?.toList().orEmpty(),
        children = tree.children.map { (childName, child) ->
            deviceSnapshotNode(child, target + childName, includeValues)
        },
    )
}

private fun deviceSnapshotNodes(
    tree: DeviceTree,
    includeValues: Boolean,
): List<DeviceSnapshotNode> = if (tree.device != null) {
    listOf(deviceSnapshotNode(tree, Name.EMPTY, includeValues))
} else {
    tree.children.map { (childName, child) ->
        deviceSnapshotNode(child, Name.EMPTY + childName, includeValues)
    }
}

public val WEB_SERVER_TARGET: Name = "@webServer".asName()

@OptIn(ExperimentalKtorApi::class)
public fun Application.deviceTreeModule(
    deviceTree: DeviceTree,
    vararg plugins: MagixFlowPlugin,
    deviceNames: Collection<Name> = deviceTree.descendantDevices().keys,
    route: String = "/",
    buffer: Int = 100,
) {
    val baseSchemaInference = attributes.getOrNull(JsonSchemaAttributeKey)
        ?: KotlinxSerializerJsonSchemaInference.Default
    val schemaInference = baseSchemaInference.withDataForgeJsonSchemas()
    attributes.put(JsonSchemaAttributeKey, schemaInference)

    if (pluginOrNull(WebSockets) == null) {
        install(WebSockets)
    }
    if (pluginOrNull(ContentNegotiation) == null) {
        install(ContentNegotiation) {
            json(MagixEndpoint.magixJson)
        }
    }

    val deviceMessages = deviceTree.messageFlow()
        .buffer(capacity = buffer, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .shareIn(
            this,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 0,
        )
//    if (pluginOrNull(CORS) == null) {
//        install(CORS) {
//            anyHost()
//        }
//    }

    routing {
        openAPI("openAPI")

        val controlsRoute = route(route) {
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
                            val device = deviceTree.resolveDevice(deviceName)
                            div {
                                id = deviceName.toString()
                                h2 { +deviceName.toString() }
                                h3 { +"Properties" }
                                ul {
                                    device.propertyDescriptors.forEach { property ->
                                        li {
                                            a(href = "devices/$deviceName/get/${property.name}") { +"${property.name}: " }
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
            }.describe {
                description = "Device server dashboard"
            }

            /**
             * Returns a tree of devices in a form of Json.
             */
            get("tree") {
                val expand = call.queryParameters["expand"]?.toBoolean() ?: false
                val json = buildJsonObject {
                    deviceTree(deviceTree, Name.EMPTY, expand)
                }
                call.respond(json)
            }.describe {
                operationId = "getTree"
                summary = "Read expanded dynamic device tree"
                description = "Devices represented as a JSON tree"
                parameters {
                    query("expand") {
                        description = "If set to 'true' include device details in the tree"
                        required = false
                        schema = jsonSchema<Boolean>()
                    }
                }
            }

            get("snapshot") {
                val includeValues = call.request.queryParameters["values"]?.toBooleanStrictOrNull() ?: true
                call.respond(
                    ControlsSnapshot(
                        time = Clock.System.now(),
                        nodes = deviceSnapshotNodes(deviceTree, includeValues),
                    )
                )
            }.describe {
                operationId = "getSnapshot"
                summary = "Read normalized device snapshot"
                description = "Devices represented as a normalized JSON snapshot"
                parameters {
                    query("values") {
                        description = "If set to 'true' include current property values"
                        required = false
                        schema = jsonSchema<Boolean>()
                    }
                }
            }

            /**
             * Send a single message [DeviceMessage] to the [DeviceManager]
             *
             * The response contains zero, one or many [DeviceMessage] objects in a form of array.
             */
            post("send") {
                val message = call.receive<DeviceMessage>()
                val response = deviceTree.respondMessage(message)
                call.respond(response)
            }.describe {
                operationId = "postSend"
                summary = "Send a device message"
                description = "Send a single message to the DeviceManager"
            }

            webSocket("events") {
                deviceMessages.collect { message ->
                    outgoing.send(
                        Frame.Text(
                            MagixEndpoint.magixJson.encodeToString(
                                DeviceMessage.serializer(),
                                message,
                            )
                        )
                    )
                }
            }.hide()

            route("devices/{target}") {
                //global route for the device

                /**
                 * Get description for device with given name.
                 *
                 * Should return an array of single [DescriptionMessage]. If device not found, returns an empty array.
                 * Could return [DeviceErrorMessage] in some cases
                 */
                get("description") {
                    val target: String by call.parameters
                    val name = Name.parse(target)
                    val request = GetDescriptionMessage(
                        time = Clock.System.now(),
                        sourceDevice = WEB_SERVER_TARGET,
                        targetDevice = name
                    )
                    val response = deviceTree.respondMessage(request)
                    call.respond(response)
                }.describe {
                    operationId = "getDeviceDescription"
                    summary = "Read device description"
                    description = "Get description for device with given name"
                }

                /**
                 * Get a property value for given device. Returns an array of singe [PropertyChangedMessage].
                 * If device not found, returns code 404.
                 *
                 * Could return one or several [DeviceErrorMessage] in case of errors
                 */
                get("get/{property}") {
                    val target: String by call.parameters
                    val property: String by call.parameters
                    val request = PropertyGetMessage(
                        time = Clock.System.now(),
                        sourceDevice = WEB_SERVER_TARGET,
                        targetDevice = Name.parse(target),
                        property = property,
                    )

                    val responses = deviceTree.respondMessage(request)
                    if (responses.isNotEmpty()) {
                        call.respond(responses)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }.describe {
                    operationId = "getPropertyValue"
                    summary = "Read property value"
                    description = "Get a property value for given device"
                }

                /**
                 * Tries to set value of the property.
                 *
                 * Should return a single [PropertyChangedMessage] in an array.
                 * Returns code 404 if device is not found.
                 *
                 * Could return one or several [DeviceErrorMessage] in case of errors
                 */
                post("set/{property}") {
                    val target: String by call.parameters
                    val property: String by call.parameters
                    val value = call.receive<Meta>()

                    val request = PropertySetMessage(
                        time = Clock.System.now(),
                        sourceDevice = WEB_SERVER_TARGET,
                        targetDevice = Name.parse(target),
                        property = property,
                        value = value
                    )

                    val responses = deviceTree.respondMessage(request)
                    if (responses.isNotEmpty()) {
                        call.respond(responses)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }.describe {
                    operationId = "setPropertyValue"
                    summary = "Set property value"
                    description = "Tries to set value of the property"
                }

                /**
                 *
                 * Subscribes on changes of given property. The current value is always send first
                 *
                 */
                webSocket("subscribe/{property}") {
                    val target: String by call.parameters
                    val property: String by call.parameters
                    val targetName = Name.parse(target)
                    val device = deviceTree.resolveDevice(targetName)

                    val request = PropertyGetMessage(
                        time = Clock.System.now(),
                        sourceDevice = WEB_SERVER_TARGET,
                        targetDevice = targetName,
                        property = property,
                    )

                    deviceTree.respondMessage(request).forEach {
                        outgoing.send(
                            Frame.Text(
                                MagixEndpoint.magixJson.encodeToString(
                                    DeviceMessage.serializer(),
                                    it
                                )
                            )
                        )
                    }

                    device.propertyMessageFlow(property).collect {
                        outgoing.send(
                            Frame.Text(
                                MagixEndpoint.magixJson.encodeToString(
                                    DeviceMessage.serializer(),
                                    it.copy(sourceDevice = targetName)
                                )
                            )
                        )
                    }
                }.hide()
            }
        }

        val openApiSource = OpenApiDocSource.Routing(
            schemaInference = schemaInference,
            routes = { controlsRoute.descendants() },
        )
        controlsRoute.get("contract/openapi.json") {
            val document = openApiSource.read(
                call.application,
                OpenApiDoc(
                    info = OpenApiInfo(
                        title = "Controls Web API",
                        version = "0.1.0",
                        description = "HTTP API for controls device trees.",
                    )
                ),
            )
            call.respondText(document.content, document.contentType)
        }.hide()
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
