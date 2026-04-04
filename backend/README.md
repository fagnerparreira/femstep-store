# FemStep Store - Backend

Spring Boot 3.x REST API with Java 21.

## Features

- RESTful API architecture
- JWT authentication and authorization
- PostgreSQL database with Flyway migrations
- Comprehensive error handling
- Input validation and sanitization

## Development

```bash
# Run application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Build JAR
./mvnw clean package

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Architecture

- **Config**: Security, database, and web configuration
- **Controller**: REST endpoints
- **Service**: Business logic layer
- **Repository**: Data access layer with Spring Data JPA
- **Domain**: JPA entities
- **DTO**: Data transfer objects
- **Mapper**: Entity ↔ DTO conversion
- **Security**: JWT implementation
- **Exception**: Global exception handling

## Database

- PostgreSQL as primary database
- Flyway for schema migrations
- Connection pooling with HikariCP

## Profiles

- `dev`: Development environment
- `prod`: Production environment
- `test`: Test environment