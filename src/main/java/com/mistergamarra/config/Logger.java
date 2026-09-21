package com.mistergamarra.config;

import module java.base;

public final class Logger {

    private static final java.util.concurrent.BlockingQueue<String> logQueue = new java.util.concurrent.ArrayBlockingQueue<>(10000);
    private static final String SERVICE_NAME = "java25-production-ready";
    private static boolean isInitialized = false;

    public static synchronized void init() {
        if (isInitialized) return;

        Thread.ofPlatform().daemon().name("async-log-drain").start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String logLine = logQueue.take();
                    System.out.println(logLine);
                    // FIX: Forces the standard output block array to flush out context data immediately
                    System.out.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        isInitialized = true;
    }

    public static void info(String message, String component) { enqueue("INFO", message, component, null); }
    public static void warn(String message, String component) { enqueue("WARN", message, component, null); }
    public static void error(String message, String component, Throwable throwable) { enqueue("ERROR", message, component, throwable); }
    public static void debug(String message, String component) {
        String env = System.getenv("TARGET_ENV");
        if ("production".equalsIgnoreCase(env)) return;
        enqueue("DEBUG", message, component, null);
    }

    private static void enqueue(String level, String message, String component, Throwable throwable) {
        if (!isInitialized) init();

        String timestamp = java.time.Instant.now().toString();
        String threadName = Thread.currentThread().getName();
        String sanitizedMessage = message != null ? message.replace("\"", "\\\"").replace("\n", " ") : "";

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{")
                .append("\"timestamp\":\"").append(timestamp).append("\",")
                .append("\"service\":\"").append(SERVICE_NAME).append("\",")
                .append("\"level\":\"").append(level).append("\",")
                .append("\"component\":\"").append(component).append("\",")
                .append("\"thread\":\"").append(threadName).append("\",")
                .append("\"message\":\"").append(sanitizedMessage).append("\"");

        if (throwable != null) {
            String exceptionClass = throwable.getClass().getName();
            String exceptionMsg = throwable.getMessage() != null ? throwable.getMessage().replace("\"", "\\\"") : "";
            jsonBuilder.append(",")
                    .append("\"exception\":\"").append(exceptionClass).append("\",")
                    .append("\"exception_message\":\"").append(exceptionMsg).append("\"");
        }

        jsonBuilder.append("}");
        logQueue.offer(jsonBuilder.toString());
    }
}
