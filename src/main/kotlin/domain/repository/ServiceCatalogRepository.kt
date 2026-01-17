package domain.repository

import bose.ankush.data.model.ServiceConfig
import bose.ankush.data.model.ServiceHistory
import bose.ankush.data.model.ServiceStatus
import domain.model.Result

/**
 * Repository interface for Service Catalog operations.
 * This interface defines the contract for accessing and manipulating service catalog data.
 */
interface ServiceCatalogRepository {
    /**
     * Create a new service in the catalog.
     * @param service The service configuration to create
     * @return Result containing the created service or an error
     */
    suspend fun createService(service: ServiceConfig): Result<ServiceConfig>

    /**
     * Get a service by its ID.
     * @param id The service ID
     * @return Result containing the service if found, or null if not found
     */
    suspend fun getServiceById(id: String): Result<ServiceConfig?>

    /**
     * Get a service by its unique service code.
     * @param code The service code (e.g., PREMIUM_ONE)
     * @return Result containing the service if found, or null if not found
     */
    suspend fun getServiceByCode(code: String): Result<ServiceConfig?>

    /**
     * Get all services with pagination, filtering, and search.
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @param status Optional status filter
     * @param searchQuery Optional search query for service code or display name
     * @return Result containing pair of (services list, total count)
     */
    suspend fun getAllServices(
        page: Int,
        pageSize: Int,
        status: ServiceStatus? = null,
        searchQuery: String? = null
    ): Result<Pair<List<ServiceConfig>, Long>>

    /**
     * Update an existing service.
     * @param id The service ID to update
     * @param updates The updated service configuration
     * @return Result containing the updated service or an error
     */
    suspend fun updateService(id: String, updates: ServiceConfig): Result<ServiceConfig>

    /**
     * Delete a service (soft delete by changing status).
     * @param id The service ID to delete
     * @return Result containing true if successful
     */
    suspend fun deleteService(id: String): Result<Boolean>

    /**
     * Add a history entry for a service change.
     * @param history The history record to add
     * @return Result containing the created history record
     */
    suspend fun addHistory(history: ServiceHistory): Result<ServiceHistory>

    /**
     * Get the change history for a service.
     * @param serviceId The service ID
     * @return Result containing list of history records in chronological order
     */
    suspend fun getServiceHistory(serviceId: String): Result<List<ServiceHistory>>
}
