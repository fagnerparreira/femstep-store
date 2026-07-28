# FemStep Store — Backend

API Spring Boot 3.5 com Java 21.

## Funcionalidades atuais

- Cadastro e autenticação com JWT
- Spring Security
- Validação de entrada e tratamento global de erros
- PostgreSQL e migrações Flyway
- Entidades e repositórios do catálogo
- Health check em `GET /api/health`
- OpenAPI/Swagger configurável

Os endpoints públicos de catálogo, pedidos e administração ainda fazem parte do
roadmap.

## Executar

Requer Java 21, Maven 3.6.3 ou superior e PostgreSQL.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Testar e empacotar

```bash
mvn verify
mvn clean package
```

## Configuração

As credenciais e os segredos são recebidos por variáveis de ambiente. Consulte
o `.env.example` da raiz. Nunca use os valores de exemplo em produção.

## Camadas existentes

- `config`: segurança e OpenAPI
- `controller`: autenticação e health check
- `service`: autenticação e emissão de tokens
- `repository`: acesso a usuários e catálogo
- `domain`: entidades JPA
- `dto`: contratos de autenticação
- `security`: filtro e serviços JWT
- `exception`: respostas de erro
