package domain.service

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.TripIntelligence
import bose.ankush.data.model.Weather
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.generativeai.GenerativeModel
import com.google.cloud.vertexai.generativeai.ResponseHandler
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class VertexAiService(
    private val projectId: String,
    private val location: String = "us-central1"
) {
    private val logger = LoggerFactory.getLogger(VertexAiService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun generateTravelIntelligence(
        homeWeather: Weather,
        homeAir: AirQuality,
        destWeather: Weather,
        destAir: AirQuality,
        destinationName: String
    ): TripIntelligence? {
        return try {
            VertexAI(projectId, location).use { vertexAi ->
                val model = GenerativeModel("gemini-1.5-flash", vertexAi)

                val prompt = """
                    Act as a premium travel concierge. A user is traveling to $destinationName.

                    Home Conditions:
                    - Weather: ${homeWeather.current?.temp}°C, ${homeWeather.current?.weather?.firstOrNull()?.description}
                    - Air Quality Index: ${homeAir.list?.firstOrNull()?.main?.aqi}

                    Destination Conditions ($destinationName):
                    - Weather: ${destWeather.current?.temp}°C, ${destWeather.current?.weather?.firstOrNull()?.description}
                    - Air Quality Index: ${destAir.list?.firstOrNull()?.main?.aqi}

                    Compare these conditions and generate a highly personalized travel intelligence JSON object.
                    The response MUST be a valid JSON matching this structure:
                    {
                      "packing": {
                        "header": "String",
                        "advice": "String comparing home and destination weather",
                        "items": ["Item 1", "Item 2"]
                      },
                      "health": {
                        "aqiAlert": "String summary of air quality risk",
                        "advice": "Actionable health advice"
                      },
                      "jetLag": {
                        "strategy": "Advice on adjusting to the destination time zone",
                        "sunlightWindow": "Best hours for outdoor exposure"
                      }
                    }
                    Return ONLY the JSON.
                """.trimIndent()

                val response = model.generateContent(prompt)
                val text = ResponseHandler.getText(response)

                // Clean up Markdown code blocks if present
                val cleanedJson = text.replace("```json", "").replace("```", "").trim()

                json.decodeFromString<TripIntelligence>(cleanedJson)
            }
        } catch (e: Exception) {
            logger.error("Failed to generate crafted suggestion: ${e.message}", e)
            null
        }
    }
}
