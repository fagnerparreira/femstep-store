# FemStep Store

Mini e-commerce full stack de calçados femininos, com catálogo, autenticação,
carrinho persistente, checkout, pedidos, pagamento sandbox, controle de estoque
e painel administrativo.

## O que está pronto

- Home, catálogo responsivo, busca, filtros, ordenação e paginação
- Página de produto com imagens, tamanho, cor e estoque por variação
- Cadastro, login JWT, refresh token rotativo em cookie HttpOnly e logout
- Carrinho de visitante no navegador e carrinho autenticado no PostgreSQL
- Migração automática do carrinho de visitante depois do login
- Checkout com endereço, frete, cupom, idempotência e validação de preços no servidor
- PIX e cartão em ambiente sandbox, sem coleta de dados bancários reais
- Reserva de estoque, expiração automática, baixa após pagamento e cancelamento
- Histórico e detalhe dos pedidos
- Cadastro e gerenciamento de endereços, com preenchimento opcional pelo ViaCEP
- Favoritos persistentes e perfil editável
- Painel administrativo com métricas, produtos, variações, estoque, pedidos e cupons
- Contratos isolados para pagamento e frete, com adaptadores locais substituíveis
- Outbox transacional para notificações de pedido, pagamento e envio
- Catálogo inicial com 12 produtos, 6 categorias, imagens e cupom `BEMVINDA10`
- Migrações Flyway e teste de integração completo com PostgreSQL/Testcontainers

## Tecnologias

- Frontend: Angular 21, TypeScript, RxJS e Vitest
- Backend: Java 21, Spring Boot 3.5, Spring Security, JPA e JDBC
- Banco: PostgreSQL 15 e Flyway
- Infraestrutura: Docker Compose e Nginx

## Executar com Docker

1. Copie `.env.example` para `.env`.
2. Preencha `DB_PASSWORD` e `JWT_SECRET` com valores aleatórios fortes.
3. Para testar compras localmente, use:

```dotenv
COOKIE_SECURE=false
PAYMENTS_SANDBOX_ENABLED=true
```

4. Para criar o primeiro administrador, informe:

```dotenv
ADMIN_EMAIL=admin@seudominio.com.br
ADMIN_PASSWORD=uma-senha-unica-com-12-ou-mais-caracteres
```

5. Suba a aplicação:

```bash
docker compose up --build
```

Endereços locais:

- Loja: `http://localhost:4200`
- API: `http://localhost:8080`
- Health check: `http://localhost:8080/api/health`
- Swagger local, quando habilitado: `http://localhost:8080/swagger-ui.html`

O Nginx do frontend encaminha automaticamente as chamadas em `/api` para o
backend.

## Desenvolvimento

Frontend:

```bash
cd frontend
npm ci
npm start
```

Backend, com PostgreSQL disponível:

```bash
cd backend
mvn spring-boot:run
```

Validação completa:

```bash
cd frontend
npm run check

cd ../backend
mvn test
```

O teste do backend usa Testcontainers e precisa de Docker em execução.

## Fluxo de compra sandbox

1. Crie uma conta.
2. Escolha um produto, tamanho e cor.
3. Abra a sacola e prossiga ao checkout.
4. Informe o endereço e, se quiser, use `BEMVINDA10`.
5. Escolha:
   - `PIX sandbox`: cria um pedido pendente e um código demonstrativo; na página
     do pedido, use “Simular pagamento aprovado”.
   - `Cartão de teste`: aprova imediatamente, sem solicitar número de cartão.

O servidor recalcula preço, desconto e frete, bloqueia a variação durante a
compra e só reduz o estoque depois da aprovação.

## Painel administrativo

Configure `ADMIN_EMAIL` e `ADMIN_PASSWORD`, entre com essa conta e abra
`/admin`. O painel permite:

- acompanhar receita, pedidos e alertas de estoque;
- cadastrar produtos com tamanhos, cores e estoque;
- ativar ou desativar produtos;
- ajustar o estoque por SKU;
- mover pedidos pagos para preparação, envio e entrega;
- registrar código de rastreio no envio.
- criar, editar, ativar e desativar cupons sem apagar o histórico.

## Produção

Antes de vender de verdade:

- mantenha `COOKIE_SECURE=true` e use HTTPS;
- mantenha `PAYMENTS_SANDBOX_ENABLED=false`;
- substitua o gateway sandbox por uma conta de pagamento real e webhooks
  assinados do provedor;
- integre uma cotação real de frete;
- use um serviço transacional de e-mail;
- armazene segredos fora do Git;
- configure backup, monitoramento e política de privacidade/LGPD.

O sandbox entregue é totalmente funcional para desenvolvimento e homologação,
mas não movimenta dinheiro real.

### Limites conscientes do ambiente local

- `SandboxPaymentGateway` não aceita dados bancários reais e falha de forma segura
  quando `PAYMENTS_SANDBOX_ENABLED=false`.
- `FixedShippingGateway` calcula apenas uma estimativa local. Um provedor real deve
  implementar `ShippingGateway`.
- eventos de e-mail são gravados em `notification_outbox` na mesma transação do
  pedido. O envio externo depende da escolha e das credenciais do serviço
  transacional; nenhum token ou segredo é embutido no código.
- ativação de Mercado Pago, Melhor Envio e e-mail exige contas sandbox próprias,
  CEP de origem e credenciais fornecidas por variáveis/secret manager.

O planejamento técnico completo está em
[`docs/PLANO_IMPLEMENTACAO_ECOMMERCE.md`](docs/PLANO_IMPLEMENTACAO_ECOMMERCE.md).

## Licença

[MIT](LICENSE)
