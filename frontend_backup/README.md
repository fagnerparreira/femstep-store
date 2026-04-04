# FemStep Store - Frontend

Angular 20 application with standalone components architecture.

## Features

- Modern e-commerce interface
- Responsive design
- JWT authentication
- Shopping cart functionality
- Product catalog and search

## Development

```bash
# Install dependencies
npm install

# Start development server
npm start

# Build for production
npm run build

# Run tests
npm test
```

## Architecture

- **Core**: Singleton services, guards, interceptors
- **Shared**: Reusable components and pipes
- **Layout**: Header, footer, sidebar components
- **Features**: Business logic modules (home, catalog, cart, auth, etc.)

## Environment Variables

Copy `environments/environment.ts` and configure API endpoints.