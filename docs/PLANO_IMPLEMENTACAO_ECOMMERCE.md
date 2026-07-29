# Plano total de implementação — FemStep Store

## 1. Objetivo

Transformar o repositório atual em um e-commerce de calçados femininos realmente
operacional, mantendo a stack existente:

- Angular no frontend;
- Spring Boot no backend;
- PostgreSQL como fonte de verdade;
- Flyway para evolução do banco;
- Docker/Nginx para execução local e publicação.

O resultado esperado é uma loja própria, inspirada na organização e na clareza
visual de grandes lojas de calçados, sem copiar marca, textos, fotos ou identidade
visual de terceiros.

## 2. Diagnóstico inicial do repositório

> Registro histórico: esta seção descreve o estado encontrado em 28/07/2026.
> O acompanhamento vigente está na seção "Progresso da implementação".

### O que já pode ser aproveitado

- Projeto Angular com home, catálogo, cards de produto e responsividade básica.
- Projeto Spring Boot com Spring Security, JWT, JPA, validação e OpenAPI.
- PostgreSQL e primeira migração Flyway.
- Entidades iniciais de usuário, papel, categoria, produto, variação e imagem.
- Cadastro e login básicos no backend.
- Serviço local de carrinho no frontend.
- Docker Compose com PostgreSQL, backend, frontend e proxy de `/api`.
- Pipeline de CI para build, testes e auditoria de dependências.

### Problemas que impedem a loja de funcionar hoje

| Área | Situação atual | Impacto |
| --- | --- | --- |
| Inicialização Angular | `main.ts` inicializa `App`, cujo `app.html` ainda contém o template de boas-vindas do Angular | Gera o grande espaço inicial visto na captura e deixa o layout real sem uso |
| Layout | `AppComponent` e `MainLayoutComponent` existem, mas não são inicializados | Cabeçalho, rodapé e navegação planejados não aparecem corretamente |
| Rotas | Só existem `/` e `/catalog`; produto, carrinho, login, perfil, pedidos e admin estão comentados | Links visíveis redirecionam para a home ou não funcionam |
| Links da home | O componente não importa `RouterLink` | O CTA “Ver Catálogo” não se comporta como navegação Angular |
| Produtos | `ProductService` entrega três produtos mockados após um atraso artificial | O catálogo não consulta o banco |
| Imagens | Os mocks apontam para arquivos que não existem | Cards caem no placeholder |
| Backend de catálogo | Não há controller ou service público de produtos/categorias | O frontend não tem API real para consumir |
| Carrinho | Fica somente em `localStorage` e não tem página própria | Não é persistente por usuário e não permite finalizar compra |
| Contador do carrinho | Está fixo em `0` no HTML | Não reflete os itens adicionados |
| Login | O serviço existe, mas não há telas ou rotas de login/cadastro | Autenticação não pode ser usada pela interface |
| Sessão | JWT fica em `sessionStorage`, sem refresh, revogação ou logout no servidor | Proteção insuficiente para uma loja publicada |
| Cadastro | O código exige `ROLE_CUSTOMER`, mas a migração não insere os papéis | Cadastro falha em um banco vazio |
| Erros de cadastro | E-mail/CPF duplicados geram `RuntimeException` | A API responde 500 em vez de 409 com mensagem adequada |
| Usuário atual | `/api/auth/me` devolve texto de TODO | Sessão não pode ser revalidada pelo servidor |
| Banco | Só modela catálogo e usuários | Não existem endereços, carrinhos, pedidos, itens, pagamentos, fretes ou movimentações de estoque |
| Vendas | Não há criação de pedido, pagamento, webhook, baixa/reserva de estoque ou histórico | Nenhuma venda real pode ocorrer |
| Administração | Não há telas nem APIs de gestão | Produtos, estoque e pedidos não podem ser operados |
| Backend tests | O Maven compila, mas informa “No tests to run” | Regras críticas não têm proteção contra regressões |

### Validação feita em 28/07/2026

- Frontend: 2 arquivos de teste e 4 testes aprovados; build Angular aprovado.
- Backend: build Maven aprovado, mas não há nenhum teste Java.
- O branch já tinha uma alteração local em `backend/pom.xml`; ela foi preservada.

