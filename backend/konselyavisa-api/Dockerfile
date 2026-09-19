FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S konselya && adduser -S konselya -G konselya \
    && apk add --no-cache wget
COPY --from=build /app/target/konselyavisa-api-*.jar app.jar
USER konselya
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
