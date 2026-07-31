# ============================================================
# SmartSOC — image unique : Spring Boot sert l'API ET l'UI React
# (aucun Nginx ; Cloudflare Tunnel expose ce seul service :8080).
# Contexte de build = racine du dépôt (accès à backend/ et frontend/).
#
# Étape 1 : build du frontend React (Node 24) -> dist/
# Étape 2 : build Maven (JDK 21), le dist React est copié dans les
#           ressources statiques de smartsoc-api avant le package
# Étape 3 : runtime minimal (JRE 21 Alpine), utilisateur non-root
# ============================================================

FROM node:24-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Couche dependances : ne s'invalide que si un pom.xml change
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
COPY backend/smartsoc-domain/pom.xml smartsoc-domain/
COPY backend/smartsoc-application/pom.xml smartsoc-application/
COPY backend/smartsoc-infrastructure/pom.xml smartsoc-infrastructure/
COPY backend/smartsoc-api/pom.xml smartsoc-api/
RUN ./mvnw -B -ntp dependency:go-offline -DexcludeGroupIds=com.smartsoc || true

# Couche sources : les tests tournent dans la CI, pas dans le build d'image
COPY backend/smartsoc-domain/src smartsoc-domain/src
COPY backend/smartsoc-application/src smartsoc-application/src
COPY backend/smartsoc-infrastructure/src smartsoc-infrastructure/src
COPY backend/smartsoc-api/src smartsoc-api/src

# Le build React devient une ressource statique servie par Spring Boot
COPY --from=frontend /fe/dist/ smartsoc-api/src/main/resources/static/
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# postgresql18-client fournit pg_dump, utilisé par la console Paramètres
# (sauvegarde reelle de la base, cf. PgDumpBackupAdapter) -- version alignee
# sur l'image postgres:18-alpine du docker-compose.
RUN apk add --no-cache postgresql18-client
RUN addgroup -S smartsoc && adduser -S smartsoc -G smartsoc
USER smartsoc
WORKDIR /app
COPY --from=build /build/smartsoc-api/target/smartsoc-api-*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
