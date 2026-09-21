package com.mistergamarra.grpc;

import com.mistergamarra.client.PromoClient;
import com.mistergamarra.config.Logger;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import java.util.UUID;


public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    private final HikariDataSource dataSource;
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("com.mistergamarra.OrderService");

    public OrderServiceImpl(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String[] ORDER_TYPES = {"DELIVERY", "PICKUP", "DINE_IN"};
    //private static final String[] PROMOS = {"FREE_DELIVERY", "BURGER_FEST", "DISCOUNT_10", "", ""};
    // Inside your OrderServiceImpl class context
    private static final String[] PROMOS = {
            "FREE_DELIVERY", "BURGER_FEST", "DISCOUNT_10", "", "",
            "FAIL_500", "DELAY_250", "BAD_REQUEST", "DROP_CONNECTION"
    };
    private static final String[] DEVICES = {"IOS", "ANDROID", "WEB"};
    private static final String[] PAYMENTS = {"CREDIT_CARD", "APPLE_PAY", "CASH"};
    private static final String[] STATUSES = {"PENDING", "PROCESSING", "COMPLETED"};

    @Override
    public void createOrder(CreateOrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        long startTime = System.nanoTime();
        Span span = tracer.spanBuilder("CreateOrder").startSpan();

        String generatedOrderId = UUID.randomUUID().toString();
        var random = java.util.concurrent.ThreadLocalRandom.current();
        String dynamicStatus = STATUSES[random.nextInt(STATUSES.length)];

        // Generate 10 dynamic business fields per request sequence
        String randomUserId = "usr_" + random.nextInt(100000, 999999);
        String randomRestId = "rest_" + random.nextInt(1000, 9999);
        String randomOrderType = ORDER_TYPES[random.nextInt(ORDER_TYPES.length)];

        String randomPromo = PROMOS[random.nextInt(PROMOS.length)];

        // --- FIX: Invoke your Java 25 featured HTTP Client to validate the promo code out-of-band ---
        boolean isPromoValid = PromoClient.validatePromoCode(randomPromo);

        double itemsTotal = Math.round((8.50 + (120.00 * random.nextDouble())) * 100.0) / 100.0;
        // Apply a 15% discount if the mock API validates the promo successfully
        if (isPromoValid) {
            itemsTotal = Math.round((itemsTotal * 0.85) * 100.0) / 100.0;
        }

        double deliveryFee = "DELIVERY".equals(randomOrderType) ? (Math.round((1.99 + (5.00 * random.nextDouble())) * 100.0) / 100.0) : 0.0;
        double finalTotal = Math.round((itemsTotal + deliveryFee) * 100.0) / 100.0;


        String lat = String.format("%.6f", -11.9 + (0.2 * random.nextDouble()));
        String lon = String.format("%.6f", -77.0 + (0.2 * random.nextDouble()));
        String randomOS = DEVICES[random.nextInt(DEVICES.length)];
        String randomPayment = PAYMENTS[random.nextInt(PAYMENTS.length)];

        try (var scope = span.makeCurrent();
             var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     "INSERT INTO client_orders (id, status, total, user_id, restaurant_id, order_type, delivery_fee, promo_code, delivery_latitude, longitude, device_os, payment_method) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            stmt.setString(1, generatedOrderId);
            stmt.setString(2, dynamicStatus);
            stmt.setDouble(3, finalTotal);
            stmt.setString(4, randomUserId);
            stmt.setString(5, randomRestId);
            stmt.setString(6, randomOrderType);
            stmt.setDouble(7, deliveryFee);
            stmt.setString(8, randomPromo);
            stmt.setString(9, lat);
            stmt.setString(10, lon);
            stmt.setString(11, randomOS);
            stmt.setString(12, randomPayment);
            stmt.executeUpdate();

            OrderResponse response = OrderResponse.newBuilder()
                    .setOrderId(generatedOrderId)
                    .setStatus(dynamicStatus)
                    .setTotal(finalTotal)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            Logger.info("Restaurant Order " + generatedOrderId + " created successfully in " + durationMs + "ms", "CRUD");

        } catch (Exception e) {
            span.recordException(e);
            Logger.error("Failed to commit restaurant transaction", "CRUD", e);
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        } finally {
            span.end();
        }
    }

    @Override
    public void getOrder(OrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        Span span = tracer.spanBuilder("GetOrder").startSpan();

        try (var scope = span.makeCurrent();
             var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT status, total FROM client_orders WHERE id = ?")) {

            stmt.setString(1, request.getOrderId());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    OrderResponse response = OrderResponse.newBuilder()
                            .setOrderId(request.getOrderId())
                            .setStatus(rs.getString("status"))
                            .setTotal(rs.getDouble("total"))
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } else {
                    responseObserver.onError(Status.NOT_FOUND.withDescription("Order not found").asRuntimeException());
                }
            }
        } catch (Exception e) {
            span.recordException(e);
            Logger.error("Database query error on GetOrder", "CRUD", e);
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        } finally {
            span.end();
        }
    }

    @Override
    public void updateOrder(UpdateOrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        Span span = tracer.spanBuilder("UpdateOrder").startSpan();
        double finalTotal = Math.round((request.getItemsTotal() + request.getDeliveryFee()) * 100.0) / 100.0;

        try (var scope = span.makeCurrent();
             var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     "UPDATE client_orders SET status = ?, order_type = ?, delivery_fee = ?, promo_code = ?, total = ? WHERE id = ?")) {

            stmt.setString(1, request.getStatus());
            stmt.setString(2, request.getOrderType());
            stmt.setDouble(3, request.getDeliveryFee());
            stmt.setString(4, request.getPromoCode());
            stmt.setDouble(5, finalTotal);
            stmt.setString(6, request.getOrderId());

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                OrderResponse response = OrderResponse.newBuilder()
                        .setOrderId(request.getOrderId())
                        .setStatus(request.getStatus())
                        .setTotal(finalTotal)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                Logger.info("Restaurant Order " + request.getOrderId() + " updated successfully", "CRUD");
            } else {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Order to update not found").asRuntimeException());
            }
        } catch (Exception e) {
            span.recordException(e);
            Logger.error("Database update error on UpdateOrder", "CRUD", e);
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        } finally {
            span.end();
        }
    }

    @Override
    public void deleteOrder(OrderRequest request, StreamObserver<DeleteOrderResponse> responseObserver) {
        Span span = tracer.spanBuilder("DeleteOrder").startSpan();

        try (var scope = span.makeCurrent();
             var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("DELETE FROM client_orders WHERE id = ?")) {

            stmt.setString(1, request.getOrderId());
            int rowsDeleted = stmt.executeUpdate();

            DeleteOrderResponse response = DeleteOrderResponse.newBuilder()
                    .setOrderId(request.getOrderId())
                    .setSuccess(rowsDeleted > 0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            Logger.info("Restaurant Order " + request.getOrderId() + " deleted successfully", "CRUD");

        } catch (Exception e) {
            span.recordException(e);
            Logger.error("Database deletion error on DeleteOrder", "CRUD", e);
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        } finally {
            span.end();
        }
    }
}