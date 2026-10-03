# ==========================================
# Stage 1: Build & Package
# ==========================================
FROM docker.io/eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# Copy Maven wrapper and root POM
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copy module POMs for dependency caching
COPY ed-iam-core/pom.xml ed-iam-core/
COPY ed-iam-resource-server/pom.xml ed-iam-resource-server/
COPY ed-iam-auth/pom.xml ed-iam-auth/
COPY ed-iam-management/pom.xml ed-iam-management/
COPY ed-iam-starter/pom.xml ed-iam-starter/
COPY samples/ed-iam-playground/pom.xml samples/ed-iam-playground/

# Copy all module sources
COPY ed-iam-core/src/ ed-iam-core/src/
COPY ed-iam-resource-server/src/ ed-iam-resource-server/src/
COPY ed-iam-auth/src/ ed-iam-auth/src/
COPY ed-iam-management/src/ ed-iam-management/src/
COPY ed-iam-starter/src/ ed-iam-starter/src/
COPY samples/ed-iam-playground/src/ samples/ed-iam-playground/src/

# Package playground fat JAR
RUN ./mvnw clean package -B -DskipTests -pl samples/ed-iam-playground -am

# Extract Spring Boot layers for efficient container caching
WORKDIR /workspace/extracted
RUN java -Djarmode=tools -jar /workspace/samples/ed-iam-playground/target/ed-iam-playground-*.jar \
    extract --layers --launcher --destination /workspace/extracted

# ==========================================
# Stage 2: Hardened Production Runtime
# ==========================================
FROM docker.io/eclipse-temurin:25-jre AS runner

# Install curl for container health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

# Copy extracted layers in order of change frequency
COPY --from=builder --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /workspace/extracted/application/ ./

USER spring:spring

EXPOSE 8080

ENV PORT=8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f "http://localhost:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
