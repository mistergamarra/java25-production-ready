-- V1__initial_schema.sql
CREATE TABLE client_orders (
                               id VARCHAR(255) PRIMARY KEY,
                               status VARCHAR(50) NOT NULL,
                               total DOUBLE PRECISION NOT NULL,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
