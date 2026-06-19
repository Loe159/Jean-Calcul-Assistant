package com.jeancalcul.assistant.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class HermesClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 15_000
        }
    }

    suspend fun health(baseUrl: String, token: String): HermesHealth = http.get("${baseUrl.clean()}/api/mobile/health") {
        if (token.isNotBlank()) bearerAuth(token)
    }.body()

    suspend fun sendRequest(baseUrl: String, token: String, request: MobileRequest): HermesResponse {
        val raw: JsonObject = http.post("${baseUrl.clean()}/api/mobile/request") {
            if (token.isNotBlank()) bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
        return json.decodeFromJsonElement(HermesResponse.serializer(), raw).copy(raw = raw)
    }

    private fun String.clean(): String = trim().trimEnd('/')
}
