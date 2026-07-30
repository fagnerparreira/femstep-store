# Operação da MOVAZU

## Ambientes

- `local`: Docker Compose, pagamento sandbox e frete fixo.
- `staging`: banco e credenciais de teste exclusivos; nunca reutilizar dados de
  produção.
- `production`: HTTPS, banco gerenciado, secret manager e sandbox desativado.

Cada ambiente precisa de banco, `JWT_SECRET`, conta administrativa e credenciais
externas próprios.

O modelo versionado [.env.staging.example](../.env.staging.example) lista apenas
os nomes necessários. Copie-os para o secret manager da hospedagem; nunca crie
um `.env` de staging dentro do repositório.

## Checklist de staging

1. criar domínio HTTPS de homologação e banco PostgreSQL exclusivo;
2. gerar `DB_PASSWORD`, `JWT_SECRET` e senha administrativa exclusivos;
3. configurar `CORS_ALLOWED_ORIGINS` e `PUBLIC_APP_URL` com o domínio HTTPS;
4. manter `PAYMENTS_SANDBOX_ENABLED=true`;
5. configurar SMTP de teste com remetente/domínio verificados;
6. executar CI, backup, migrations e readiness;
7. testar cadastro, verificação de e-mail, recuperação de senha, catálogo,
   carrinho, checkout sandbox, pagamento, pedido e painel administrativo;
8. confirmar que cookies têm `Secure`, que a API não aceita origem indevida e
   que a outbox não acumula eventos em `FAILED`.

## Verificações de saúde

- liveness: `/actuator/health/liveness`;
- readiness: `/actuator/health/readiness`;
- diagnóstico local simples: `/api/health`.

O balanceador deve usar readiness. Ela inclui a conexão com o PostgreSQL e impede
que uma instância sem banco receba tráfego.

## Publicação

1. executar frontend tests/build e backend `verify`;
2. gerar imagens imutáveis identificadas pelo commit;
3. fazer backup do banco;
4. aplicar migrations Flyway com uma única instância;
5. iniciar a nova versão sem remover a anterior;
6. aguardar readiness;
7. executar smoke test de catálogo, login e checkout sandbox;
8. direcionar o tráfego;
9. observar erros, latência e pedidos por pelo menos 15 minutos.

Rollback de aplicação deve reutilizar a imagem anterior. Não reverta migrations
destrutivamente: faça uma migration corretiva compatível.

## Backup e restauração

Exemplo de backup lógico:

```bash
pg_dump --format=custom --no-owner --file=femstep.dump "$DATABASE_URL"
```

Teste a restauração em um banco isolado, nunca sobre produção:

```bash
createdb femstep_restore_test
pg_restore --clean --if-exists --no-owner \
  --dbname=femstep_restore_test femstep.dump
```

Depois da restauração, valide Flyway, contagem de pedidos, itens, pagamentos,
estoque e integridade das chaves estrangeiras. O arquivo de backup deve ser
criptografado, ter retenção definida e acesso restrito.

## Observabilidade e dados sensíveis

- toda resposta da API recebe `X-Request-Id`;
- logs incluem `requestId`; use também `orderId` e `paymentId` nos adaptadores;
- não registrar senha, JWT, refresh token, CPF completo, cartão ou payload bruto
  de webhook;
- alertar para aumento de 5xx, readiness indisponível, falhas de webhook,
  notificações em `FAILED` e reservas expiradas acumuladas;
- acompanhar `api_rate_limits` para picos anormais sem registrar IP bruto;
- limitar a retenção de logs e auditoria conforme a necessidade operacional.

## Integrações externas pendentes

Para ativar venda real ainda são obrigatórios:

- credenciais sandbox e produção do provedor de pagamento;
- segredo e validação oficial de assinatura de webhook;
- conta do serviço de frete, CEP de origem, pesos e dimensões reais;
- serviço transacional de e-mail e domínio remetente verificado;
- domínio, certificado HTTPS e URLs públicas de callback.

O processador da `notification_outbox` já possui claim concorrente,
recuperação de mensagens travadas, backoff e estado `FAILED` após cinco
tentativas. O Mailpit é somente local; não o exponha publicamente.

Esses valores devem entrar por secret manager ou variáveis de ambiente. Não
devem ser versionados em `.env`, documentação, imagens Docker ou logs.
