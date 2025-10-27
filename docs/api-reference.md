# API Reference

This document provides a comprehensive reference for the Androidplay API, including all endpoints,
request parameters, response formats, and authentication requirements.

## Related Documentation

- [Database Indexes](./database-indexes.md) - Database performance optimization and index documentation
- [Security Checklist](./security-checklist.md) - Security considerations and compliance checklist

## Table of Contents

- [General Information](#general-information)
    - [Base URL](#base-url)
    - [Authentication](#authentication)
    - [Response Format](#response-format)
    - [Error Handling](#error-handling)
- [Authentication Endpoints](#authentication-endpoints)
    - [Register User](#register-user)
    - [Login User](#login-user)
- [Weather Endpoints](#weather-endpoints)
    - [Get Current Weather](#get-current-weather)
    - [Get Air Pollution](#get-air-pollution)
- [Feedback Endpoints](#feedback-endpoints)
    - [Get Feedback](#get-feedback)
    - [Submit Feedback](#submit-feedback)
    - [Delete Feedback](#delete-feedback)
- [Subscription Endpoints](#subscription-endpoints)
    - [Get Subscription Status](#get-subscription-status)
    - [Get Subscription History](#get-subscription-history)
    - [Cancel Subscription](#cancel-subscription)
- [Admin Subscription Endpoints](#admin-subscription-endpoints)
    - [Get All Subscriptions](#get-all-subscriptions)
    - [Get Subscription Analytics](#get-subscription-analytics)
    - [Admin Cancel Subscription](#admin-cancel-subscription)
- [Admin Financial Endpoints](#admin-financial-endpoints)
    - [Get Financial Metrics](#get-financial-metrics)
    - [Get Payment History](#get-payment-history)
    - [Generate Bill](#generate-bill)
    - [Export Financial Data](#export-financial-data)

## General Information

### Base URL

All API endpoints are relative to the base URL of the API server.

### Authentication

Most endpoints require authentication using a JWT token. To authenticate, include the JWT token in
the `Authorization` header of your request:

```
Authorization: Bearer <your_jwt_token>
```

You can obtain a JWT token by calling the [Login User](#login-user) endpoint.

### Response Format

All API responses follow a standard format:

```json
{
  "status": true,
  "message": "Operation successful",
  "data": {}
}
```

- `status`: A boolean indicating whether the request was successful (`true`) or failed (`false`).
- `message`: A human-readable message describing the result of the request.
- `data`: The response data, which varies depending on the endpoint. For error responses, this may
  be `null` or an empty object.

### Error Handling

When an error occurs, the API returns a response with `status: false` and an appropriate HTTP status
code. The `message` field contains a description of the error.

Common error status codes:

- `400 Bad Request`: The request was malformed or missing required parameters.
- `401 Unauthorized`: Authentication is required or the provided credentials are invalid.
- `403 Forbidden`: The authenticated user does not have permission to access the requested resource.
- `404 Not Found`: The requested resource was not found.
- `500 Internal Server Error`: An unexpected error occurred on the server.

## Authentication Endpoints

### Register User

Registers a new user with email and password.

- **URL**: `/register`
- **Method**: `POST`
- **Authentication**: None
- **Request Body**:

```json
{
  "email": "user@example.com",
  "password": "StrongP@ssw0rd"
}
```

- **Response**:
    - **Success (201 Created)**:
      ```json
      {
        "status": true,
        "message": "Registration successful",
        "data": {
          "_id": "b2d5f3b2-5f9a-4b0f-9f77-123456789abc"
        }
      }
      ```
    - **Error (400 Bad Request)**: Invalid email format or weak password
    - **Error (409 Conflict)**: User already exists

- **Notes**:
    - Email must be a valid email format.
    - Password must be at least 8 characters long and contain at least one digit, one lowercase
      letter, one uppercase letter, and one special character.

### Login User

Authenticates a user and returns a JWT token.

- **URL**: `/login`
- **Method**: `POST`
- **Authentication**: None
- **Request Body**:

```json
{
  "email": "user@example.com",
  "password": "StrongP@ssw0rd"
}
```

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Login successful",
        "data": {
          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
          "email": "user@example.com"
        }
      }
      ```
    - **Error (401 Unauthorized)**: Invalid credentials or user not registered
    - **Error (403 Forbidden)**: Account inactive


## Weather Endpoints

### Get Current Weather

Gets current weather data for a location.

- **URL**: `/weather`
- **Method**: `GET`
- **Authentication**: Required (JWT)
- **Query Parameters**:
    - `lat`: Latitude of the location (required)
    - `lon`: Longitude of the location (required)

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Weather data retrieved successfully",
        "data": {
          "id": 12345,
          "alerts": [
            {
              "description": "Flood Warning",
              "end": 1627776000,
              "event": "Flood",
              "senderName": "National Weather Service",
              "start": 1627689600
            }
          ],
          "current": {
            "clouds": 75,
            "dt": 1627660800,
            "feelsLike": 28.5,
            "humidity": 65,
            "pressure": 1012,
            "sunrise": 1627635600,
            "sunset": 1627686000,
            "temp": 27.2,
            "uvi": 8.5,
            "weather": [
              {
                "description": "scattered clouds",
                "icon": "03d",
                "id": 802,
                "main": "Clouds"
              }
            ],
            "wind_gust": 5.2,
            "wind_speed": 3.6
          },
          "daily": [
            {
              "clouds": 70,
              "dewPoint": 18.5,
              "dt": 1627660800,
              "humidity": 65,
              "pressure": 1012,
              "rain": 2.5,
              "summary": "Partly cloudy with a chance of rain",
              "sunrise": 1627635600,
              "sunset": 1627686000,
              "temp": {
                "day": 27.2,
                "eve": 25.8,
                "max": 29.5,
                "min": 21.3,
                "morn": 22.1,
                "night": 23.4
              },
              "uvi": 8.5,
              "weather": [
                {
                  "description": "light rain",
                  "icon": "10d",
                  "id": 500,
                  "main": "Rain"
                }
              ],
              "windGust": 5.2,
              "windSpeed": 3.6
            }
          ],
          "hourly": [
            {
              "clouds": 75,
              "dt": 1627660800,
              "feelsLike": 28.5,
              "humidity": 65,
              "temp": 27.2,
              "weather": [
                {
                  "description": "scattered clouds",
                  "icon": "03d",
                  "id": 802,
                  "main": "Clouds"
                }
              ]
            }
          ]
        }
      }
      ```
    - **Error (400 Bad Request)**: Missing location parameters
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (500 Internal Server Error)**: Failed to fetch weather data

- **Notes**:
    - The response includes current weather conditions, hourly forecast, daily forecast, and weather
      alerts.
    - The API caches weather data for 30 minutes to improve performance.

### Get Air Pollution

Gets air pollution data for a location.

- **URL**: `/air-pollution`
- **Method**: `GET`
- **Authentication**: Required (JWT)
- **Query Parameters**:
    - `lat`: Latitude of the location (required)
    - `lon`: Longitude of the location (required)

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Air pollution data retrieved successfully",
        "data": {
          "list": [
            {
              "components": {
                "co": 230.31,
                "nh3": 0.82,
                "no": 0.21,
                "no2": 1.35,
                "o3": 68.72,
                "pm10": 15.27,
                "pm2_5": 9.41,
                "so2": 0.58
              },
              "dt": 1627660800,
              "main": {
                "aqi": 2
              }
            }
          ]
        }
      }
      ```
    - **Error (400 Bad Request)**: Missing location parameters
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (500 Internal Server Error)**: Failed to fetch air pollution data

- **Notes**:
    - The `aqi` (Air Quality Index) value ranges from 1 (Good) to 5 (Very Poor).
    - The components are measured in μg/m³ (micrograms per cubic meter).

## Feedback Endpoints

### Get Feedback

Gets feedback by ID.

- **URL**: `/feedback`
- **Method**: `GET`
- **Authentication**: Required (JWT)
- **Query Parameters**:
    - `id`: ID of the feedback to retrieve (required)

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Feedback retrieved successfully",
        "data": {
          "_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
          "deviceId": "android-device-123",
          "deviceOs": "Android 12",
          "feedbackTitle": "App Suggestion",
          "feedbackDescription": "It would be great to have a dark mode option in the app.",
          "timestamp": "1627660800"
        }
      }
      ```
    - **Error (400 Bad Request)**: Missing ID parameter
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (404 Not Found)**: Feedback not found

### Submit Feedback

Submits new feedback.

- **URL**: `/feedback`
- **Method**: `POST`
- **Authentication**: Required (JWT)
- **Query Parameters**:
    - `deviceId`: ID of the device submitting the feedback (required)
    - `deviceOs`: Operating system of the device (required)
    - `feedbackTitle`: Title of the feedback (required)
    - `feedbackDescription`: Description of the feedback (required)

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Feedback submitted successfully",
        "data": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
      }
      ```
    - **Error (400 Bad Request)**: Missing required parameters
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (500 Internal Server Error)**: Failed to save feedback

### Delete Feedback

Deletes feedback by ID.

- **URL**: `/feedback`
- **Method**: `DELETE`
- **Authentication**: Required (JWT)
- **Query Parameters**:
    - `id`: ID of the feedback to delete (required)

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Feedback removed successfully",
        "data": {}
      }
      ```
    - **Error (400 Bad Request)**: Missing ID parameter
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (404 Not Found)**: Feedback not found or removal failed

## Subscription Endpoints

### Get Subscription Status

Gets the current subscription status for the authenticated user.

- **URL**: `/subscriptions/status`
- **Method**: `GET`
- **Authentication**: Required (JWT)
- **Query Parameters**: None

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Subscription status retrieved successfully",
        "data": {
          "service": "PREMIUM_ONE",
          "startDate": "2024-01-15T10:30:00Z",
          "endDate": "2024-02-15T10:30:00Z",
          "status": "ACTIVE",
          "daysRemaining": 15,
          "isInGracePeriod": false,
          "sourcePaymentId": "pay_123456789"
        }
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (404 Not Found)**: No active subscription found
      ```json
      {
        "status": false,
        "message": "No active subscription found",
        "data": null
      }
      ```

- **Notes**:
    - Returns the most recent ACTIVE or GRACE_PERIOD subscription
    - `daysRemaining` is calculated from the current date to the subscription end date
    - `isInGracePeriod` indicates if the subscription is in the 72-hour grace period after expiration
    - Subscription statuses: `ACTIVE`, `EXPIRED`, `CANCELLED`, `GRACE_PERIOD`

### Get Subscription History

Gets the complete subscription history for the authenticated user.

- **URL**: `/subscriptions/history`
- **Method**: `GET`
- **Authentication**: Required (JWT)
- **Query Parameters**: None

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Subscription history retrieved successfully",
        "data": {
          "subscriptions": [
            {
              "service": "PREMIUM_ONE",
              "startDate": "2024-01-15T10:30:00Z",
              "endDate": "2024-02-15T10:30:00Z",
              "status": "ACTIVE",
              "daysRemaining": 15,
              "isInGracePeriod": false,
              "sourcePaymentId": "pay_123456789"
            },
            {
              "service": "PREMIUM_ONE",
              "startDate": "2023-12-15T10:30:00Z",
              "endDate": "2024-01-15T10:30:00Z",
              "status": "EXPIRED",
              "daysRemaining": null,
              "isInGracePeriod": false,
              "sourcePaymentId": "pay_987654321"
            }
          ],
          "totalCount": 2
        }
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token

- **Notes**:
    - Returns all subscriptions ordered by creation date (most recent first)
    - Includes subscriptions with all statuses: ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD
    - `daysRemaining` is null for expired or cancelled subscriptions

### Cancel Subscription

Cancels the active subscription for the authenticated user.

- **URL**: `/subscriptions/cancel`
- **Method**: `POST`
- **Authentication**: Required (JWT)
- **Request Body**: None

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Subscription cancelled successfully",
        "data": {
          "service": "PREMIUM_ONE",
          "startDate": "2024-01-15T10:30:00Z",
          "endDate": "2024-02-15T10:30:00Z",
          "status": "CANCELLED",
          "daysRemaining": 15,
          "isInGracePeriod": false,
          "sourcePaymentId": "pay_123456789"
        }
      }
      ```
    - **Error (400 Bad Request)**: Subscription already cancelled or expired
      ```json
      {
        "status": false,
        "message": "Subscription is already cancelled",
        "data": null
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (404 Not Found)**: No active subscription found
      ```json
      {
        "status": false,
        "message": "No active subscription found",
        "data": null
      }
      ```

- **Notes**:
    - Subscriptions with ACTIVE or GRACE_PERIOD status can be cancelled
    - Cancellation maintains the original end date (user retains access until expiration)
    - Auto-renewal is disabled upon cancellation
    - Premium features remain accessible until the subscription end date
    - A confirmation email is automatically sent to the user (if email is configured)
    - The cancellation succeeds even if the email fails to send

## Admin Subscription Endpoints

### Get All Subscriptions

Gets a paginated list of all user subscriptions with optional status filtering. Requires admin authentication.

- **URL**: `/admin/subscriptions`
- **Method**: `GET`
- **Authentication**: Required (Admin JWT)
- **Query Parameters**:
    - `page`: Page number (default: 1)
    - `pageSize`: Number of items per page (default: 10, max: 100)
    - `status`: Filter by subscription status (optional): `ACTIVE`, `EXPIRED`, `CANCELLED`, `GRACE_PERIOD`

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Subscriptions retrieved successfully",
        "data": {
          "subscriptions": [
            {
              "userEmail": "user@example.com",
              "userId": "b2d5f3b2-5f9a-4b0f-9f77-123456789abc",
              "service": "PREMIUM_ONE",
              "startDate": "2024-01-15T10:30:00Z",
              "endDate": "2024-02-15T10:30:00Z",
              "status": "ACTIVE",
              "daysRemaining": 15,
              "paymentId": "pay_123456789",
              "amount": 299,
              "currency": "INR",
              "createdAt": "2024-01-15T10:30:00Z"
            }
          ],
          "totalCount": 150,
          "page": 1,
          "pageSize": 10
        }
      }
      ```
    - **Error (400 Bad Request)**: Invalid pagination parameters or status filter
      ```json
      {
        "status": false,
        "message": "Invalid page or pageSize parameter",
        "data": null
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (403 Forbidden)**: Admin privileges required
      ```json
      {
        "status": false,
        "message": "Admin privileges required",
        "data": null
      }
      ```

- **Notes**:
    - Only accessible by users with ADMIN role
    - Results are paginated for performance
    - Status filter allows viewing specific subscription states
    - Payment information is enriched from the Payments collection

### Get Subscription Analytics

Gets aggregated subscription metrics and analytics. Requires admin authentication.

- **URL**: `/admin/subscriptions/analytics`
- **Method**: `GET`
- **Authentication**: Required (Admin JWT)
- **Query Parameters**: None

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Subscription analytics retrieved successfully",
        "data": {
          "totalActive": 125,
          "totalExpired": 45,
          "totalCancelled": 18,
          "totalRevenue": 52750.00,
          "averageSubscriptionDays": 28.5,
          "recentSubscriptions": [
            {
              "userEmail": "user@example.com",
              "service": "PREMIUM_ONE",
              "startDate": "2024-01-20T14:30:00Z",
              "amount": 299,
              "status": "ACTIVE"
            }
          ]
        }
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (403 Forbidden)**: Admin privileges required
    - **Error (500 Internal Server Error)**: Failed to calculate analytics

- **Notes**:
    - Only accessible by users with ADMIN role
    - `totalRevenue` is calculated from all verified payments
    - `averageSubscriptionDays` is the mean duration across all subscriptions
    - `recentSubscriptions` shows the 10 most recent subscription activations
    - Analytics are calculated in real-time (consider caching for production)

### Admin Cancel Subscription

Allows an admin to cancel a user's active subscription. Requires admin authentication.

- **URL**: `/admin/subscriptions/cancel`
- **Method**: `POST`
- **Authentication**: Required (Admin JWT)
- **Request Body**:

```json
{
  "userEmail": "user@example.com"
}
```

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "User subscription cancelled successfully",
        "data": {
          "service": "PREMIUM_ONE",
          "startDate": "2024-01-15T10:30:00Z",
          "endDate": "2024-02-15T10:30:00Z",
          "status": "CANCELLED",
          "daysRemaining": 15,
          "isInGracePeriod": false,
          "sourcePaymentId": "pay_123456789"
        }
      }
      ```
    - **Error (400 Bad Request)**: Missing userEmail or subscription already cancelled
      ```json
      {
        "status": false,
        "message": "Subscription is already cancelled",
        "data": null
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (403 Forbidden)**: Admin privileges required
    - **Error (404 Not Found)**: User not found or no active subscription
      ```json
      {
        "status": false,
        "message": "User not found",
        "data": null
      }
      ```

- **Notes**:
    - Only accessible by users with ADMIN role
    - Cancellation maintains the original end date (user retains access until expiration)
    - Admin cancellations are logged for audit purposes
    - The affected user's premium features remain accessible until the subscription end date

## Admin Financial Endpoints

### Get Financial Metrics

Gets aggregated financial metrics including revenue and payment statistics. Requires admin authentication.

- **URL**: `/admin/finance/metrics`
- **Method**: `GET`
- **Authentication**: Required (Admin JWT)
- **Query Parameters**: None

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Financial metrics retrieved successfully",
        "data": {
          "totalRevenue": 125750.50,
          "monthlyRevenue": 15250.00,
          "activeSubscriptionsRevenue": 8970.00,
          "totalPaymentsCount": 542,
          "monthlyRevenueChart": [
            {
              "month": "2024-01",
              "revenue": 12500.00
            },
            {
              "month": "2024-02",
              "revenue": 15250.00
            }
          ]
        }
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (403 Forbidden)**: Admin privileges required
    - **Error (500 Internal Server Error)**: Failed to calculate metrics

- **Notes**:
    - Only accessible by users with ADMIN role
    - `totalRevenue` includes all successful payments
    - `monthlyRevenue` is calculated for the current calendar month
    - `activeSubscriptionsRevenue` is the total value of all active subscriptions
    - `monthlyRevenueChart` provides the last 12 months of revenue data
    - Metrics are cached for 5 minutes to improve performance

### Get Payment History

Gets a paginated list of all payment transactions with optional filtering. Requires admin authentication.

- **URL**: `/admin/finance/payments`
- **Method**: `GET`
- **Authentication**: Required (Admin JWT)
- **Query Parameters**:
    - `page`: Page number (default: 1)
    - `pageSize`: Number of items per page (default: 50, max: 100)
    - `status`: Filter by payment status (optional): `SUCCESS`, `FAILED`, `PENDING`, `REFUNDED`
    - `startDate`: Filter payments from this date (optional, ISO 8601 format)
    - `endDate`: Filter payments until this date (optional, ISO 8601 format)

- **Response**:
    - **Success (200 OK)**:
      ```json
      {
        "status": true,
        "message": "Payment history retrieved successfully",
        "data": {
          "payments": [
            {
              "id": "pay_123456789",
              "userEmail": "user@example.com",
              "amount": 299.00,
              "currency": "INR",
              "paymentMethod": "razorpay",
              "status": "SUCCESS",
              "transactionId": "txn_abc123xyz",
              "createdAt": "2024-02-15T10:30:00Z"
            }
          ],
          "pagination": {
            "page": 1,
            "pageSize": 50,
            "totalCount": 542,
            "totalPages": 11
          }
        }
      }
      ```
    - **Error (400 Bad Request)**: Invalid pagination parameters or date format
      ```json
      {
        "status": false,
        "message": "Invalid date format. Use ISO 8601 format (YYYY-MM-DD)",
        "data": null
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (403 Forbidden)**: Admin privileges required

- **Notes**:
    - Only accessible by users with ADMIN role
    - Results are paginated for performance
    - Date filters use the payment `createdAt` timestamp
    - Status filter allows viewing specific payment states
    - All timestamps are in ISO 8601 format (UTC)

### Generate Bill

Generates a PDF invoice for a user's selected payments and subscriptions. Requires admin authentication.

- **URL**: `/admin/finance/generate-bill`
- **Method**: `POST`
- **Authentication**: Required (Admin JWT)
- **Request Body**:

```json
{
  "userEmail": "user@example.com",
  "paymentIds": ["pay_123456789", "pay_987654321"],
  "subscriptionIds": ["sub_abc123", "sub_xyz789"],
  "sendViaEmail": true
}
```

- **Response**:
    - **Success (200 OK)** - PDF Download:
      ```
      Content-Type: application/pdf
      Content-Disposition: attachment; filename="invoice-1627660800.pdf"

      [PDF Binary Data]
      ```
    - **Success (200 OK)** - Email Sent:
      ```json
      {
        "status": true,
        "message": "Bill generated and sent successfully",
        "data": {
          "invoiceNumber": "INV-20240215-001",
          "emailSent": true
        }
      }
      ```
    - **Success (200 OK)** - Email Failed (PDF still generated):
      ```json
      {
        "status": true,
        "message": "Bill generated but email failed to send",
        "data": {
          "invoiceNumber": "INV-20240215-001",
          "emailSent": false,
          "pdfAvailable": true
        }
      }
      ```
    - **Error (400 Bad Request)**: Missing required fields or invalid IDs
      ```json
      {
        "status": false,
        "message": "At least one payment or subscription ID is required",
        "data": null
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (403 Forbidden)**: Admin privileges required
    - **Error (404 Not Found)**: User not found or invalid transaction IDs
    - **Error (500 Internal Server Error)**: PDF generation failed

- **Notes**:
    - Only accessible by users with ADMIN role
    - At least one payment or subscription ID must be provided
    - If `sendViaEmail` is true, the PDF is sent to the user's email address
    - If `sendViaEmail` is false, the PDF is returned as a downloadable file
    - Invoice includes company information, itemized charges, and payment instructions
    - Invoice numbers are auto-generated in format: INV-YYYYMMDD-XXX
    - All bill generation requests are logged for audit purposes

### Export Financial Data

Exports payment and subscription data in CSV format for a specified date range. Requires admin authentication.

- **URL**: `/admin/tools/export-financial-data`
- **Method**: `POST`
- **Authentication**: Required (Admin JWT)
- **Request Body**:

```json
{
  "exportType": "PAYMENTS",
  "startDate": "2024-01-01",
  "endDate": "2024-02-29"
}
```

- **Export Types**:
    - `PAYMENTS`: Export payment records only
    - `SUBSCRIPTIONS`: Export subscription records only
    - `BOTH`: Export both payments and subscriptions in separate sections

- **Response**:
    - **Success (200 OK)**:
      ```
      Content-Type: text/csv
      Content-Disposition: attachment; filename="financial-export-1627660800.csv"

      Payment ID,User Email,Amount,Currency,Payment Method,Status,Transaction ID,Created At
      "pay_123","user@example.com","299.00","INR","razorpay","SUCCESS","txn_abc","2024-02-15T10:30:00Z"
      ```
    - **Error (400 Bad Request)**: Invalid date range or export type
      ```json
      {
        "status": false,
        "message": "Start date must be before end date",
        "data": null
      }
      ```
    - **Error (401 Unauthorized)**: Missing or invalid JWT token
    - **Error (403 Forbidden)**: Admin privileges required
    - **Error (413 Payload Too Large)**: Export exceeds 10,000 records
      ```json
      {
        "status": false,
        "message": "Export exceeds maximum limit of 10,000 records",
        "data": null
      }
      ```

- **Notes**:
    - Only accessible by users with ADMIN role
    - Date range is inclusive (includes both start and end dates)
    - Maximum export limit is 10,000 records to prevent performance issues
    - CSV fields are properly escaped to handle special characters
    - All timestamps are in ISO 8601 format (UTC)
    - Export requests are logged with admin email and parameters for audit purposes
    - **Payment CSV Columns**: Payment ID, User Email, Amount, Currency, Payment Method, Status, Transaction ID, Created
      At
    - **Subscription CSV Columns**: Subscription ID, User Email, Service Name, Start Date, End Date, Status, Amount,
      Created At
