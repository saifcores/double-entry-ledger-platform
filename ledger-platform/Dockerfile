# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml .
COPY ledger-domain/pom.xml ledger-domain/
COPY ledger-persistence/pom.xml ledger-persistence/
COPY ledger-core/pom.xml ledger-core/
COPY ledger-platform/pom.xml ledger-platform/
COPY ledger-domain/src ledger-domain/src
COPY ledger-persistence/src ledger-persistence/src
COPY ledger-core/src ledger-core/src
COPY ledger-platform/src ledger-platform/src
RUN mvn -q -pl ledger-platform -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /src/ledger-platform/target/ledger-platform-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
