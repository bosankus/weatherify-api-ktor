# Architecture Diagram

```mermaid
graph TD
    Client[Client] --> |HTTP Requests| Server[Ktor Server]
    
    %% Main Application Components
    Server --> Routing[Routing]
    Server --> Authentication[Authentication]
    Server --> HTTP[HTTP Configuration]
    Server --> Monitoring[Monitoring]
    
    %% Routes
    Routing --> HomeRoute[Home Route]
    Routing --> WeatherRoute[Weather Route]
    Routing --> FeedbackRoute[Feedback Route]
    Routing --> AuthRoute[Auth Route]
    
    %% Weather Components
    WeatherRoute --> WeatherCache[Weather Cache]
    WeatherCache --> |Fetch Data| ExternalAPI[External Weather API]
    WeatherRoute --> |Save Data| Database[(MongoDB)]
    
    %% Auth Components
    AuthRoute --> |User Operations| Database
    AuthRoute --> |Generate Token| JWTConfig[JWT Configuration]
    Authentication --> |Verify Token| JWTConfig
    
    %% Feedback Components
    FeedbackRoute --> |CRUD Operations| Database
    
    %% Environment & Configuration
    Environment[Environment Config] --> WeatherCache
    Environment --> JWTConfig
    SecretManager[Secret Manager] --> WeatherCache
    SecretManager --> JWTConfig
    
    %% Legend
    classDef core fill:#f9f,stroke:#333,stroke-width:2px;
    classDef route fill:#bbf,stroke:#333,stroke-width:1px;
    classDef external fill:#bfb,stroke:#333,stroke-width:1px;
    classDef data fill:#fbb,stroke:#333,stroke-width:1px;
    
    class Server,Routing,Authentication,HTTP,Monitoring core;
    class HomeRoute,WeatherRoute,FeedbackRoute,AuthRoute route;
    class ExternalAPI external;
    class Database,Environment,SecretManager,JWTConfig,WeatherCache data;
```

This architecture diagram illustrates the main components of the Weatherify API and their
relationships:

## Core Components

- **Ktor Server**: The main application server that handles HTTP requests
- **Routing**: Configures the API endpoints
- **Authentication**: Handles JWT authentication
- **HTTP Configuration**: Sets up content negotiation, headers, etc.
- **Monitoring**: Configures logging and monitoring

## Routes

- **Home Route**: Serves the interactive HTML documentation
- **Weather Route**: Provides weather and air pollution data
- **Feedback Route**: Manages user feedback
- **Auth Route**: Handles user registration and login

## Data Sources

- **MongoDB**: Stores user data, weather data, and feedback
- **External Weather API**: Provides weather and air pollution data
- **Secret Manager**: Securely stores API keys and secrets
- **Environment Config**: Provides configuration values from environment variables

## Key Interactions

1. Client sends HTTP requests to the Ktor Server
2. Routing directs requests to the appropriate route handlers
3. Authentication verifies JWT tokens for protected routes
4. Weather Route fetches data from External API via Weather Cache
5. Auth Route generates JWT tokens for authenticated users
6. Data is stored in and retrieved from MongoDB