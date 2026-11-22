# Étape 1 : compiler le projet (Maven + JDK 17)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Le dossier contracts/proto est nécessaire pour générer le code gRPC
COPY contracts/proto contracts/proto
COPY pricing-service/pom.xml pricing-service/pom.xml
COPY pricing-service/src pricing-service/src
WORKDIR /build/pricing-service
RUN mvn -q -DskipTests package

# Étape 2 : image finale légère (JRE seulement)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/pricing-service/target/*.jar app.jar
EXPOSE 8084 9091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]