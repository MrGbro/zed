package io.homeey.gateway.observe.otel;

import io.homeey.gateway.observe.api.ObserveOptions;
import io.homeey.gateway.observe.api.ObserveProvider;
import io.homeey.gateway.observe.api.RequestObservation;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default OpenTelemetry backed observe provider with fail-open behavior.
 */
public final class OtelObserveProvider implements ObserveProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OtelObserveProvider.class);
    private static final Logger ACCESS_LOGGER = LoggerFactory.getLogger("io.homeey.gateway.access");
    private static final AtomicLong SEQ = new AtomicLong(1);
    private static final TextMapGetter<HttpRequestMessage> REQUEST_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpRequestMessage carrier) {
            return carrier.headers().keySet();
        }

        @Override
        public String get(HttpRequestMessage carrier, String key) {
            if (carrier == null || carrier.headers() == null) {
                return null;
            }
            return carrier.headers().get(key);
        }
    };

    private final ObserveOptions options;
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private volatile OpenTelemetrySdk sdk;
    private volatile SdkTracerProvider tracerProvider;
    private volatile SdkMeterProvider meterProvider;
    private volatile SdkLoggerProvider loggerProvider;
    private volatile OpenTelemetry telemetry;
    private volatile Tracer tracer;

    public OtelObserveProvider(ObserveOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        counters.put("gateway_requests_total", new AtomicLong(0));
        counters.put("gateway_errors_total", new AtomicLong(0));
        counters.put("gateway_request_duration_ms_count", new AtomicLong(0));
        counters.put("gateway_request_duration_ms_sum", new AtomicLong(0));
    }

    @Override
    public void init() {
        try {
            Resource resource = Resource.getDefault().merge(
                    Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), options.serviceName()))
            );
            var tracerBuilder = SdkTracerProvider.builder().setResource(resource);
            var meterBuilder = SdkMeterProvider.builder().setResource(resource);
            var loggerBuilder = SdkLoggerProvider.builder().setResource(resource);

            if (!options.otlpEndpoint().isBlank()) {
                var spanExporterBuilder = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(options.otlpEndpoint());
                var metricExporterBuilder = OtlpGrpcMetricExporter.builder()
                        .setEndpoint(options.otlpEndpoint());
                var logExporterBuilder = OtlpGrpcLogRecordExporter.builder()
                        .setEndpoint(options.otlpEndpoint());
                for (Map.Entry<String, String> header : options.otlpHeaders().entrySet()) {
                    spanExporterBuilder.addHeader(header.getKey(), header.getValue());
                    metricExporterBuilder.addHeader(header.getKey(), header.getValue());
                    logExporterBuilder.addHeader(header.getKey(), header.getValue());
                }
                tracerBuilder.addSpanProcessor(BatchSpanProcessor.builder(spanExporterBuilder.build()).build());
                meterBuilder.registerMetricReader(PeriodicMetricReader.builder(metricExporterBuilder.build())
                        .setInterval(Duration.ofMillis(options.exportIntervalMillis()))
                        .build());
                loggerBuilder.addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporterBuilder.build()).build());
            }

            this.tracerProvider = tracerBuilder.build();
            this.meterProvider = meterBuilder.build();
            this.loggerProvider = loggerBuilder.build();
            this.sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setMeterProvider(meterProvider)
                    .setLoggerProvider(loggerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
            this.telemetry = sdk;
            this.tracer = sdk.getTracer("io.homeey.gateway.observe.otel");
        } catch (Exception e) {
            LOGGER.warn("Observe provider init failed, fallback to noop telemetry behavior", e);
            this.sdk = null;
            this.telemetry = OpenTelemetry.noop();
            this.tracer = telemetry.getTracer("io.homeey.gateway.observe.noop");
        }
    }

    @Override
    public void start() {
        LOGGER.info("Observe provider started: type=otel, serviceName={}, endpoint={}", options.serviceName(), options.otlpEndpoint());
    }

    @Override
    public void stop() {
        try {
            SdkMeterProvider currentMeterProvider = this.meterProvider;
            if (currentMeterProvider != null) {
                currentMeterProvider.forceFlush();
                currentMeterProvider.close();
            }
            SdkTracerProvider currentTracerProvider = this.tracerProvider;
            if (currentTracerProvider != null) {
                currentTracerProvider.forceFlush();
                currentTracerProvider.close();
            }
            SdkLoggerProvider currentLoggerProvider = this.loggerProvider;
            if (currentLoggerProvider != null) {
                currentLoggerProvider.forceFlush();
                currentLoggerProvider.close();
            }
        } catch (Exception e) {
            LOGGER.warn("Observe provider stop encountered exception", e);
        }
    }

    @Override
    public RequestObservation begin(HttpRequestMessage request) {
        long startNanos = System.nanoTime();
        String traceId = "";
        Span span = null;
        Scope scope = null;
        try {
            Context extracted = ContextPropagators.create(W3CTraceContextPropagator.getInstance())
                    .getTextMapPropagator()
                    .extract(Context.current(), request, REQUEST_GETTER);
            span = tracer()
                    .spanBuilder(request.method() + " " + safePath(request.path()))
                    .setSpanKind(SpanKind.SERVER)
                    .setParent(extracted)
                    .setAttribute("http.method", request.method())
                    .setAttribute("http.target", safePath(request.path()))
                    .setAttribute("gateway.request.seq", SEQ.getAndIncrement())
                    .startSpan();
            traceId = span.getSpanContext().getTraceId();
            scope = span.makeCurrent();
        } catch (Exception e) {
            LOGGER.debug("Observe begin failed, keep request path untouched", e);
        }
        final Span currentSpan = span;
        final Scope currentScope = scope;
        final String currentTraceId = traceId == null ? "" : traceId;
        return new RequestObservation() {
            private volatile String routeId = "";
            private volatile String upstream = "";
            private volatile int status = 0;
            private volatile String errorCategory = "";
            private volatile Throwable error;

            @Override
            public String traceId() {
                return currentTraceId;
            }

            @Override
            public void onRouteMatched(String value) {
                this.routeId = value == null ? "" : value;
                if (currentSpan != null && !routeId.isBlank()) {
                    currentSpan.setAttribute("gateway.route.id", routeId);
                }
            }

            @Override
            public void onUpstreamSelected(String value) {
                this.upstream = value == null ? "" : value;
                if (currentSpan != null && !upstream.isBlank()) {
                    currentSpan.setAttribute("gateway.upstream", upstream);
                }
            }

            @Override
            public void onError(Throwable throwable, String category) {
                this.error = throwable;
                this.errorCategory = category == null ? "" : category;
                counters.get("gateway_errors_total").incrementAndGet();
                if (currentSpan != null) {
                    currentSpan.recordException(throwable);
                    currentSpan.setStatus(StatusCode.ERROR);
                    if (!this.errorCategory.isBlank()) {
                        currentSpan.setAttribute("gateway.error.category", this.errorCategory);
                    }
                }
            }

            @Override
            public void onResponse(HttpResponseMessage response) {
                this.status = response == null ? 0 : response.statusCode();
                counters.get("gateway_requests_total").incrementAndGet();
                if (currentSpan != null && status > 0) {
                    currentSpan.setAttribute("http.status_code", status);
                }
            }

            @Override
            public void close() {
                long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
                counters.get("gateway_request_duration_ms_count").incrementAndGet();
                counters.get("gateway_request_duration_ms_sum").addAndGet(durationMs);
                if (currentSpan != null) {
                    currentSpan.setAttribute("gateway.latency.ms", durationMs);
                    currentSpan.end();
                }
                if (currentScope != null) {
                    currentScope.close();
                }
                if (options.accessLogEnabled()) {
                    ACCESS_LOGGER.info(
                            "traceId={} routeId={} method={} path={} status={} latencyMs={} upstream={} errorCategory={}",
                            currentTraceId,
                            routeId,
                            request.method(),
                            safePath(request.path()),
                            status,
                            durationMs,
                            upstream,
                            errorCategory
                    );
                }
            }
        };
    }

    @Override
    public String metricsSnapshot() {
        long req = counters.get("gateway_requests_total").get();
        long err = counters.get("gateway_errors_total").get();
        long count = counters.get("gateway_request_duration_ms_count").get();
        long sum = counters.get("gateway_request_duration_ms_sum").get();
        StringBuilder out = new StringBuilder(256);
        out.append("# TYPE gateway_requests_total counter\n");
        out.append("gateway_requests_total ").append(req).append('\n');
        out.append("# TYPE gateway_errors_total counter\n");
        out.append("gateway_errors_total ").append(err).append('\n');
        out.append("# TYPE gateway_request_duration_ms summary\n");
        out.append("gateway_request_duration_ms_count ").append(count).append('\n');
        out.append("gateway_request_duration_ms_sum ").append(sum).append('\n');
        return out.toString();
    }

    private Tracer tracer() {
        Tracer current = this.tracer;
        if (current != null) {
            return current;
        }
        OpenTelemetry otel = this.telemetry;
        if (otel == null) {
            return OpenTelemetry.noop().getTracer("io.homeey.gateway.observe.noop");
        }
        return otel.getTracer("io.homeey.gateway.observe.otel");
    }

    private String safePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
