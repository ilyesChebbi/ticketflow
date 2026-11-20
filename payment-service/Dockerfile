# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /build/target/*.jar app.jar
USER app
EXPOSE 8085
HEALTHCHECK --interval=15s --timeout=3s --retries=10 \
  CMD wget -qO- http://localhost:8085/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
