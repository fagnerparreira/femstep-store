# Documentação do FemStep Store

Esta pasta registra o estado real e as próximas decisões do projeto.

## Arquitetura atual

```text
Navegador
   │
   ▼
Nginx / Angular ── /api ──► Spring Boot ──► PostgreSQL
```

- O frontend pode ser executado como aplicação Angular ou servido pelo Nginx.
- O Nginx encaminha `/api` para o serviço `backend` no Docker Compose.
- O backend controla autenticação, persistência e migrações.
- O PostgreSQL mantém os dados em um volume local.

## Decisões pendentes

1. Definir o contrato da API pública de produtos.
2. Criar DTOs que não exponham entidades JPA.
3. Adicionar paginação, busca e filtros no backend.
4. Implementar testes de integração com Testcontainers.
5. Definir provedor de pagamento somente após o fluxo de pedidos.

## Princípio de manutenção

A documentação deve descrever apenas arquivos e funcionalidades existentes.
Novos documentos serão adicionados junto com a implementação correspondente.