## 2.1. Progresso da implementação

Atualizado em 29/07/2026 após validação local do fluxo completo.

| Fase | Estado | Entregue | Próximo aceite |
| --- | --- | --- | --- |
| 0 — Fundação visual | Quase concluída | Shell Angular, header, footer, rotas, home, catálogo, estados de carregamento e layout responsivo básico | Auditoria de acessibilidade, teclado e viewports |
| 1 — Banco e migrations | Quase concluída | Schema comercial, constraints, índices, sessões, carrinho, pedidos, pagamentos, estoque, cupons, auditoria e seed repetível | Ampliar catálogo demonstrativo e validar restauração em banco vazio |
| 2 — API de catálogo | Parcial avançada | DTOs, paginação, busca, filtros, ordenação, detalhe por slug, cache HTTP e operações administrativas básicas | CRUD completo de categorias, variantes e imagens |
| 3 — Loja visual | Quase concluída | Produtos reais, imagens locais, filtros, galeria, variantes, favoritos, SEO dinâmico, carrinho e preços em BRL | Sitemap dependente do domínio e cotação real de frete |
| 4 — Autenticação e conta | Parcial avançada | Cadastro, login, refresh rotativo, logout, `/me`, guards, perfil, endereços e ViaCEP | Verificação de e-mail e recuperação de senha após definir o provedor transacional |
| 5 — Carrinho, checkout e pedidos | Quase concluída | Carrinho visitante/autenticado, cupom, checkout, idempotência, reserva, pedido, cancelamento, histórico e concorrência validada | Sessão expirada no E2E e cenários reais do gateway |
| 6 — Pagamento e frete reais | Fundação pronta | `PaymentGateway`, `ShippingGateway`, adaptadores locais e outbox transacional de notificações | Credenciais sandbox, adaptadores reais, webhooks assinados, reconciliação e processador de e-mail |
| 7 — Administração | Parcial avançada | Dashboard, produtos, ativação, estoque, pedidos, status, rastreio, cupons e auditoria | Edição completa, imagens, categorias e exportação |
| 8 — Qualidade e operação | Parcial avançada | Docker, CI, Dependabot, readiness/liveness, request ID, headers de segurança, testes e runbook | E2E visual, carga, staging, restauração de backup e produção |

### Baseline validado

- Frontend: 4 arquivos de teste, 8 testes aprovados e build Angular/SSR aprovado.
- Backend: 11 testes de integração aprovados com PostgreSQL/Testcontainers.
- Fluxo local validado: cadastro, produto, variante, carrinho, cupom, checkout,
  pedido Pix sandbox e aprovação do pagamento.
- Execução local: PostgreSQL, API e frontend saudáveis via Docker Compose.
- Segurança local: refresh token em cookie `HttpOnly`, acesso curto via JWT,
  senha com BCrypt e rotas administrativas protegidas por papel.
- Operação local: migrations até V4, readiness com banco, CSP/headers de segurança,
  request ID e auditoria de dependências de produção sem vulnerabilidades.

### Ordem de execução vigente

1. estabilizar o MVP e cobrir os cenários críticos;
2. fechar lacunas essenciais das Fases 0–5;
3. completar as operações administrativas prioritárias;
4. implementar integrações externas atrás de contratos simples;
5. preparar homologação e produção sem compartilhar credenciais entre ambientes.

## 3. Escopo do MVP comercial

### Loja pública

- Home com barra promocional, cabeçalho, busca, categorias, destaques, benefícios
  da loja e rodapé.
- Catálogo paginado com busca, filtros e ordenação.
- Filtros por categoria, tamanho, cor, material, faixa de preço e disponibilidade.
- Página de produto com galeria, preço, parcelamento, descrição, tamanhos, cores,
  estoque e cálculo de frete.
- Favoritos para usuários autenticados.
- Carrinho lateral e página completa de carrinho.
- Cupom de desconto.
- Checkout dentro da loja.
- Pagamento por Pix e cartão.
- Tela de confirmação e acompanhamento do pedido.

### Conta do cliente

- Cadastro, login, logout e revalidação de sessão.
- Confirmação de e-mail.
- Recuperação e redefinição de senha.
- Perfil e endereços.
- Histórico e detalhe dos pedidos.
- Repetir compra quando ainda houver estoque.

