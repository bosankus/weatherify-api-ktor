package domain.service.impl

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import domain.model.Result
import domain.repository.WeatherRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeatherServiceImplTest {

    // Create a mock WeatherRepository for testing
    private class MockWeatherRepository : WeatherRepository {
        var shouldReturnSuccess = true

        override suspend fun getWeatherData(lat: String, lon: String): Result<Weather> {
            return if (shouldReturnSuccess) {
                Result.success(Weather())
            } else {
                Result.error("Failed to get weather data")
            }
        }

        override suspend fun getAirPollutionData(lat: String, lon: String): Result<AirQuality> {
            return if (shouldReturnSuccess) {
                Result.success(AirQuality(listOf()))
            } else {
                Result.error("Failed to get air pollution data")
            }
        }
    }

    private val mockRepository = MockWeatherRepository()
    private val weatherService = WeatherServiceImpl(mockRepository)

    @Test
    fun `test validateLocationParams with valid parameters`() {
        // Valid latitude and longitude
        val result = weatherService.validateLocationParams("40.7128", "-74.0060")

        assertTrue(result.isSuccess)
        assertEquals(Pair("40.7128", "-74.0060"), result.getOrNull())
    }

    @Test
    fun `test validateLocationParams with invalid latitude`() {
        // Latitude out of range (-90 to 90)
        val result = weatherService.validateLocationParams("100.0", "-74.0060")

        assertTrue(result.isError)
        assertEquals("Latitude must be between -90 and 90", (result as Result.Error).message)
    }

    @Test
    fun `test validateLocationParams with invalid longitude`() {
        // Longitude out of range (-180 to 180)
        val result = weatherService.validateLocationParams("40.7128", "200.0")

        assertTrue(result.isError)
        assertEquals("Longitude must be between -180 and 180", (result as Result.Error).message)
    }

    @Test
    fun `test validateLocationParams with non-numeric values`() {
        // Non-numeric latitude and longitude
        val result = weatherService.validateLocationParams("invalid", "invalid")

        assertTrue(result.isError)
        assertTrue((result as Result.Error).message.contains("must be valid numbers"))
    }

    @Test
    fun `test validateLocationParams with null values`() {
        // Null latitude and longitude
        val result = weatherService.validateLocationParams(null, null)

        assertTrue(result.isError)
        assertEquals("Latitude and longitude are required", (result as Result.Error).message)
    }

    @Test
    fun `test validateLocationParams with empty values`() {
        // Empty latitude and longitude
        val result = weatherService.validateLocationParams("", "")

        assertTrue(result.isError)
        assertEquals("Latitude and longitude are required", (result as Result.Error).message)
    }

    @Test
    fun `test getWeatherData with valid parameters`() {
        runBlocking {
            // Set up mock repository to return success
            mockRepository.shouldReturnSuccess = true

            // Call the method
            val result = weatherService.getWeatherData("40.7128", "-74.0060")

            // Verify the result
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
        }
    }

    @Test
    fun `test getWeatherData with invalid parameters`() {
        runBlocking {
            // Call the method with invalid parameters
            val result = weatherService.getWeatherData("invalid", "invalid")

            // Verify the result
            assertTrue(result.isError)
            assertEquals("Invalid location parameters", (result as Result.Error).message)
        }
    }

    @Test
    fun `test getWeatherData with repository failure`() {
        runBlocking {
            // Set up mock repository to return failure
            mockRepository.shouldReturnSuccess = false

            // Call the method
            val result = weatherService.getWeatherData("40.7128", "-74.0060")

            // Verify the result
            assertTrue(result.isError)
            assertEquals("Failed to get weather data", (result as Result.Error).message)
        }
    }

    @Test
    fun `test getAirPollutionData with valid parameters`() {
        runBlocking {
            // Set up mock repository to return success
            mockRepository.shouldReturnSuccess = true

            // Call the method
            val result = weatherService.getAirPollutionData("40.7128", "-74.0060")

            // Verify the result
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
        }
    }

}
