# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests need Docker (Testcontainers) and run in CI / locally via ./mvnw verify.
RUN mvn -B -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app \
    && mkdir -p /app/data/uploads && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/tourist-permit-*.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
