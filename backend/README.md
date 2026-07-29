# FemStep Store — Backend

API Java 21 com Spring Boot 3.5 e PostgreSQL.

## Módulos

- autenticação JWT com sessão de refresh rotativa;
- catálogo público e estoque por variação;
- carrinho persistente;
- endereços da conta;
- checkout transacional, cupom e frete;
- pedidos, pagamentos sandbox, cancelamento e expiração de reserva;
- painel administrativo, inventário, rastreio e auditoria;
- migrações Flyway e catálogo inicial.

## Executar

Defina as variáveis descritas no `.env.example` da raiz e execute:

```bash
mvn spring-boot:run
```

## Testes

```bash
mvn test
```

O teste de integração inicia um PostgreSQL 15 descartável com Testcontainers,
aplica todas as migrações e executa cadastro, carrinho, cupom, checkout,
pagamento e baixa de estoque.

## Principais APIs

- `/api/public/*`: catálogo
- `/api/auth/*`: autenticação
- `/api/cart`: carrinho autenticado
- `/api/checkout/preview`: simulação final
- `/api/orders`: checkout e pedidos
- `/api/account/addresses`: endereços
- `/api/admin/*`: operação administrativa

As rotas administrativas exigem `ROLE_ADMIN`.
