# MOVAZU — Frontend

Aplicação Angular 21 responsiva para a loja MOVAZU.

## Funcionalidades

- home, catálogo, filtros e detalhes de produto;
- autenticação e conta;
- sacola de visitante e carrinho persistente;
- checkout, pedidos e pagamento sandbox;
- endereços;
- painel administrativo.

## Comandos

```bash
npm ci
npm start
npm run test:ci
npm run build
npm run check
```

Durante o desenvolvimento, `proxy.conf.json` encaminha `/api` para
`http://localhost:8080`. No Docker, o Nginx faz o mesmo encaminhamento para o
serviço `backend`.
