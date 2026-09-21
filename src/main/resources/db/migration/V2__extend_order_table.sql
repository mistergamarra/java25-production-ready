ALTER TABLE client_orders
    ADD COLUMN user_id VARCHAR(255),
    ADD COLUMN restaurant_id VARCHAR(255),
    ADD COLUMN order_type VARCHAR(50),
    ADD COLUMN delivery_fee DOUBLE PRECISION,
    ADD COLUMN promo_code VARCHAR(50),
    ADD COLUMN delivery_latitude VARCHAR(50),
    ADD COLUMN longitude VARCHAR(50),
    ADD COLUMN device_os VARCHAR(50),
    ADD COLUMN payment_method VARCHAR(50);
