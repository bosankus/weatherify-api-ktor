package data.repository

import bose.ankush.data.model.ServiceConfig
import bose.ankush.data.model.ServiceHistory
import bose.ankush.data.model.ServiceStatus
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoCollection
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.ServiceCatalogRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Implementation of ServiceCatalogRepository that uses MongoDB for data storage.
 * Provides operations for managing service catalog data.
 */
class ServiceCatalogRepositoryImpl(private val databaseModule: DatabaseModule) : ServiceCatalogRepository {
    private val logger = LoggerFactory.getLogger(ServiceCatalogRepositoryImpl::class.java)

    companion object {
        private const val SERVICES_COLLECTION = "services"
        private const val SERVICE_HISTORY_COLLECTION = "service_history"
    }

    init {
        // Create indexes when repository is initialized
        kotlinx.coroutines.runBlocking {
            createIndexes()
        }
    }

    /**
     * Create necessary indexes for services and service_history collections
     */
    private suspend fun createIndexes() {
        try {
            logger.info("Creating indexes for service catalog collections")

            // Services collection indexes
            val servicesCollection = getServicesCollection()

            // Unique index on serviceCode
            servicesCollection.createIndex(
                Indexes.ascending("serviceCode"),
                IndexOptions().unique(true)
            )

            // Index on status for filtering
            servicesCollection.createIndex(
                Indexes.ascending("status")
            )

            // Index on createdAt for sorting
            servicesCollection.createIndex(
                Indexes.descending("createdAt")
            )

            // Compound index on status + createdAt for common queries
            servicesCollection.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("status"),
                    Indexes.descending("createdAt")
                )
            )

            // Service history collection indexes
            val historyCollection = getServiceHistoryCollection()

            // Index on serviceId for querying history by service
            historyCollection.createIndex(
                Indexes.ascending("serviceId")
            )

            // Index on changedAt for chronological sorting
            historyCollection.createIndex(
                Indexes.descending("changedAt")
            )

