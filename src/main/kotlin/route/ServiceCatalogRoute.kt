package bose.ankush.route

import bose.ankush.data.model.*
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import domain.model.Result
import domain.service.ServiceCatalogService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.AuthHelper.getAuthenticatedAdminOrRespond

private val serviceCatalogLogger = LoggerFactory.getLogger("ServiceCatalogRoute")

/**
 * Service Catalog routes for admin operations
 * - Service CRUD operations
 * - Service status management
 * - Service cloning
 * - Service analytics
 */
fun Route.serviceCatalogRoute() {
    val serviceCatalogService: ServiceCatalogService by application.inject()

    // Admin service catalog routes - require admin authentication
    route("/services") {

        // GET /services - List all services with pagination and filtering
        get {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@get
            serviceCatalogLogger.info("Admin ${admin.email} listing services")

            // Extract query parameters
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
            val statusParam = call.request.queryParameters["status"]
            val searchQuery = call.request.queryParameters["search"]

            // Validate pagination parameters
            if (page < 1) {
                call.respondError(
                    message = "Page number must be greater than 0",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            if (pageSize < 1 || pageSize > 100) {
                call.respondError(
                    message = "Page size must be between 1 and 100",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            // Parse status filter
            val status = if (statusParam != null) {
                try {
                    ServiceStatus.valueOf(statusParam.uppercase())
                } catch (_: IllegalArgumentException) {
                    call.respondError(
                        message = "Invalid status value. Must be one of: ACTIVE, INACTIVE, ARCHIVED",
                        data = Unit,
                        status = HttpStatusCode.BadRequest
                    )
                    return@get
                }
            } else {
                null
            }

            when (val result = serviceCatalogService.listServices(page, pageSize, status, searchQuery)) {
                is Result.Success -> {
                    serviceCatalogLogger.info("Services listed successfully: ${result.data.totalCount} total")
                    call.respondSuccess(
                        message = "Services retrieved successfully",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    serviceCatalogLogger.warn("Failed to list services: ${result.message}")
                    call.respondError(result.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // POST /services - Create a new service
        post {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@post
            serviceCatalogLogger.info("Admin ${admin.email} creating new service")

            val request = try {
                call.receive<CreateServiceRequest>()
            } catch (e: Exception) {
                serviceCatalogLogger.warn("Malformed service creation request payload", e)
                call.respondError(
                    message = "Malformed JSON payload",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            // Validate request
            if (request.serviceCode.isBlank()) {
                call.respondError(
                    message = "Service code is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            if (request.displayName.isBlank()) {
                call.respondError(
                    message = "Display name is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            if (request.pricingTiers.isEmpty()) {
                call.respondError(
                    message = "At least one pricing tier is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            when (val result = serviceCatalogService.createService(request, admin.email)) {
                is Result.Success -> {
                    serviceCatalogLogger.info("Service created successfully: ${result.data.serviceCode}")
                    call.respondSuccess(
                        message = "Service created successfully",
                        data = result.data,
                        status = HttpStatusCode.Created
                    )
                }

                is Result.Error -> {
                    serviceCatalogLogger.warn("Service creation failed: ${result.message}")
                    val statusCode = when {
                        result.message.contains("already exists", ignoreCase = true) -> HttpStatusCode.Conflict
                        result.message.contains("invalid", ignoreCase = true) -> HttpStatusCode.BadRequest
                        result.message.contains("validation", ignoreCase = true) -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // GET /services/:id - Get service details with analytics and history
        get("/{id}") {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@get

            val serviceId = call.parameters["id"]?.trim()
            if (serviceId.isNullOrBlank()) {
                call.respondError(
                    message = "Service ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            serviceCatalogLogger.info("Admin ${admin.email} retrieving service: $serviceId")

            when (val result = serviceCatalogService.getService(serviceId)) {
                is Result.Success -> {
                    serviceCatalogLogger.info("Service details retrieved: ${result.data.service.serviceCode}")
                    call.respondSuccess(
                        message = "Service details retrieved successfully",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    serviceCatalogLogger.warn("Failed to retrieve service: ${result.message}")
                    val statusCode = if (result.message.contains("not found", ignoreCase = true)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // PUT /services/:id - Update an existing service
        put("/{id}") {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@put

            val serviceId = call.parameters["id"]?.trim()
            if (serviceId.isNullOrBlank()) {
                call.respondError(
                    message = "Service ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@put
            }

            serviceCatalogLogger.info("Admin ${admin.email} updating service: $serviceId")

            val request = try {
                call.receive<UpdateServiceRequest>()
            } catch (e: Exception) {
                serviceCatalogLogger.warn("Malformed service update request payload", e)
                call.respondError(
                    message = "Malformed JSON payload",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@put
            }

            // Validate that at least one field is being updated
            if (request.displayName == null &&
                request.description == null &&
                request.pricingTiers == null &&
                request.features == null &&
                request.limits == null &&
                request.availabilityStart == null &&
                request.availabilityEnd == null
            ) {
                call.respondError(
                    message = "At least one field must be provided for update",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@put
            }

            when (val result = serviceCatalogService.updateService(serviceId, request, admin.email)) {
                is Result.Success -> {
                    serviceCatalogLogger.info("Service updated successfully: ${result.data.serviceCode}")
                    call.respondSuccess(
                        message = "Service updated successfully",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    serviceCatalogLogger.warn("Service update failed: ${result.message}")
                    val statusCode = when {
                        result.message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                        result.message.contains("invalid", ignoreCase = true) -> HttpStatusCode.BadRequest
                        result.message.contains("validation", ignoreCase = true) -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // PATCH /services/:id/status - Change service status
        patch("/{id}/status") {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@patch

            val serviceId = call.parameters["id"]?.trim()
            if (serviceId.isNullOrBlank()) {
                call.respondError(
                    message = "Service ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@patch
            }

            serviceCatalogLogger.info("Admin ${admin.email} changing status for service: $serviceId")

            val request = try {
                call.receive<ChangeServiceStatusRequest>()
            } catch (e: Exception) {
                serviceCatalogLogger.warn("Malformed status change request payload", e)
                call.respondError(
                    message = "Malformed JSON payload",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@patch
            }

            when (val result = serviceCatalogService.changeServiceStatus(serviceId, request.status, admin.email)) {
                is Result.Success -> {
                    serviceCatalogLogger.info("Service status changed successfully: ${result.data.serviceCode} -> ${result.data.status}")
                    call.respondSuccess(
                        message = "Service status changed successfully",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    serviceCatalogLogger.warn("Service status change failed: ${result.message}")
                    val statusCode = when {
                        result.message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                        result.message.contains("invalid transition", ignoreCase = true) -> HttpStatusCode.BadRequest
                        result.message.contains("active purchases", ignoreCase = true) -> HttpStatusCode.BadRequest
                        result.message.contains("cannot", ignoreCase = true) -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // POST /services/:id/clone - Clone an existing service
        post("/{id}/clone") {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@post

            val serviceId = call.parameters["id"]?.trim()
            if (serviceId.isNullOrBlank()) {
                call.respondError(
                    message = "Service ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            serviceCatalogLogger.info("Admin ${admin.email} cloning service: $serviceId")

            val request = try {
                call.receive<CloneServiceRequest>()
            } catch (e: Exception) {
                serviceCatalogLogger.warn("Malformed clone service request payload", e)
                call.respondError(
                    message = "Malformed JSON payload",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            if (request.newServiceCode.isBlank()) {
                call.respondError(
                    message = "New service code is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            when (val result = serviceCatalogService.cloneService(serviceId, request.newServiceCode, admin.email)) {
                is Result.Success -> {
                    serviceCatalogLogger.info("Service cloned successfully: ${result.data.serviceCode}")
                    call.respondSuccess(
                        message = "Service cloned successfully",
                        data = result.data,
                        status = HttpStatusCode.Created
                    )
                }

                is Result.Error -> {
                    serviceCatalogLogger.warn("Service cloning failed: ${result.message}")
                    val statusCode = when {
                        result.message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                        result.message.contains("already exists", ignoreCase = true) -> HttpStatusCode.Conflict
                        result.message.contains("invalid", ignoreCase = true) -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // GET /services/:id/analytics - Get service analytics
        get("/{id}/analytics") {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@get

            val serviceId = call.parameters["id"]?.trim()
            if (serviceId.isNullOrBlank()) {
                call.respondError(
                    message = "Service ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            serviceCatalogLogger.info("Admin ${admin.email} retrieving analytics for service: $serviceId")

            // First get the service to extract the service code
            when (val serviceResult = serviceCatalogService.getService(serviceId)) {
                is Result.Success -> {
                    val serviceCode = serviceResult.data.service.serviceCode

                    when (val analyticsResult = serviceCatalogService.getServiceAnalytics(serviceCode)) {
                        is Result.Success -> {
                            serviceCatalogLogger.info("Service analytics retrieved: $serviceCode")
                            call.respondSuccess(
                                message = "Service analytics retrieved successfully",
                                data = analyticsResult.data,
                                status = HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            serviceCatalogLogger.warn("Failed to retrieve analytics: ${analyticsResult.message}")
                            call.respondError(analyticsResult.message, Unit, HttpStatusCode.InternalServerError)
                        }
                    }
                }

                is Result.Error -> {
                    serviceCatalogLogger.warn("Failed to retrieve service for analytics: ${serviceResult.message}")
                    val statusCode = if (serviceResult.message.contains("not found", ignoreCase = true)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.InternalServerError
                    }
                    call.respondError(serviceResult.message, Unit, statusCode)
                }
            }
        }
    }
}
