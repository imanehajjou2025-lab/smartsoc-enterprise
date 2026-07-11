# ============================================================
# SmartSOC Frontend - build multi-stage
# Etape 1 : build Vite (Node 24)
# Etape 2 : nginx unprivileged (non-root, port 8080)
# ============================================================

FROM node:24-alpine AS build
WORKDIR /build

# Couche dependances : ne s'invalide que si les manifests changent
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 8080
# 127.0.0.1 explicite : dans le conteneur, localhost se resout en ::1
# alors que nginx n'ecoute qu'en IPv4 -> healthcheck faussement rouge.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ > /dev/null || exit 1