            logger.info("Service catalog indexes created successfully")
        } catch (e: Exception) {
            logger.error("Failed to create service catalog indexes", e)
        }
    }

    /**
     * Get the services collection
     */
    private fun getServicesCollection(): MongoCollection<ServiceConfig> {
        return databaseModule.getServicesCollection()
    }

    /**
     * Get the service history collection
     */
    private fun getServiceHistoryCollection(): MongoCollection<ServiceHistory> {
        return databaseModule.getServiceHistoryCollection()
    }

    override suspend fun createService(service: ServiceConfig): Result<ServiceConfig> {
        logger.debug("Creating service: ${service.serviceCode}")
        return try {
            // Check for duplicate service code
            val existing = getServiceByCode(service.serviceCode)
            if (existing is Result.Success && existing.data != null) {
                logger.warn("Service code already exists: ${service.serviceCode}")
                return Result.error("Service code already exists: ${service.serviceCode}")
            }

            getServicesCollection().insertOne(service)
            logger.info("Service created successfully: ${service.serviceCode}")
            Result.success(service)
        } catch (e: Exception) {
            logger.error("Failed to create service: ${service.serviceCode}", e)
            Result.error("Failed to create service: ${e.message}", e)
        }
    }

    override suspend fun getServiceById(id: String): Result<ServiceConfig?> {
        logger.debug("Finding service by ID: $id")
        return try {
            val filter = if (ObjectId.isValid(id)) {
                Filters.or(
                    Filters.eq("_id", ObjectId(id)),
                    Filters.eq("_id", id)
                )
            } else {
                Filters.eq("_id", id)
            }

            val service = getServicesCollection().find(filter).firstOrNull()
            logger.debug("Service found: ${service != null}")
            Result.success(service)
        } catch (e: Exception) {
            logger.error("Failed to find service by ID: $id", e)
            Result.error("Failed to find service: ${e.message}", e)
        }
    }

    override suspend fun getServiceByCode(code: String): Result<ServiceConfig?> {
        logger.debug("Finding service by code: $code")
        return try {
            val query = Document("serviceCode", code)
            val service = getServicesCollection().find(query).firstOrNull()
            logger.debug("Service found: ${service != null}")
            Result.success(service)
        } catch (e: Exception) {
            logger.error("Failed to find service by code: $code", e)
            Result.error("Failed to find service: ${e.message}", e)
        }
    }

    override suspend fun getAllServices(
        page: Int,
        pageSize: Int,
        status: ServiceStatus?,
        searchQuery: String?
    ): Result<Pair<List<ServiceConfig>, Long>> {
        logger.debug("Getting all services with page: $page, pageSize: $pageSize, status: $status, search: $searchQuery")
        return try {
            // Build query with filters
            val filters = mutableListOf<Bson>()

            if (status != null) {
                filters.add(Filters.eq("status", status.name))
            }

            if (!searchQuery.isNullOrBlank()) {
                // Search in serviceCode or displayName (case-insensitive)
                filters.add(
                    Filters.or(
                        Filters.regex("serviceCode", searchQuery, "i"),
                        Filters.regex("displayName", searchQuery, "i")
                    )
                )
            }

            val query = if (filters.isNotEmpty()) {
                Filters.and(filters)
            } else {
                Document()
            }

            val skip = (page - 1) * pageSize
            val totalCount = getServicesCollection().countDocuments(query)
            val services = getServicesCollection()
                .find(query)
                .sort(Sorts.descending("createdAt"))
                .skip(skip)
                .limit(pageSize)
                .toList()

            logger.debug("Retrieved ${services.size} services out of $totalCount total")
            Result.success(Pair(services, totalCount))
        } catch (e: Exception) {
            logger.error("Failed to get all services", e)
            Result.error("Failed to get services: ${e.message}", e)
        }
    }

    override suspend fun updateService(id: String, updates: ServiceConfig): Result<ServiceConfig> {
        logger.debug("Updating service: $id")
        return try {
            val filter = if (ObjectId.isValid(id)) {
                Filters.or(
                    Filters.eq("_id", ObjectId(id)),
                    Filters.eq("_id", id)
                )
            } else {
                Filters.eq("_id", id)
            }

            // Update the updatedAt timestamp
            val updatedService = updates.copy(updatedAt = Instant.now().toString())

            val result = getServicesCollection().replaceOne(filter, updatedService)

            if (result.matchedCount == 0L) {
                val msg = "Service not found: $id"
                logger.warn(msg)
                Result.error(msg)
            } else {
                logger.info("Service updated successfully: $id")
                Result.success(updatedService)
            }
        } catch (e: Exception) {
            logger.error("Failed to update service: $id", e)
            Result.error("Failed to update service: ${e.message}", e)
        }
    }

    override suspend fun deleteService(id: String): Result<Boolean> {
        logger.debug("Deleting service (soft delete): $id")
        return try {
            // Soft delete by changing status to ARCHIVED
            val service = getServiceById(id)
            if (service is Result.Error) {
                return service.mapError { msg, ex -> Pair("Failed to find service: $msg", ex) }
            }

            val existingService = (service as Result.Success).data
            if (existingService == null) {
                val msg = "Service not found: $id"
                logger.warn(msg)
                return Result.error(msg)
            }

            val updatedService = existingService.copy(
                status = ServiceStatus.ARCHIVED,
                updatedAt = Instant.now().toString()
            )

            val updateResult = updateService(id, updatedService)
            if (updateResult is Result.Error) {
                return updateResult.mapError { msg, ex -> Pair("Failed to archive service: $msg", ex) }
            }

            logger.info("Service archived successfully: $id")
            Result.success(true)
        } catch (e: Exception) {
            logger.error("Failed to delete service: $id", e)
            Result.error("Failed to delete service: ${e.message}", e)
        }
    }

    override suspend fun addHistory(history: ServiceHistory): Result<ServiceHistory> {
        logger.debug("Adding history entry for service: ${history.serviceId}")
        return try {
            getServiceHistoryCollection().insertOne(history)
            logger.info("History entry added successfully for service: ${history.serviceId}")
            Result.success(history)
        } catch (e: Exception) {
            logger.error("Failed to add history entry for service: ${history.serviceId}", e)
            Result.error("Failed to add history: ${e.message}", e)
        }
    }

    override suspend fun getServiceHistory(serviceId: String): Result<List<ServiceHistory>> {
        logger.debug("Getting history for service: $serviceId")
        return try {
            val query = Document("serviceId", serviceId)
            val history = getServiceHistoryCollection()
                .find(query)
                .sort(Sorts.descending("changedAt"))
                .toList()

            logger.debug("Retrieved ${history.size} history entries for service: $serviceId")
            Result.success(history)
        } catch (e: Exception) {
            logger.error("Failed to get history for service: $serviceId", e)
            Result.error("Failed to get history: ${e.message}", e)
        }
    }
}
