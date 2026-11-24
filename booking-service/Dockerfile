# Contexte de build = racine du dépôt (le pom lit ../contracts/proto et ../contracts/xsd)
# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY contracts contracts
COPY booking-service/pom.xml booking-service/pom.xml
WORKDIR /build/booking-service
RUN mvn -q -B dependency:go-offline
COPY booking-service/src src
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /build/booking-service/target/*.jar app.jar
USER app
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=3s --retries=10 \
  CMD wget -qO- http://localhost:8082/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
