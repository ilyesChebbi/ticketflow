# Étape 1 : compiler le projet (Maven + JDK 17)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Le dossier contracts/proto est nécessaire pour générer le code gRPC
COPY contracts/proto contracts/proto
COPY seat-inventory-service/pom.xml seat-inventory-service/pom.xml
COPY seat-inventory-service/src seat-inventory-service/src
WORKDIR /build/seat-inventory-service
RUN mvn -q -DskipTests package

# Étape 2 : image finale légère (JRE seulement)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/seat-inventory-service/target/*.jar app.jar
EXPOSE 8083 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]