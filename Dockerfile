# Multi-stage build for Spring Boot application on Render
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# Copy build descriptors and maven wrapper
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Build application package skipping tests for fast deployment
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre
LABEL maintainer="Mohammad Saadati"
WORKDIR /app

# Copy the built artifact from builder stage
COPY --from=builder /workspace/target/library-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT dynamically (defaults to 10000 on Render, 8079 locally)
ENV PORT=8079
EXPOSE 8079 10000

# Tuned JVM parameters for Render Free Tier (512MB RAM, 0.1 CPU):
# -XX:+UseSerialGC: lowers GC memory overhead compared to G1
# -Xms64m -Xmx280m: caps heap memory to prevent container OOM
# -XX:MaxMetaspaceSize=128m: prevents metaspace from expanding unbounded
ENTRYPOINT ["sh", "-c", "java -XX:+UseSerialGC -Xms64m -Xmx280m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -Dserver.port=${PORT:-8079} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
