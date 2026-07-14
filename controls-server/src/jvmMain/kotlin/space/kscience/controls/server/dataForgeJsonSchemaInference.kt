@file:OptIn(ExperimentalSerializationApi::class)

package space.kscience.controls.server

import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonSchemaInference
import io.ktor.openapi.JsonType
import io.ktor.openapi.ReferenceOr
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import space.kscience.dataforge.meta.MetaSerializer
import kotlin.reflect.KType

private const val META_VALUE_SCHEMA_NAME: String = "MetaValue"

private val opaqueJsonSerialNames: Set<String> = setOf(
    MetaSerializer.descriptor.serialName,
    JsonElement.serializer().descriptor.serialName,
).flatMapTo(mutableSetOf()) { serialName ->
    listOf(serialName, serialName.substringAfterLast('.'))
}

private val metaValueSchema: JsonSchema = JsonSchema(
    title = META_VALUE_SCHEMA_NAME,
    oneOf = listOf(
        JsonSchema(type = JsonType.NULL),
        JsonSchema(type = JsonType.BOOLEAN),
        JsonSchema(type = JsonType.NUMBER),
        JsonSchema(type = JsonType.STRING),
        JsonSchema(
            type = JsonType.ARRAY,
            items = ReferenceOr.schema(META_VALUE_SCHEMA_NAME),
        ),
        JsonSchema(
            type = JsonType.OBJECT,
            additionalProperties = AdditionalProperties.PSchema(ReferenceOr.schema(META_VALUE_SCHEMA_NAME)),
        ),
    ).map { schema -> ReferenceOr.value(schema) },
)

private class DataForgeJsonSchemaInference(
    private val delegate: JsonSchemaInference,
) : JsonSchemaInference {
    override fun buildSchema(type: KType): JsonSchema = delegate.buildSchema(type).replaceOpaqueJsonSchemas()
}

internal fun JsonSchemaInference.withDataForgeJsonSchemas(): JsonSchemaInference =
    this as? DataForgeJsonSchemaInference ?: DataForgeJsonSchemaInference(this)

private fun JsonSchema.replaceOpaqueJsonSchemas(): JsonSchema {
    if (title in opaqueJsonSerialNames) return metaValueSchema

    return copy(
        allOf = allOf?.map { it.replaceOpaqueJsonSchemas() },
        oneOf = oneOf?.map { it.replaceOpaqueJsonSchemas() },
        not = not?.replaceOpaqueJsonSchemas(),
        anyOf = anyOf?.map { it.replaceOpaqueJsonSchemas() },
        properties = properties?.mapValues { (_, schema) -> schema.replaceOpaqueJsonSchemas() },
        additionalProperties = when (val value = additionalProperties) {
            is AdditionalProperties.PSchema -> AdditionalProperties.PSchema(value.value.replaceOpaqueJsonSchemas())
            else -> value
        },
        items = items?.replaceOpaqueJsonSchemas(),
        prefixItems = prefixItems?.map { it.replaceOpaqueJsonSchemas() },
    )
}

private fun ReferenceOr<JsonSchema>.replaceOpaqueJsonSchemas(): ReferenceOr<JsonSchema> = when (this) {
    is ReferenceOr.Reference -> {
        val schemaName = ref.removePrefix("#/components/schemas/")
        if (schemaName in opaqueJsonSerialNames) {
            ReferenceOr.schema(META_VALUE_SCHEMA_NAME, isDynamic)
        } else {
            this
        }
    }

    is ReferenceOr.Value -> ReferenceOr.value(value.replaceOpaqueJsonSchemas())
}
