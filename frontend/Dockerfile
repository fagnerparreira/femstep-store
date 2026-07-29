FROM node:22-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/femstep-store-frontend/browser /usr/share/nginx/html
COPY --from=build /workspace/dist/femstep-store-frontend/browser/index.csr.html /usr/share/nginx/html/index.html

EXPOSE 80
