# Data Flow Diagrams

This document illustrates the key data flows in the Weatherify API.

## Weather Data Retrieval Flow

```mermaid
sequenceDiagram
    participant Client
    participant WeatherRoute
    participant WeatherCache
    participant ExternalAPI as External Weather API
    participant Database

    Client->>WeatherRoute: GET /weather?lat=X&lon=Y
    Note over WeatherRoute: Extract and validate location parameters
    
    WeatherRoute->>WeatherCache: Get HttpClient
    WeatherCache-->>WeatherRoute: Return cached or new HttpClient
    
    WeatherRoute->>WeatherCache: Get API key
    WeatherCache->>WeatherCache: Check if cached API key is valid
    alt API key not cached or expired
        WeatherCache->>SecretManager: Get API key from Secret Manager
        SecretManager-->>WeatherCache: Return API key
    end
    WeatherCache-->>WeatherRoute: Return API key
    
    WeatherRoute->>WeatherCache: Get Weather URL
    WeatherCache->>WeatherCache: Check if cached URL is valid
    alt URL not cached or expired
        WeatherCache->>Environment: Get URL from Environment
        Environment-->>WeatherCache: Return URL
    end
    WeatherCache-->>WeatherRoute: Return URL
    
    WeatherRoute->>ExternalAPI: Fetch weather data
    ExternalAPI-->>WeatherRoute: Return weather data
    
    alt Success
        WeatherRoute-->>Client: Return weather data with 200 OK
        WeatherRoute->>Database: Save weather data asynchronously
        Database-->>WeatherRoute: Acknowledge save operation
    else Failure
        WeatherRoute-->>Client: Return error with appropriate status code
    end
```

## Authentication Flow

```mermaid
sequenceDiagram
    participant Client
    participant AuthRoute
    participant Database
    participant PasswordUtil
    participant JWTConfig

    %% Registration Flow
    Client->>AuthRoute: POST /register with email & password
    AuthRoute->>AuthRoute: Validate email format
    AuthRoute->>AuthRoute: Validate password strength
    
    AuthRoute->>Database: Check if user exists
    Database-->>AuthRoute: Return user (if exists)
    
    alt User already exists
        AuthRoute-->>Client: Return 409 Conflict
    else User doesn't exist
        AuthRoute->>PasswordUtil: Hash password
        PasswordUtil-->>AuthRoute: Return password hash
        
        AuthRoute->>Database: Create user
        Database-->>AuthRoute: Acknowledge creation
        
        alt Success
            AuthRoute-->>Client: Return 201 Created
        else Failure
            AuthRoute-->>Client: Return error with appropriate status code
        end
    end

    %% Login Flow
    Client->>AuthRoute: POST /login with email & password
    AuthRoute->>Database: Find user by email
    Database-->>AuthRoute: Return user (if found)
    
    alt User not found
        AuthRoute-->>Client: Return 401 Unauthorized
    else User found
        AuthRoute->>PasswordUtil: Verify password
        PasswordUtil-->>AuthRoute: Return verification result
        
        alt Password incorrect
            AuthRoute-->>Client: Return 401 Unauthorized
        else Password correct
            AuthRoute->>AuthRoute: Check if user is active
            
            alt User inactive
                AuthRoute-->>Client: Return 403 Forbidden
            else User active
                AuthRoute->>JWTConfig: Generate JWT token
                JWTConfig-->>AuthRoute: Return JWT token
                
                AuthRoute-->>Client: Return token with 200 OK
            end
        end
    end
```

## Feedback Submission Flow

```mermaid
sequenceDiagram
    participant Client
    participant FeedbackRoute
    participant Database

    Client->>FeedbackRoute: POST /feedback with feedback data
    FeedbackRoute->>FeedbackRoute: Validate required fields
    
    alt Validation fails
        FeedbackRoute-->>Client: Return 400 Bad Request
    else Validation succeeds
        FeedbackRoute->>Database: Save feedback
        Database-->>FeedbackRoute: Return feedback ID
        
        alt Success
            FeedbackRoute-->>Client: Return feedback ID with 201 Created
        else Failure
            FeedbackRoute-->>Client: Return error with appropriate status code
        end
    end
```

These diagrams illustrate the key data flows in the application, showing how data moves between
different components and the decision points in each process.