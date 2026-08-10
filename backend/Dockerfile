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
WORKDIR /app
COPY --from=build /build/smartsoc-api/target/smartsoc-api-*.jar app.jar

# Truststore dedie SOC (ADR-015, livrable prevu de la phase 1.2, construit
# ici) : les certificats de l'API Wazuh, de son Indexer et de MISP sont
# signes par des autorites internes au labo, absentes du magasin public de
# la JVM -- sans ca, tout appel live echoue en PKIX path building failed.
# Part du cacerts par defaut (conserve la confiance publique, ex.
# VirusTotal) plutot que de le remplacer, n'y ajoute que ces 3 certificats.
COPY docker/truststore/*.pem /tmp/truststore/
RUN cp "$JAVA_HOME/lib/security/cacerts" /app/truststore.p12 \
    && for cert in /tmp/truststore/*.pem; do \
         alias=$(basename "$cert" .pem); \
         keytool -importcert -noprompt -trustcacerts \
           -alias "smartsoc-$alias" -file "$cert" \
           -keystore /app/truststore.p12 -storepass changeit; \
       done \
    && rm -rf /tmp/truststore \
    && chown smartsoc:smartsoc /app/truststore.p12

USER smartsoc
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", \
    "-Djavax.net.ssl.trustStore=/app/truststore.p12", \
    "-Djavax.net.ssl.trustStorePassword=changeit", \
    "-jar", "app.jar"]
