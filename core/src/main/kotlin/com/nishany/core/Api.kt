package com.nishany.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ApiFailure(val status: Int, val payload: JsonObject, val retryAfter: String? = null) :
    IOException("HTTP $status: ${payload["error"].str()}")
class UncertainDelivery(cause: Throwable) : IOException("The write may have completed; check before sending again.", cause)

interface Transport {
    suspend fun request(path: String, method: String = "GET", body: JsonElement? = null): JsonElement
}

/** One client per login context. No redirects, intercepting proxies, logs, or write retries. */
class NishanyApi(
    private val cookies: CookieJar,
    origin: String = "https://nishany.com",
    allowLocalTestHttp: Boolean = false,
) : Transport, AutoCloseable {
    private val base = origin.toHttpUrl()
    private val originHeader: String
    private val client: OkHttpClient
    init {
        require(base.encodedPath == "/" && base.query == null && base.fragment == null)
        require(base.username.isEmpty() && base.password.isEmpty())
        require(base.isHttps || (allowLocalTestHttp && base.host in setOf("localhost", "127.0.0.1")))
        originHeader = base.toString().removeSuffix("/")
        client = OkHttpClient.Builder().cookieJar(cookies)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS).build()
    }
    override suspend fun request(path: String, method: String, body: JsonElement?): JsonElement = withContext(Dispatchers.IO) {
        require(path.startsWith("/api/") && !path.contains('\\'))
        val url = base.resolve(path) ?: error("Invalid API path")
        require(url.scheme == base.scheme && url.host == base.host && url.port == base.port)
        require(url.encodedPath.startsWith("/api/") && url.fragment == null)
        val writing = method !in setOf("GET", "HEAD")
        val builder = Request.Builder().url(url).header("Accept", "application/json")
            .header("Cache-Control", "no-store")
        if (writing) builder.header("Origin", originHeader)
        val data = if (writing) (body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()) else null
        builder.method(method, data)
        try {
            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val parsed = try { if (raw.isBlank()) JsonNull else WireJson.parseToJsonElement(raw) }
                    catch (e: Exception) {
                        if (writing && response.isSuccessful) throw UncertainDelivery(e)
                        if (response.isSuccessful) throw IOException("Invalid JSON response", e)
                        JsonNull
                    }
                if (!response.isSuccessful) throw ApiFailure(response.code, parsed.obj(), response.header("Retry-After"))
                parsed
            }
        } catch (e: ApiFailure) {
            if (writing && e.status >= 500) throw UncertainDelivery(e)
            throw e
        } catch (e: UncertainDelivery) { throw e
        } catch (e: IOException) {
            if (writing) throw UncertainDelivery(e)
            throw e
        }
    }
    override fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

/** An untrusted identifier is always a single path segment. */
fun segment(value: String): String = HttpUrl.Builder().scheme("https").host("nishany.com")
    .addPathSegment(value).build().encodedPath.removePrefix("/")
