package io.homeey.gateway.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.homeey.gateway.admin.model.PublishRequest;
import io.homeey.gateway.admin.repository.PublishRecordRepository;
import io.homeey.gateway.config.api.ConfigProvider;
import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PublishRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发布服务，负责路由配置的验证和发布。
 * <p>
 * 该服务提供路由配置的业务逻辑处理，包括：
 * <ul>
 *   <li>验证路由配置的合法性</li>
 *   <li>构建路由快照并发布到配置中心</li>
 *   <li>记录发布历史</li>
 * </ul>
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@Service
public class PublishService {
    private static final int SNAPSHOT_SCHEMA_VERSION = 2;
    private static final String ROUTES_DATA_ID = "gateway.routes.json";
    private static final String ROUTES_GROUP = "GATEWAY";
    private final ConfigProvider configProvider;
    private final PublishRecordRepository publishRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublishService(ConfigProvider configProvider, PublishRecordRepository publishRecordRepository) {
        this.configProvider = configProvider;
        this.publishRecordRepository = publishRecordRepository;
    }

    public Map<String, Object> publish(PublishRequest request) {
        validateRequest(request);
        String version = "v" + System.currentTimeMillis();
        String publishedAt = Instant.now().toString();
        List<Map<String, Object>> routes = request.routes() == null
                ? List.of()
                : request.routes().stream().map(route -> Map.<String, Object>of(
                                "id", route.id(),
                                "host", route.host(),
                                "pathPrefix", route.pathPrefix(),
                                "method", route.method() == null ? "GET" : route.method(),
                                "headers", route.headers() == null ? Map.of() : route.headers(),
                                "upstreamService", route.upstreamService(),
                                "upstreamPath", route.upstreamPath() == null ? "" : route.upstreamPath()
                        ))
                        .toList();
        List<Map<String, Object>> routeBindings = new ArrayList<>();
        if (request.pluginBindings() != null) {
            for (PluginBinding binding : request.pluginBindings()) {
                routeBindings.add(Map.of(
                        "name", binding.name(),
                        "routeId", binding.routeId() == null ? "" : binding.routeId(),
                        "order", binding.order(),
                        "enabled", binding.enabled(),
                        "failPolicy", binding.failPolicy().name(),
                        "config", binding.config()
                ));
            }
        }
        Map<String, Object> snapshot = Map.of(
                "schemaVersion", SNAPSHOT_SCHEMA_VERSION,
                "version", version,
                "publishedAt", publishedAt,
                "operator", request.operator() == null ? "admin" : request.operator(),
                "summary", request.summary() == null ? "" : request.summary(),
                "policySet", request.policySet() == null ? Map.of() : request.policySet().entries(),
                "pluginBindings", routeBindings,
                "routes", List.copyOf(routes)
        );
        try {
            String payload = objectMapper.writeValueAsString(snapshot);
            boolean ok = configProvider.publish(ROUTES_DATA_ID, ROUTES_GROUP, payload)
                    .toCompletableFuture()
                    .join();
            PublishRecord record = new PublishRecord(
                    version,
                    publishedAt,
                    request.operator() == null ? "admin" : request.operator(),
                    request.summary() == null ? "" : request.summary(),
                    Map.of(
                            "schemaVersion", SNAPSHOT_SCHEMA_VERSION,
                            "published", ok,
                            "routesCount", routes.size(),
                            "bindingsCount", routeBindings.size()
                    )
            );
            publishRecordRepository.save(record);
            return Map.of(
                    "version", version,
                    "publishedAt", publishedAt,
                    "published", ok
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish routes snapshot", e);
        }
    }

    public List<PublishRecord> listRecords() {
        return publishRecordRepository.list();
    }

    private void validateRequest(PublishRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("publish request cannot be null");
        }
        if (request.routes() == null || request.routes().isEmpty()) {
            throw new IllegalArgumentException("routes cannot be empty");
        }
        Set<String> routeIds = new HashSet<>();
        for (PublishRequest.RouteItem route : request.routes()) {
            if (route == null) {
                throw new IllegalArgumentException("route item cannot be null");
            }
            if (isBlank(route.id())) {
                throw new IllegalArgumentException("route id cannot be blank");
            }
            if (!routeIds.add(route.id())) {
                throw new IllegalArgumentException("duplicate route id: " + route.id());
            }
            if (isBlank(route.host())) {
                throw new IllegalArgumentException("route host cannot be blank, route=" + route.id());
            }
            if (isBlank(route.pathPrefix())) {
                throw new IllegalArgumentException("route pathPrefix cannot be blank, route=" + route.id());
            }
            if (isBlank(route.upstreamService())) {
                throw new IllegalArgumentException("route upstreamService cannot be blank, route=" + route.id());
            }
            validateMethod(route.method(), route.id());
        }
        validateBindings(request.pluginBindings(), routeIds);
        validatePolicySet(request.policySet() == null ? Map.of() : request.policySet().entries());
    }

    private void validateMethod(String method, String routeId) {
        String normalized = method == null ? "GET" : method.trim().toUpperCase();
        if (!Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD").contains(normalized)) {
            throw new IllegalArgumentException("unsupported method '" + method + "', route=" + routeId);
        }
    }

    private void validateBindings(List<PluginBinding> bindings, Set<String> routeIds) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        Set<String> dedupe = new HashSet<>();
        for (PluginBinding binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException("plugin binding cannot be null");
            }
            if (isBlank(binding.name())) {
                throw new IllegalArgumentException("plugin binding name cannot be blank");
            }
            if (!isBlank(binding.routeId()) && !routeIds.contains(binding.routeId())) {
                throw new IllegalArgumentException("plugin binding routeId not found: " + binding.routeId());
            }
            String dedupeKey = binding.name() + "@" + (binding.routeId() == null ? "" : binding.routeId());
            if (!dedupe.add(dedupeKey)) {
                throw new IllegalArgumentException("duplicate plugin binding: " + dedupeKey);
            }
            if (binding.order() < 0) {
                throw new IllegalArgumentException("plugin binding order cannot be negative: " + dedupeKey);
            }
            validatePolicySet(binding.config());
        }
    }

    private void validatePolicySet(Map<String, Object> entries) {
        if (entries == null) {
            return;
        }
        Map<String, Object> flat = new HashMap<>(entries);
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            if (isBlank(entry.getKey())) {
                throw new IllegalArgumentException("policy key cannot be blank");
            }
            Object value = entry.getValue();
            if (value instanceof String str && str.length() > 4096) {
                throw new IllegalArgumentException("policy value too long, key=" + entry.getKey());
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
