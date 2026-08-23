FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY platform-contracts platform-contracts
COPY gateway-service gateway-service
COPY event-service event-service
COPY reservation-service reservation-service
COPY booking-service booking-service
COPY payment-service payment-service
ARG SERVICE
RUN mvn -B -pl ${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 seatsync
WORKDIR /app
ARG SERVICE
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-XX:+ExitOnOutOfMemoryError","-jar","app.jar"]
