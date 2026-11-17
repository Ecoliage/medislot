FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
FROM eclipse-temurin:25-jre
WORKDIR /app
ADD --chmod=644 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.1/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
COPY --from=build /src/target/medislot.jar /app/app.jar
# A new named volume copies this directory, owner included: the app can write its logos.
RUN mkdir -p /data/logos && chown 10001 /data/logos
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
