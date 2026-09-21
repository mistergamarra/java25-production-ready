package com.mistergamarra.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import static com.mistergamarra.MicroserviceApplication.getEnvOrProperty;

public class Otel {

    public static void initializeOpenTelemetryGlobalContext() {
        String otelEnabledStr = getEnvOrProperty("OTEL_ENABLED", "false");
        boolean otelEnabled = Boolean.parseBoolean(otelEnabledStr);

        if (!otelEnabled) {
            Logger.info("OpenTelemetry instrumentation is DISABLED (OTEL_ENABLED=false). GlobalOpenTelemetry will use default No-op engine.", "Telemetry");
            // By doing nothing here, GlobalOpenTelemetry automatically defaults to a clean No-op engine,
            // resulting in ZERO runtime system call or allocations overhead.
            return;
        }

        String otelEndpoint = getEnvOrProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4317");
        Logger.info("OpenTelemetry instrumentation is ENABLED. Exporting traces to: " + otelEndpoint, "Telemetry");

        try {
            OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otelEndpoint)
                    .build();

            Resource resource = Resource.getDefault().merge(
                    Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "java25-production-ready"))
            );

            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                    .setResource(resource)
                    .build();

            // Binds the active SDK configuration directly into OpenTelemetry's global framework state
            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();

            GlobalOpenTelemetry.set(sdk);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Logger.info("Flushing remaining telemetry spans out-of-band...", "Telemetry");
                tracerProvider.shutdown();
            }));

        } catch (Exception e) {
            Logger.error("Failed to initialize OpenTelemetry SDK context.", "Telemetry", e);
        }
    }

}
