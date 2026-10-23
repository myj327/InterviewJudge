# Fixed Docker CLI used by SandboxService through the mounted host socket.
FROM docker:29.7.2-cli AS docker-cli

# Stage 1: Build the application
FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

# Copy all pom.xml files first to leverage Docker cache
COPY DOJ-BE/pom.xml .
COPY DOJ-BE/common/pom.xml ./common/
COPY DOJ-BE/gateway-service/pom.xml ./gateway-service/
COPY DOJ-BE/problem-service/pom.xml ./problem-service/
COPY DOJ-BE/sandbox-service/pom.xml ./sandbox-service/
COPY DOJ-BE/submission-service/pom.xml ./submission-service/
COPY DOJ-BE/user-service/pom.xml ./user-service/

# Download all dependencies
RUN mvn dependency:go-offline

# Copy all source code
COPY DOJ-BE/common/src ./common/src
COPY DOJ-BE/sandbox-service/src ./sandbox-service/src

# Build the specific service
RUN mvn -f ./pom.xml -pl sandbox-service -am clean package -DskipTests
RUN ls -al ./sandbox-service/target

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre

# Only the CLI is required; avoid an unpinned Ubuntu docker.io package install.
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker

WORKDIR /app

# Copy the executable jar from the build stage
COPY --from=build /app/sandbox-service/target/sandbox-service.jar ./app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "./app.jar"]
