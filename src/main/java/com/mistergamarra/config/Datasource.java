package com.mistergamarra.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import static com.mistergamarra.MicroserviceApplication.getEnvOrProperty;

public class Datasource {

    public static HikariConfig getHikariConfig() throws InterruptedException {
        HikariConfig config = new HikariConfig();
        // config.setJdbcUrl("jdbc:postgresql://localhost:5432/java25-production-ready-db");
        config.setJdbcUrl(getEnvOrProperty("DB_URL", "jdbc:postgresql://localhost:5432/java25-production-ready-db?sslmode=disable"));
        config.setUsername(getEnvOrProperty("DB_USER", "postgres"));
        config.setPassword(getEnvOrProperty("DB_PASSWORD", "password1"));

        // Match min/max pool sizes to eliminate connection creation syscalls at runtime
        config.setMaximumPoolSize(16);
        config.setMinimumIdle(16);
        config.setConnectionTimeout(3000);

        // Retry strategy loop
        boolean connected = false;
        int retries = 5;
        while (!connected && retries > 0) {
            try {
                HikariDataSource dataSource = new HikariDataSource(config);
                // Test connection
                try (var conn = dataSource.getConnection()) {
                    connected = true;
                    Logger.info("Database connection successfully established.", "Database");
                }
            } catch (Exception e) {
                retries--;
                Logger.warn("Database connection rejected. Retrying in 2 seconds... Remaining retries: " + retries, "Database");
                Thread.sleep(2000);
            }
        }

        if (!connected) {
            Logger.error("Could not bind to PostgreSQL pool infrastructure. Aborting boot.", "Main", null);
            System.exit(1);
        }

        return config;
    }


}
