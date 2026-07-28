# FemStep Store — Frontend

Aplicação Angular do FemStep Store.

## Requisitos

- Node.js 22.12 ou superior
- npm 11

## Comandos

```bash
npm ci          # instala exatamente as dependências do lockfile
npm start       # servidor de desenvolvimento
npm run test:ci # testes sem modo interativo
npm run build   # build de produção
npm run check   # testes e build
```

## Funcionalidades atuais

- Home e catálogo carregados sob demanda
- Filtros e busca sobre o catálogo demonstrativo
- Carrinho local com persistência, limite de estoque e suporte a SSR
- Componentes standalone
- Renderização no servidor e prerender das rotas públicas

## Próxima integração

O catálogo ainda usa dados demonstrativos. A próxima etapa funcional é expor a
API de produtos no backend e substituir o conteúdo mockado do `ProductService`.
