# FemStep Store

E-commerce platform for fashion and accessories built with modern full-stack technologies.

## Tech Stack

- **Frontend**: Angular 21 with Standalone Components
- **Backend**: Spring Boot 3.x with Java 21
- **Database**: PostgreSQL with Flyway migrations
- **Authentication**: JWT tokens
- **Containerization**: Docker & Docker Compose

## Project Structure

```
femstep-store/
├── frontend/          # Angular application
├── backend/           # Spring Boot API
├── docs/             # Project documentation
└── docker-compose.yml
```

## Quick Start

1. Clone the repository
2. Copy `.env.example` to `.env` and configure variables
3. Run `docker-compose up`
4. Access the application at `http://localhost:4200`

### Required security configuration

Production startup requires `DB_PASSWORD`, `JWT_SECRET`, and
`CORS_ALLOWED_ORIGINS`. Copy `.env.example` to `.env`, generate unique secrets,
and never commit the resulting `.env` file.

- Use at least 32 random bytes for `JWT_SECRET` (for example,
  `openssl rand -base64 48`).
- Use a different high-entropy value for `DB_PASSWORD`.
- Set `CORS_ALLOWED_ORIGINS` to the exact frontend origins, separated by commas.
- Keep `SPRINGDOC_ENABLED=false` in production unless the API documentation is
  intentionally exposed.

## Development

### Frontend
```bash
cd frontend
npm install
npm start
```

### Backend
```bash
cd backend
./mvnw spring-boot:run
```

## Documentation

See `/docs` folder for detailed documentation.

## License

MIT License
