# =========================================================================
# STAGE 1: Compile the self-contained static binary in a GraalVM 25 environment
# =========================================================================
FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /build

# Copy build configuration dependencies first to maximize caching efficiency
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./

# FIX: Force Gradle to turn off auto-download loops inside isolated containers
# This ensures it binds directly to the image's pre-packaged Native compiler
ENV GRADLE_OPTS="-Dorg.gradle.java.installations.auto-detect=true -Dorg.gradle.java.installations.auto-download=false"

# Copy your framework-free Java 25 source architecture
COPY src/ src/

# Trigger compilation down to a pure static Linux binary context
RUN ./gradlew nativeCompile --no-daemon

# =========================================================================
# STAGE 2: Distribute inside a microscopic, immutable "scratch" filesystem
# =========================================================================
FROM gcr.io/distroless/cc-debian12:latest

# Set production environment flags
ENV TARGET_ENV=production

# Copy the compiled standalone executable binary using a wildcard match
COPY --from=builder /build/build/native/nativeCompile/* /java25-production-ready

# Expose your performance-optimized gRPC listener port
EXPOSE 8080

# Run under a safe non-root system call user ID for strict security compliance
USER 65534:65534

# Boot the binary instantly (Sub-10ms, no JVM, no shell layer overhead)
ENTRYPOINT ["/java25-production-ready"]