### Painel administrativo

- Login restrito a `ROLE_ADMIN`.
- Dashboard simples de vendas, pedidos e baixo estoque.
- CRUD de categorias, produtos, variações, preços e imagens.
- Entrada/ajuste de estoque com histórico.
- Gestão de pedidos e atualização de expedição/rastreio.
- Gestão de cupons.
- Ativar/desativar produtos sem apagá-los.

### Fora do primeiro MVP

- Marketplace com múltiplos vendedores.
- Programa de pontos.
- Assinaturas.
- Aplicativo móvel nativo.
- Recomendação por inteligência artificial.
- ERP completo e emissão fiscal automática.
- Troca/devolução totalmente automatizada.

Esses itens podem entrar depois que o fluxo principal de venda estiver estável.

## 4. Direção visual e experiência

Usar como referência apenas os padrões de experiência observados na página
indicada:

- barra de benefício/promocional no topo;
- cabeçalho leve com busca, conta, favoritos e sacola;
- categorias visuais logo antes da listagem;
- breadcrumb;
- contador de produtos;
- filtros em painel lateral/drawer;
- ordenação por relevância, novidade, preço e desconto;
- grade de produtos com foto grande, preço, parcelamento, tamanhos e ação de compra;
- bastante espaço em branco, tipografia elegante e cores neutras/quentes.

### Identidade própria proposta

- Marca: FemStep Store.
- Paleta: off-white, areia, café, rosa queimado e grafite.
- Tipografia: título editorial e texto sem serifa legível.
- Fotografias com fundo uniforme e enquadramento consistente.
- Ícones de uma biblioteca acessível; não usar emojis como ícones finais.
- Mobile first, com alvos de toque adequados e filtros em drawer.
- Contraste, foco visível, navegação por teclado e textos alternativos.

## 5. Arquitetura alvo

```text
Navegador
   |
   v
Angular SSR/SPA servido por Nginx
   |
   +---- /api ------------------------+
                                      |
                                      v
                              Spring Boot API
                         +------------+-------------+
                         |            |             |
                         v            v             v
                    PostgreSQL   Armazenamento   Provedores
                                  de imagens     externos
                                                   |
                                  +----------------+----------------+
                                  |                |                |
                              Pagamento         Frete/CEP         E-mail
```

### Decisões técnicas

- Manter a arquitetura atual; não reescrever o projeto em outro framework.
- PostgreSQL será a fonte de verdade para usuários, catálogo, estoque e vendas.
- Carrinho anônimo pode começar no navegador, mas deve ser validado no servidor e
  mesclado ao carrinho persistente depois do login.
- O servidor sempre recalcula preço, desconto, frete e total; nunca confiar nos
  valores enviados pelo frontend.
- Imagens devem ficar em armazenamento de objetos compatível com S3; o banco
  guarda URL, ordem, descrição e metadados.
- Pagamento deve ficar atrás de uma interface `PaymentGateway`, para não espalhar
  código do provedor pelo domínio.
- Frete deve ficar atrás de uma interface `ShippingGateway`.

## 6. Modelo de dados

### Tabelas atuais a manter e evoluir

- `roles`
- `users`
- `user_roles`
- `categories`
- `products`
- `product_variants`
- `product_images`

### Alterações nas tabelas atuais

#### `users`

- normalizar e-mails em minúsculas;
- tornar CPF opcional até o checkout;
- armazenar CPF normalizado e protegido;
- adicionar `email_verified_at`;
- adicionar `last_login_at`;
- adicionar `deleted_at` para desativação lógica quando aplicável.

#### `products`

- adicionar `short_description`;
- adicionar `material`;
- adicionar `brand`;
- adicionar `weight_kg`, `height_cm`, `width_cm`, `length_cm`;
- adicionar `seo_title` e `seo_description`;
- considerar `published_at`;
- manter preço monetário como `NUMERIC`, nunca `double`.

#### `product_variants`

- adicionar `reserved_quantity`;
- impedir estoque negativo com constraint;
- validar tamanho e preço;
- criar índice para filtros por tamanho/cor/ativo;
- usar controle otimista de concorrência (`version`) ou atualização atômica.

