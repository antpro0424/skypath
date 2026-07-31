# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so editing source does not re-download
# the world on every rebuild.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are run by the developer and by CI, not during an image build: a reviewer
# running "docker compose up --build" wants the application, not a test report.
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------- runtime
# The Ubuntu-based tag rather than -alpine: Temurin publishes Alpine images for
# amd64 only, which would leave arm64 machines emulating or failing outright.
FROM eclipse-temurin:17-jre AS runtime

# curl is here solely for the container health check.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system skypath && useradd --system --gid skypath --no-create-home skypath

WORKDIR /app
COPY --from=build /build/target/flight-search-*.jar app.jar
USER skypath

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
