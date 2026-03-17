FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src
RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/saas-subscription-tracker-backend /app
EXPOSE 8080
ENTRYPOINT ["/app/bin/saas-subscription-tracker-backend"]
