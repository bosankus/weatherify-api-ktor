# API Reference

This document provides a comprehensive reference for the Androidplay API, including all endpoints,
request parameters, response formats, and authentication requirements.

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
