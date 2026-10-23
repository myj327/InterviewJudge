FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends time \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# SandboxService passes the complete timed command as one image argument.
ENTRYPOINT ["sh", "-c"]
