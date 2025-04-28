package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.updateWeatherData
import bose.ankush.getSecretValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.weatherRoute() {
    val client = HttpClient(CIO) {
        install(HttpCache)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                coerceInputValues = true
                allowSpecialFloatingPointValues = true
                useArrayPolymorphism = false
            })
        }
    }
    route("/get-weather") {
        get {
            val lat = call.request.queryParameters["lat"]
            val lon = call.request.queryParameters["lon"]

            if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
                call.respond(mapOf("error" to "Missing mandatory query parameters: lat and lon"))
                return@get
            }

            try {
                val apiKey = getSecretValue("weather-data-secret")
                val response: String = client.get(getSecretValue("weather-data-url")) {
                    parameter("lat", lat)
                    parameter("lon", lon)
                    parameter("exclude", "minutely")
                    parameter("appid", apiKey)
                }.body()
                call.respond(response)
                updateWeatherData(Json.decodeFromString(response))
            } catch (e: Exception) {
                call.respond(mapOf("error" to e.message))
            }
        }
    }
    route("/get-air-pollution") {
        get {
            val lat = call.request.queryParameters["lat"]
            val lon = call.request.queryParameters["lon"]

            if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
                call.respond(mapOf("error" to "Missing mandatory query parameters: lat and lon"))
                return@get
            }

            try {
                val apiKey = getSecretValue("weather-data-secret")
                val response: String = client.get(getSecretValue("air-pollution-url")) {
                    parameter("lat", lat)
                    parameter("lon", lon)
                    parameter("appid", apiKey)
                }.body()
                call.respond(Json.decodeFromString(response))
            } catch (e: Exception) {
                call.respond(mapOf("error" to e.message))
            }
        }
    }
}
