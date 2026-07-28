# FemStep Store

E-commerce full stack em evolução, criado para demonstrar integração entre
Angular, Spring Boot, PostgreSQL, autenticação JWT e execução com contêineres.

## Estado atual

### Disponível

- Home e catálogo responsivos em Angular
- Catálogo demonstrativo com filtros e busca
- Carrinho local com controle de estoque e persistência no navegador
- Cadastro e login no backend com JWT
- Modelo relacional para usuários, categorias, produtos, variações e imagens
- Migrações de banco com Flyway
- Health check e documentação OpenAPI
- Build, testes e auditoria de produção automatizados no GitHub Actions
- Imagens Docker para frontend e backend

### Em desenvolvimento

- API pública de produtos conectada ao catálogo
- Tela de detalhes do produto
- Fluxo completo do carrinho e checkout
- Pedidos, pagamentos e painel administrativo
- Testes de integração do backend com PostgreSQL

O repositório representa um produto em construção. Funcionalidades ainda não
implementadas não são apresentadas como prontas.

## Tecnologias

- **Frontend:** Angular 21, TypeScript, RxJS e Vitest
- **Backend:** Java 21, Spring Boot 3.5, Spring Security e Spring Data JPA
- **Dados:** PostgreSQL 15 e Flyway
- **Infraestrutura:** Docker Compose e Nginx
- **Qualidade:** GitHub Actions, testes automatizados e auditoria de dependências

## Estrutura

```text
femstep-store/
├── backend/          API Spring Boot
├── frontend/         Aplicação Angular
├── docs/             Estado e decisões do projeto
└── docker-compose.yml
```

## Início rápido com Docker

1. Copie `.env.example` para `.env`.
2. Substitua todos os valores de exemplo por segredos únicos.
3. Execute:

```bash
docker compose up --build
```

Serviços locais:

- Frontend: `http://localhost:4200`
- Backend: `http://localhost:8080`
- Health check: `http://localhost:8080/api/health`

O frontend usa o Nginx para encaminhar chamadas em `/api` ao backend.

## Desenvolvimento sem Docker

### Frontend

Requer Node.js 22.12 ou superior.

```bash
cd frontend
npm ci
npm start
```

Verificações:

```bash
npm run check
npm audit --omit=dev
```

### Backend

Requer Java 21 e Maven 3.6.3 ou superior.

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Verificações:

```bash
mvn verify
```

## Configuração segura

As variáveis abaixo devem ser definidas antes de subir o ambiente:

- `DB_PASSWORD`: senha exclusiva para o PostgreSQL
- `JWT_SECRET`: segredo aleatório com pelo menos 32 bytes
- `CORS_ALLOWED_ORIGINS`: origens exatas autorizadas a acessar a API

O arquivo `.env` é ignorado pelo Git e nunca deve ser publicado.

## Atualizações de versão

Angular 21 permanece em suporte ativo e Spring Boot 3.5 é a base recomendada
antes de uma migração para Spring Boot 4. As atualizações maiores devem ser
feitas separadamente, com testes de regressão, em vez de apenas alterar números
de versão.

## Licença

[MIT](LICENSE)
