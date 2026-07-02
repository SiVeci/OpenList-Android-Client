package io.openlist.client.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Minimal Retrofit converter backed by kotlinx.serialization, implemented in-tree
 * to avoid depending on the external retrofit2-kotlinx-serialization-converter
 * artifact (whose package failed to resolve via the version catalog). Behaviour
 * mirrors that converter: reflective serializer lookup per request/response type.
 */
@OptIn(ExperimentalSerializationApi::class)
class KotlinxJsonConverterFactory(
    private val json: Json,
    private val contentType: MediaType,
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val loader = json.serializersModule.serializer(type)
        return Converter<ResponseBody, Any?> { body ->
            val text = body.use { it.string() }
            json.decodeFromString(loader, text)
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> {
        val saver = json.serializersModule.serializer(type)
        return Converter<Any?, RequestBody> { value ->
            json.encodeToString(saver, value).toRequestBody(contentType)
        }
    }
}
