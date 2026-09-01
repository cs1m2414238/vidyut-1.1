# Multi-stage Dockerfile for Vidyut Spring Boot Domain API (Google Cloud Run ready)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B || true
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r vidyut && useradd -r -g vidyut vidyut
COPY --from=build /app/target/vidyut-backend-*.jar app.jar
USER vidyut:vidyut
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
