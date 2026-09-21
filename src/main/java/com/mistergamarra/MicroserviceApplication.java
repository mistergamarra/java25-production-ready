package com.mistergamarra;

import module java.base;
import com.mistergamarra.config.Datasource;
import com.mistergamarra.config.Logger;
import com.mistergamarra.config.Otel;
import com.mistergamarra.grpc.OrderServiceGrpc;
import com.mistergamarra.grpc.OrderServiceImpl;
import io.grpc.Server;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.protobuf.services.HealthStatusManager;
import io.opentelemetry.api.trace.Tracer;


public class MicroserviceApplication {

    private static HikariDataSource dataSource;
    private static Tracer tracer;

    static void main() throws Exception {

        // Initialize async log engine structure immediately
        Logger.init();
        Logger.info("Starting production microservice context application initialization...", "Main");

        loadDotEnv();

        // 1. Conditionally bootstrap OpenTelemetry based on environment variable toggle
        Otel.initializeOpenTelemetryGlobalContext();


        // 1. Establish the high-performance PostgreSQL Connection Pool
        HikariConfig config = Datasource.getHikariConfig();
        dataSource = new HikariDataSource(config);


        // 3. Initialize your Loom-based gRPC Netty Server (Safe now that DB schema is matching)
        Logger.info("Database migrations applied. Starting network engine...", "Main");

        HealthStatusManager healthManager = new HealthStatusManager();

        // 2. Build the optimized, lightweight gRPC Server instance
        int port = 8080;
        Server server = NettyServerBuilder.forPort(port)
                // Register our low-overhead functional logic layer
                .addService(new OrderServiceImpl(dataSource))
                .addService(healthManager.getHealthService())
                // Core Loom configuration: Process network payloads entirely on Virtual Threads
                .executor(Executors.newVirtualThreadPerTaskExecutor())

                // Production tuning parameters to reduce keep-alive network syscall pollution
                .keepAliveTime(5, TimeUnit.MINUTES)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .maxConnectionIdle(1, TimeUnit.HOURS)
                .build();

        server.start();
        healthManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
        healthManager.setStatus(OrderServiceGrpc.SERVICE_NAME, HealthCheckResponse.ServingStatus.SERVING);
        Logger.info("Production gRPC Server listening on port " + port, "Main");

        // Standard clean JVM shutdown hook hookups
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Initiating graceful shutdown procedures...", "Main");
            server.shutdown();
            if (dataSource != null) {
                dataSource.close();
            }
        }));

        server.awaitTermination();
    }

    private static void loadDotEnv() {
        java.io.File envFile = new java.io.File(".env");
        if (!envFile.exists()) {
            // Silently skip if the file isn't present (e.g., inside production Docker networks)
            return;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and code comment blocks
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIdx = line.indexOf('=');
                if (separatorIdx > 0) {
                    String key = line.substring(0, separatorIdx).trim();
                    String value = line.substring(separatorIdx + 1).trim();

                    // Strip optional bounding quotes if present around string values
                    if (value.startsWith("\"") && value.endsWith("\"") ||
                            value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    // Inject the variable into System properties for simple lookup fallbacks
                    System.setProperty(key, value);
                }
            }
            Logger.info("Successfully loaded local configurations from .env file.", "Main");
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse local .env file: " + e.getMessage());
        }
    }

    // Optimized parameter lookup wrapper utility method
    public static String getEnvOrProperty(String key, String defaultValue) {
        // Check actual OS environment variable definitions first (Docker context)
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            // Fall back to our parsed system property definitions (.env local context)
            value = System.getProperties().getProperty(key);
        }
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

}