### Novas tabelas obrigatórias

#### Conta e sessão

- `addresses`
- `auth_sessions` ou `refresh_tokens`
- `email_verification_tokens`
- `password_reset_tokens`

#### Compra

- `carts`
- `cart_items`
- `stock_reservations`
- `orders`
- `order_items`
- `order_status_history`
- `payments`
- `payment_events`
- `shipments`

#### Operação

- `inventory_movements`
- `coupons`
- `coupon_redemptions`
- `favorites`
- `audit_logs`
- `webhook_events`

### Dados que devem ser congelados no pedido

Mesmo que o produto ou endereço mude depois, o pedido deve preservar:

- nome, SKU, tamanho, cor e foto do item;
- preço unitário, desconto e quantidade;
- nome e documento do comprador usados na compra;
- endereço de entrega completo;
- serviço, valor e prazo estimado do frete;
- subtotal, desconto, frete e total;
- identificadores externos de pagamento e envio.

### Status

#### Pedido

`PENDING_PAYMENT -> PAID -> PROCESSING -> SHIPPED -> DELIVERED`

Saídas alternativas:

- `CANCELLED`
- `PAYMENT_FAILED`
- `REFUNDED`
- `PARTIALLY_REFUNDED`

#### Pagamento

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`
- `EXPIRED`
- `REFUNDED`
- `CHARGEBACK`

Cada mudança deve ser validada por uma máquina de estados, não por texto livre.

## 7. Contrato de API

Todos os endpoints devem usar DTOs; entidades JPA não serão devolvidas diretamente.

### Catálogo público

| Método | Endpoint | Função |
| --- | --- | --- |
| GET | `/api/public/categories` | Listar categorias ativas |
| GET | `/api/public/products` | Paginar, buscar, filtrar e ordenar |
| GET | `/api/public/products/{slug}` | Detalhe completo do produto |
| GET | `/api/public/products/featured` | Destaques da home |
| POST | `/api/public/shipping/quote` | Cotar frete a partir de CEP e itens |

Parâmetros de catálogo:

- `page`, `size`;
- `q`;
- `category`;
- `sizes`;
- `colors`;
- `materials`;
- `minPrice`, `maxPrice`;
- `inStock`;
- `sort`.

### Autenticação e conta

| Método | Endpoint | Função |
| --- | --- | --- |
| POST | `/api/auth/register` | Criar conta |
| POST | `/api/auth/login` | Iniciar sessão |
| POST | `/api/auth/refresh` | Renovar sessão |
| POST | `/api/auth/logout` | Revogar sessão |
| GET | `/api/auth/me` | Retornar usuário autenticado |
| POST | `/api/auth/verify-email` | Confirmar e-mail |
| POST | `/api/auth/forgot-password` | Solicitar redefinição |
| POST | `/api/auth/reset-password` | Redefinir senha |
| GET/POST/PUT/DELETE | `/api/account/addresses` | Gerenciar endereços |
| GET | `/api/account/orders` | Histórico de pedidos |
| GET | `/api/account/orders/{number}` | Detalhe de pedido |

### Carrinho e checkout

| Método | Endpoint | Função |
| --- | --- | --- |
| GET | `/api/cart` | Ler carrinho persistente |
| POST | `/api/cart/items` | Adicionar variante |
| PATCH | `/api/cart/items/{id}` | Alterar quantidade |
| DELETE | `/api/cart/items/{id}` | Remover item |
| POST | `/api/cart/merge` | Mesclar carrinho anônimo |
| POST | `/api/cart/coupon` | Aplicar cupom |
| POST | `/api/checkout/preview` | Recalcular e validar compra |
| POST | `/api/orders` | Criar pedido e reserva |
| POST | `/api/orders/{number}/payment` | Criar pagamento |

### Integrações

| Método | Endpoint | Função |
| --- | --- | --- |
| POST | `/api/webhooks/payments/mercado-pago` | Receber eventos assinados |
| POST | `/api/webhooks/shipping` | Receber atualização de entrega, se disponível |

### Administração

- `/api/admin/categories`
- `/api/admin/products`
- `/api/admin/products/{id}/variants`
- `/api/admin/inventory/movements`
- `/api/admin/orders`
- `/api/admin/orders/{id}/status`
- `/api/admin/shipments`
- `/api/admin/coupons`
- `/api/admin/reports/sales`

Listagens administrativas devem ser paginadas e auditadas.

## 8. Rotas do frontend

### Públicas

- `/`
- `/sapatos`
- `/categoria/:slug`
- `/produto/:slug`
- `/busca`
- `/carrinho`
- `/checkout`
- `/pedido/:number/confirmacao`
- `/entrar`
- `/cadastro`
- `/esqueci-minha-senha`
- `/redefinir-senha`
- `/privacidade`
- `/termos`
- `/trocas-e-devolucoes`

### Autenticadas

- `/minha-conta`
- `/minha-conta/dados`
- `/minha-conta/enderecos`
- `/minha-conta/pedidos`
- `/minha-conta/pedidos/:number`
- `/favoritos`

### Administrativas

- `/admin`
- `/admin/produtos`
- `/admin/produtos/novo`
- `/admin/produtos/:id`
- `/admin/estoque`
- `/admin/pedidos`
- `/admin/pedidos/:id`
- `/admin/cupons`

## 9. Fluxos críticos

### Adicionar ao carrinho

1. Cliente escolhe explicitamente tamanho e cor.
2. Frontend envia o `variantId`, não apenas o `productId`.
3. Backend valida se produto e variante estão ativos.
4. Backend limita quantidade ao estoque disponível.
5. Carrinho recebe o preço atual apenas para exibição.
6. Antes do checkout, todos os valores são recalculados.

Não adicionar automaticamente o primeiro tamanho/cor como o código atual faz.

### Checkout e estoque

1. Usuário faz login.
2. Informa ou seleciona endereço.
3. Backend calcula opções de frete.
4. Usuário escolhe o serviço.
5. Backend cria uma prévia com preços e cupom validados.
6. Ao criar o pedido, uma transação reserva o estoque por tempo limitado.
7. O pagamento é criado com chave de idempotência.
8. Pagamento aprovado confirma a baixa.
9. Pagamento rejeitado, cancelado ou expirado libera a reserva.
10. Repetições do mesmo webhook não duplicam baixa nem pedido.

### Pagamento

Recomendação inicial: Mercado Pago Checkout Transparente via Orders API, com Pix
e cartão. A documentação oficial atual recomenda a Orders API e exige chave de
idempotência; para cartão, o Card Payment Brick tokeniza os dados no navegador.

Regras:

- nunca armazenar número completo do cartão ou CVV;
- credencial privada somente no backend;
- validar assinatura do webhook;
- consultar o provedor antes de aceitar uma mudança crítica de status;
- guardar payload mínimo necessário e mascarar dados sensíveis;
- começar em sandbox e usar contas/cartões de teste;
- a página de retorno não confirma pagamento sozinha: o webhook/consulta confirma.

Referências:

- https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/overview
- https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/cards
- https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/pix
- https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks

### Frete e CEP

Recomendação inicial:

- ViaCEP para preenchimento assistido do endereço;
- Melhor Envio para cotação por peso/dimensões, inicialmente em sandbox;
- persistir no pedido a cotação escolhida;
- permitir uma tabela de frete fixa como fallback administrativo;
- não impedir checkout apenas porque o serviço de CEP está indisponível: permitir
  preenchimento manual validado.

Referências:

- https://viacep.com.br/
- https://docs.melhorenvio.com.br/docs/cotacao-de-fretes
- https://docs.melhorenvio.com.br/reference/calculo-de-fretes-por-produtos

## 10. Segurança e privacidade

### Autenticação

- Trocar o JWT duradouro em `sessionStorage`.
- Preferir access token curto em memória e refresh token rotativo em cookie
  `HttpOnly`, `Secure` e `SameSite`, armazenando apenas o hash da sessão no banco.
- Revogar refresh token no logout e em troca de senha.
- Proteger operações por papel no servidor; guards Angular são apenas UX.
- Implementar CSRF no modelo escolhido.
- Rate limit em login, cadastro, recuperação de senha e checkout.
- Mensagens de login e recuperação não devem revelar se um e-mail existe.
- Senha com hash forte e política mínima; considerar Argon2id ou BCrypt com custo
  revisado.
- E-mail verificado antes de operações sensíveis.

### Proteção do domínio

- DTOs e validação para todos os inputs.
- Transações de banco nas operações de pedido/estoque.
- Lock otimista ou atualização atômica contra overselling.
- Chaves de idempotência em criação de pedido e pagamento.
- Assinatura e deduplicação de webhooks.
- CORS com origens exatas.
- Headers de segurança no Nginx.
- Segredos apenas em variáveis de ambiente/secret manager.
- Logs sem senha, token, CPF completo ou dados de cartão.
- Backups criptografados e restauração testada.

### LGPD

Nome, CPF, endereço, e-mail, telefone e IP são dados pessoais. Portanto:

- coletar somente o necessário para vender e entregar;
- registrar finalidade e base legal;
- criar política de privacidade e prazo de retenção;
- oferecer correção, exportação e solicitação de exclusão quando aplicável;
- anonimizar dados analíticos;
- restringir acesso administrativo e manter trilha de auditoria;
- definir procedimento de incidente.

Referência geral:

- https://www.gov.br/governodigital/pt-br/privacidade-e-seguranca/guias/guia_lgpd.pdf/view

## 11. Plano por fases

### Fase 0 — Corrigir a fundação visual (1–2 dias)

- Remover o template padrão de `app.html`.
- Inicializar um único componente raiz com header, conteúdo e footer.
- Corrigir imports de `RouterLink`.
- Criar tokens globais de cor, tipografia, espaçamento e breakpoints.
- Definir layout mobile/desktop.
- Substituir emojis por ícones acessíveis.
- Criar estados de loading, vazio e erro.

Aceite:

- não existe espaço/template Angular antes da loja;
- header e footer aparecem em todas as páginas;
- home e catálogo funcionam em 360 px, tablet e desktop;
- navegação por links funciona sem recarregar a página.

### Fase 1 — Banco, migrations e dados iniciais (2–4 dias)

- Criar migration para semear `ROLE_CUSTOMER` e `ROLE_ADMIN`.
- Criar novas tabelas de sessão, endereço, carrinho, pedido, pagamento e estoque.
- Adicionar constraints, índices e status.
- Criar seed de desenvolvimento com 20–30 calçados próprios/de demonstração.
- Criar usuário admin somente por comando seguro ou variável de bootstrap.
- Preparar armazenamento de imagens.

Aceite:

- banco vazio sobe sem erro;
- cadastro de cliente funciona;
- seed é repetível e não duplica dados;
- schema é validado pelo Hibernate;
- estoque não aceita valor negativo.

### Fase 2 — API real de catálogo (4–6 dias)

- DTOs e mapeadores.
- Services de categoria, produto, variante e imagem.
- Endpoints públicos paginados.
- Busca, filtros e ordenação no banco.
- Endpoint por slug.
- Cache HTTP para catálogo quando seguro.
- CRUD administrativo de catálogo.
- Upload de imagens com validação de tipo/tamanho.

Aceite:

- frontend não contém mocks de produto;
- filtro e paginação não carregam todo o catálogo na memória;
- produto inativo não aparece publicamente;
- detalhe mostra apenas variantes ativas e estoque disponível.

### Fase 3 — Loja visual e catálogo completo (5–7 dias)

- Home inspirada na referência, com identidade FemStep.
- Header com busca, conta, favoritos e contador reativo da sacola.
- Página de categoria com breadcrumb, chips visuais, filtros e ordenação.
- Card com foto, preço BRL, desconto, parcelamento e tamanhos.
- Página de produto com galeria, seleção de variante e frete.
- SEO: títulos, descrições, canonical, sitemap e dados estruturados de produto.
- Imagens responsivas e lazy loading.

Aceite:

- nenhum produto usa imagem ausente em produção;
- preço usa formatação `pt-BR`;
- produto sem tamanho escolhido não entra no carrinho;
- URLs são compartilháveis e preservam filtros importantes.

### Fase 4 — Autenticação e conta (3–5 dias)

- Corrigir contrato e erros de cadastro/login.
- Implementar sessão segura, refresh, logout e `/me`.
- Criar telas de entrar/cadastrar.
- Verificação de e-mail e recuperação de senha.
- Perfil e CRUD de endereços.
- Guard de cliente e guard de admin.
- Integrar ViaCEP com fallback manual.

Aceite:

- sessão expirada é renovada ou leva ao login sem loop;
- logout revoga a sessão;
- cliente não acessa admin;
- admin não é criado por cadastro público;
- duplicidade retorna 409 e validação retorna 400.

### Fase 5 — Carrinho, checkout e pedidos (5–7 dias)

- Carrinho persistente para usuário.
- Carrinho anônimo e merge no login.
- Página/drawer de carrinho.
- Cupom.
- Checkout em etapas: identificação, endereço, frete, pagamento e revisão.
- Criação transacional de pedido.
- Snapshot de itens/endereço/frete.
- Reserva e liberação de estoque.
- Histórico e detalhe de pedidos.

Aceite:

- manipular preço no navegador não altera o total do servidor;
- duas compras concorrentes não vendem além do estoque;
- atualizar estoque invalida quantidades impossíveis no carrinho;
- pedido recebe número público não sequencial/previsível;
- repetir a requisição não duplica pedido.

### Fase 6 — Pagamento e frete reais (5–8 dias)

- Adaptador Mercado Pago.
- Pix com QR Code e copia-e-cola.
- Cartão tokenizado pelo SDK/Brick.
- Idempotência e webhooks assinados.
- Estados de pagamento e reconciliação.
- Adaptador Melhor Envio para cotação.
- Salvar serviço/prazo/preço selecionado.
- E-mails de pedido criado, pagamento aprovado e envio.

Aceite:

- fluxo completo aprovado em sandbox;
- Pix pendente vira aprovado via webhook;
- webhook repetido não altera estoque duas vezes;
- cartão nunca passa em texto puro pelo backend;
- indisponibilidade externa gera erro recuperável, não pedido inconsistente.

### Fase 7 — Painel administrativo (5–8 dias)

- Dashboard.
- CRUD de catálogo e upload/ordenação de fotos.
- Variações por tamanho/cor/SKU.
- Movimentação e alerta de estoque.
- Gestão de pedidos e envio.
- Cupons com período, limite, valor mínimo e quantidade de usos.
- Exportação CSV de pedidos sem dados desnecessários.
- Auditoria de ações críticas.

Aceite:

- toda alteração de estoque registra motivo e administrador;
- preço/estoque podem ser atualizados sem SQL manual;
- ações administrativas são autorizadas no backend;
- produto com pedido histórico não é apagado fisicamente.

### Fase 8 — Qualidade, publicação e operação (4–7 dias)

- Testes automatizados descritos na seção seguinte.
- Revisão de acessibilidade e responsividade.
- Teste de carga leve nos endpoints de catálogo e checkout.
- Observabilidade, alertas e rastreamento de erros.
- Ambientes separados: local, homologação e produção.
- Banco gerenciado, HTTPS, domínio, backup e storage de imagens.
- Checklist de homologação do gateway.
- Smoke test depois do deploy.

Aceite:

- CI bloqueia merge com teste/build falhando;
- migrations são aplicadas antes da nova aplicação receber tráfego;
- restauração de backup foi testada;
- health/readiness distinguem aplicação viva de dependências prontas;
- compra real de valor controlado é validada ponta a ponta antes do lançamento.

## 12. Estratégia de testes

### Backend

- Unitários: preço, cupom, status, estoque e regras de autorização.
- Integração com Testcontainers/PostgreSQL: repositories e migrations.
- API: autenticação, catálogo, carrinho, pedido e admin.
- Concorrência: duas compras da última unidade.
- Contrato do gateway: respostas aprovadas, pendentes, rejeitadas e timeout.
- Webhook: assinatura inválida, replay e evento fora de ordem.

### Frontend

- Unitários: serviços, interceptors, guards e cálculo apenas visual.
- Componentes: seletor de tamanho, card, carrinho, formulários e estados de erro.
- E2E: cadastro, login, filtro, produto, carrinho, checkout sandbox e histórico.
- Acessibilidade automatizada e revisão manual por teclado/leitor.
- Viewports mobile, tablet e desktop.

### Casos mínimos antes da produção

- produto sem estoque;
- estoque muda durante o checkout;
- cupom expirado/limite atingido;
- CEP inválido;
- frete indisponível;
- pagamento Pix expirado;
- cartão rejeitado;
- clique duplo em “Pagar”;
- webhook duplicado e fora de ordem;
- sessão expira durante o checkout;
- pedido cancelado e estoque liberado.

## 13. Publicação e operação

### Ambientes

- `local`: Docker Compose e provedores sandbox.
- `staging`: domínio de homologação, banco isolado e credenciais de teste.
- `production`: banco, storage e credenciais totalmente separados.

### Componentes de produção

- frontend/Nginx em container;
- API Spring Boot em container;
- PostgreSQL gerenciado com backup automático;
- armazenamento de objetos com CDN;
- secret manager;
- serviço transacional de e-mail;
- HTTPS obrigatório;
- monitoramento de disponibilidade e erros.

### Observabilidade

- logs estruturados com `requestId`, `orderId` e `paymentId`;
- métricas de latência, erro, checkout, aprovação e estoque;
- alertas de falha de webhook, fila acumulada, erro 5xx e banco indisponível;
- auditoria separada de logs técnicos;
- retenção sem dados pessoais desnecessários.

## 14. Estimativa

Para uma pessoa desenvolvedora trabalhando em tempo integral:

- MVP demonstrável sem pagamento/frete real: aproximadamente 4–6 semanas;
- MVP comercial com sandbox, admin, testes e deploy: aproximadamente 8–12 semanas;
- produção madura com políticas, homologação, observabilidade e ajustes pós-teste:
  aproximadamente 10–14 semanas.

Estimativa total: 34–54 dias úteis de desenvolvimento, dependendo da qualidade e
quantidade de fotos/dados, regras de frete, aprovação de contas externas e nível
de acabamento do painel administrativo.

## 15. Dependências do responsável pela loja

Antes de produção serão necessários:

- logo e identidade visual própria;
- fotos autorizadas e informações reais dos produtos;
- SKUs, tamanhos, cores, preços, pesos e dimensões;
- endereço/CEP de origem;
- regras de troca, devolução, frete grátis e atendimento;
- conta empresarial e credenciais de produção do gateway de pagamento;
- conta e configuração do serviço de frete;
- domínio e e-mail transacional;
- textos jurídicos revisados para privacidade, termos e trocas;
- definição de quem terá acesso administrativo.

## 16. Premissas adotadas

Para evitar indefinição, este plano assume:

- compra exige login no MVP;
- Pix e cartão via Mercado Pago;
- cotação de frete via Melhor Envio;
- ViaCEP apenas auxilia o preenchimento;
- PostgreSQL continua sendo o banco;
- painel administrativo faz parte do MVP;
- fotos e catálogo de terceiros não serão copiados;
- emissão fiscal automática fica para a próxima etapa.

Essas escolhas podem ser trocadas antes das Fases 5 e 6 sem reescrever o restante,
desde que os adaptadores de pagamento e frete sejam respeitados.

## 17. Definição de “loja funcional”

A FemStep só será considerada funcional quando:

- um cliente conseguir criar conta e entrar;
- produtos reais vierem do banco;
- tamanho/cor/estoque forem validados;
- carrinho sobreviver à sessão e puder ser editado;
- endereço e frete forem calculados;
- pedido for criado com total calculado no servidor;
- pagamento sandbox for aprovado e confirmado por webhook;
- estoque for reservado/baixado sem duplicidade;
- cliente enxergar o pedido;
- admin conseguir cadastrar produto, ajustar estoque e operar o pedido;
- testes cobrirem os caminhos financeiros e de concorrência;
- homologação e produção usarem segredos e bancos separados.

## 18. Ordem recomendada para começar

O primeiro incremento deve executar somente as Fases 0, 1 e 2:

1. corrigir a raiz Angular e o layout;
2. criar migrations/seed confiáveis;
3. expor o catálogo real pela API;
4. substituir os mocks no frontend.

Isso transforma rapidamente a captura atual em uma loja navegável conectada ao
banco, sem fingir que pagamento e pedidos estão prontos. Depois, avançar em
fatias verticais: conta, carrinho/pedido, pagamento/frete, admin e produção.
