# ── Etapa 1: build Angular ────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# Cachea node_modules: solo se reinstala si package.json cambia
COPY package*.json ./
RUN npm ci --silent

# Compila la app en modo producción
COPY . .
RUN npm run build -- --configuration production

# ── Etapa 2: servir con nginx ─────────────────────────────────────────────
FROM nginx:alpine

# Copia el build al directorio de nginx
COPY --from=build /app/dist/votaciones-frontend /usr/share/nginx/html

# Configuración nginx para SPA (Angular routing)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
