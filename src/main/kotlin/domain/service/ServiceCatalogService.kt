package domain.service

import bose.ankush.data.model.*
import domain.model.Result

/**
 * Service interface for Service Catalog operations.
 * Handles business logic for creating, updating, and managing service offerings.
 */
interface ServiceCatalogService {
    /**
     * Create a new service in the catalog.
     * @param request Service creation request
     * @param adminEmail Email of the admin creating the service
     * @return Result containing the created service configuration
     */
    suspend fun createService(request: CreateServiceRequest, adminEmail: String): Result<ServiceConfig>

    /**
     * Get detailed service information including analytics and history.
     * @param id The service ID
     * @return Result containing service detail response
     */
    suspend fun getService(id: String): Result<ServiceDetailResponse>

    /**
     * List all services with pagination and filtering.
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @param status Optional status filter
     * @param searchQuery Optional search query for service code or display name
     * @return Result containing service list response
     */
    suspend fun listServices(
        page: Int,
        pageSize: Int,
        status: ServiceStatus? = null,
        searchQuery: String? = null
    ): Result<ServiceListResponse>

    /**
     * Update an existing service.
     * @param id The service ID to update
     * @param request Service update request
     * @param adminEmail Email of the admin updating the service
     * @return Result containing the updated service configuration
     */
    suspend fun updateService(id: String, request: UpdateServiceRequest, adminEmail: String): Result<ServiceConfig>

    /**
     * Change the status of a service.
     * @param id The service ID
     * @param newStatus The new status to set
     * @param adminEmail Email of the admin changing the status
     * @return Result containing the updated service configuration
     */
    suspend fun changeServiceStatus(id: String, newStatus: ServiceStatus, adminEmail: String): Result<ServiceConfig>

    /**
     * Clone an existing service to create a new one.
     * @param sourceId The ID of the service to clone
     * @param newServiceCode The service code for the new service
     * @param adminEmail Email of the admin cloning the service
     * @return Result containing the newly created service configuration
     */
    suspend fun cloneService(sourceId: String, newServiceCode: String, adminEmail: String): Result<ServiceConfig>

    /**
     * Get analytics for a specific service.
     * @param serviceCode The service code
     * @return Result containing service analytics
     */
    suspend fun getServiceAnalytics(serviceCode: String): Result<ServiceAnalytics>

    /**
     * Validate a service code format.
     * @param code The service code to validate
     * @return Result containing true if valid, error otherwise
     */
    suspend fun validateServiceCode(code: String): Result<Boolean>
}
