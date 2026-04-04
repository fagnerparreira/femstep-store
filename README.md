# FemStep Store

E-commerce platform for fashion and accessories built with modern full-stack technologies.

## Tech Stack

- **Frontend**: Angular 20 with Standalone Components
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