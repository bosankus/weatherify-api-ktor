package domain.service

import bose.ankush.data.model.Trip
import bose.ankush.data.model.TripIntelligence
import domain.model.Result
import domain.repository.SavedLocationRepository
import domain.repository.TripRepository
import domain.repository.WeatherRepository
import org.slf4j.LoggerFactory

class TripService(
    private val tripRepository: TripRepository,
    private val weatherRepository: WeatherRepository,
    private val savedLocationRepository: SavedLocationRepository,
    private val vertexAiService: VertexAiService
) {
    private val logger = LoggerFactory.getLogger(TripService::class.java)

    suspend fun saveTrip(
        email: String,
        destinationName: String,
        destLat: Double,
        destLon: Double,
        startDate: Long,
        endDate: Long
    ): Result<Boolean> {
        val trip = Trip(
            userEmail = email,
            destinationName = destinationName,
            destinationLat = destLat,
            destinationLon = destLon,
            startDate = startDate,
            endDate = endDate
        )
        return tripRepository.saveTrip(trip)
    }

    suspend fun getTrips(email: String): Result<List<Trip>> {
        return tripRepository.getTripsByUser(email)
    }

    suspend fun getActiveTripIntelligence(email: String): Result<Pair<Trip, TripIntelligence?>?> {
        return try {
            val tripResult = tripRepository.getActiveTrip(email)
            if (tripResult is Result.Error) return Result.error(tripResult.message)

            val trip = tripResult.getOrNull() ?: return Result.success(null)

            // Get Home location (take the first saved location as home for now)
            val locationsResult = savedLocationRepository.getLocationsByUser(email)
            val home = if (locationsResult is Result.Success && locationsResult.data.isNotEmpty()) {
                locationsResult.data.first()
            } else {
                null
            }

            // If no home location, we can't do a full comparison, but we can still get destination intelligence
            // For now, let's use a default if home is null (e.g. London)
            val homeLat = home?.lat?.toString() ?: "51.5074"
            val homeLon = home?.lon?.toString() ?: "-0.1278"

            // Fetch Weather and Air Quality for both Home and Destination
            val homeWeather = weatherRepository.getWeatherData(homeLat, homeLon)
            val homeAir = weatherRepository.getAirPollutionData(homeLat, homeLon)
            val destWeather =
                weatherRepository.getWeatherData(trip.destinationLat.toString(), trip.destinationLon.toString())
            val destAir =
                weatherRepository.getAirPollutionData(trip.destinationLat.toString(), trip.destinationLon.toString())

            if (homeWeather is Result.Success && homeAir is Result.Success &&
                destWeather is Result.Success && destAir is Result.Success
            ) {

                val intelligence = vertexAiService.generateTravelIntelligence(
                    homeWeather.data,
                    homeAir.data,
                    destWeather.data,
                    destAir.data,
                    trip.destinationName
                )

                Result.success(Pair(trip, intelligence))
            } else {
                // Fallback: return trip with null intelligence if weather fetch fails
                logger.warn("Failed to fetch weather data for intelligence generation")
                Result.success(Pair(trip, null))
            }
        } catch (e: Exception) {
            Result.error("Failed to get trip intelligence: ${e.message}", e)
        }
    }

    suspend fun deleteTrip(id: String, email: String): Result<Boolean> {
        return tripRepository.deleteTrip(id, email)
    }
}
