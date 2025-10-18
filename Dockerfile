# Multi-stage build for Cripto Monitor
FROM clojure:temurin-17-tools-deps-alpine AS builder

WORKDIR /app

# Copy dependency files first for better caching
COPY deps.edn build.clj ./

# Download dependencies
RUN clojure -P -M:build

# Copy source code
COPY src ./src
COPY resources ./resources

# Build uberjar
RUN clojure -T:build uberjar



# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy the built jar
COPY --from=builder /app/target/cripto-monitor-1.0.0-standalone.jar ./cripto-monitor.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:3000/api/health || exit 1

# Start the application
CMD ["java", "-jar", "cripto-monitor.jar"]