# ============================================================
# SmartSOC Backend - build multi-stage
# Etape 1 : build Maven (JDK 21) avec cache des dependances
# Etape 2 : runtime minimal (JRE 21 Alpine), utilisateur non-root
# ============================================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Couche dependances : ne s'invalide que si un pom.xml change
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY smartsoc-domain/pom.xml smartsoc-domain/
COPY smartsoc-application/pom.xml smartsoc-application/
COPY smartsoc-infrastructure/pom.xml smartsoc-infrastructure/
COPY smartsoc-api/pom.xml smartsoc-api/
RUN ./mvnw -B -ntp dependency:go-offline -DexcludeGroupIds=com.smartsoc || true

# Couche sources : les tests tournent dans la CI, pas dans le build d'image
COPY smartsoc-domain/src smartsoc-domain/src
COPY smartsoc-application/src smartsoc-application/src
COPY smartsoc-infrastructure/src smartsoc-infrastructure/src
COPY smartsoc-api/src smartsoc-api/src
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S smartsoc && adduser -S smartsoc -G smartsoc
USER smartsoc
WORKDIR /app
COPY --from=build /build/smartsoc-api/target/smartsoc-api-*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
