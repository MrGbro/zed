package io.homeey.gateway.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.homeey.gateway.admin.model.PublishRequest;
import io.homeey.gateway.admin.model.ReleaseRecord;
import io.homeey.gateway.admin.service.ReleaseGovernanceService;
import io.homeey.gateway.admin.service.PublishService;
import io.homeey.gateway.config.api.ConfigProvider;
import io.homeey.gateway.plugin.api.FilterFailPolicy;
import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PublishRecord;
import io.homeey.gateway.plugin.api.PolicySet;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 路由管理控制器，提供路由的CRUD和发布功能。
 * <p>
 * 该控制器提供REST API用于管理路由配置、插件绑定以及发布路由到网关。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private static final String ROUTES_DATA_ID = "gateway.routes.json";
    private static final String ROUTES_GROUP = "GATEWAY";

    private final List<PublishRequest.RouteItem> routes = new CopyOnWriteArrayList<>();
    private final List<PluginBinding> pluginBindings = new CopyOnWriteArrayList<>();
    private final PublishService publishService;
    private final ReleaseGovernanceService releaseGovernanceService;
    private final ConfigProvider configProvider;
    private final ObjectMapper objectMapper;
    private final Object initLock = new Object();
    private volatile boolean initializedFromConfigCenter = false;

    public RouteController(
            PublishService publishService,
            ReleaseGovernanceService releaseGovernanceService,
            ConfigProvider configProvider,
            ObjectMapper objectMapper
    ) {
        this.publishService = publishService;
        this.releaseGovernanceService = releaseGovernanceService;
        this.configProvider = configProvider;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<PublishRequest.RouteItem> list() {
        ensureStateInitialized();
        return new ArrayList<>(routes);
    }

    @PostMapping
    public PublishRequest.RouteItem create(@RequestBody PublishRequest.RouteItem route) {
        ensureStateInitialized();
        routes.add(route);
        return route;
    }

    @DeleteMapping("/{routeId}")
    public Map<String, Object> delete(@PathVariable("routeId") String routeId) {
        ensureStateInitialized();
        routes.removeIf(route -> route != null && routeId.equals(route.id()));
        pluginBindings.removeIf(binding -> binding != null && routeId.equals(binding.routeId()));
        return Map.of("deletedRouteId", routeId);
    }

    @GetMapping("/plugins")
    public List<PluginBinding> listBindings() {
        ensureStateInitialized();
        return new ArrayList<>(pluginBindings);
    }

    @PostMapping("/plugins")
    public List<PluginBinding> bindPlugins(
            @RequestBody List<PluginBinding> bindings
    ) {
        ensureStateInitialized();
        pluginBindings.clear();
        if (bindings != null) {
            pluginBindings.addAll(bindings);
        }
        return new ArrayList<>(pluginBindings);
    }

    @DeleteMapping("/plugins/{name}")
    public Map<String, Object> deleteBinding(
            @PathVariable("name") String name,
            @RequestParam(value = "routeId", required = false) String routeId
    ) {
        ensureStateInitialized();
        pluginBindings.removeIf(binding -> binding != null
                && name.equals(binding.name())
                && (routeId == null || routeId.equals(binding.routeId())));
        return Map.of(
                "deletedBinding", name,
                "routeId", routeId == null ? "" : routeId
        );
    }

    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestBody(required = false) PublishCommand command) {
        ensureStateInitialized();
        PublishRequest request = new PublishRequest(
                new ArrayList<>(routes),
                new ArrayList<>(pluginBindings),
                new PolicySet(command == null || command.policySet() == null ? Map.of() : command.policySet()),
                command == null || command.operator() == null || command.operator().isBlank() ? "admin" : command.operator(),
                command == null || command.summary() == null ? "publish routes and plugins" : command.summary()
        );
        return publishService.publish(request);
    }

    @PostMapping("/releases/draft")
    public ReleaseRecord createReleaseDraft(@RequestBody(required = false) ReleaseDraftCommand command) {
        ensureStateInitialized();
        return releaseGovernanceService.createDraft(
                new ArrayList<>(routes),
                new ArrayList<>(pluginBindings),
                new ReleaseGovernanceService.DraftCommand(
                        command == null ? null : command.operator(),
                        command == null ? null : command.summary(),
                        command == null ? Map.of() : command.policySet(),
                        command == null || command.canary() == null
                                ? ReleaseRecord.CanaryPolicy.disabled()
                                : command.canary().toPolicy()
                )
        );
    }

    @PostMapping("/releases/{releaseId}/validate")
    public ReleaseRecord validateRelease(@PathVariable("releaseId") String releaseId) {
        return releaseGovernanceService.validate(releaseId);
    }

    @PostMapping("/releases/{releaseId}/approve")
    public ReleaseRecord approveRelease(
            @PathVariable("releaseId") String releaseId,
            @RequestBody(required = false) ApproveCommand command
    ) {
        return releaseGovernanceService.approve(
                releaseId,
                new ReleaseGovernanceService.ApproveCommand(
                        command == null ? null : command.approver(),
                        command == null ? null : command.comment()
                )
        );
    }

    @PostMapping("/releases/{releaseId}/publish")
    public ReleaseRecord publishRelease(@PathVariable("releaseId") String releaseId) {
        return releaseGovernanceService.publish(releaseId);
    }

    @PostMapping("/releases/{releaseId}/rollback")
    public ReleaseRecord rollbackRelease(
            @PathVariable("releaseId") String releaseId,
            @RequestBody(required = false) RollbackCommand command
    ) {
        return releaseGovernanceService.rollback(
                releaseId,
                new ReleaseGovernanceService.RollbackCommand(
                        command == null ? null : command.operator(),
                        command == null ? null : command.comment(),
                        command == null ? null : command.targetReleaseId()
                )
        );
    }

    @GetMapping("/releases/{releaseId}")
    public ReleaseRecord getRelease(@PathVariable("releaseId") String releaseId) {
        return releaseGovernanceService.get(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("release not found: " + releaseId));
    }

    @GetMapping("/releases")
    public List<ReleaseRecord> listReleases() {
        return releaseGovernanceService.list();
    }

    @GetMapping("/publish-records")
    public List<PublishRecord> publishRecords() {
        return publishService.listRecords();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationError(IllegalArgumentException ex) {
        return Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage()
        );
    }

    private void ensureStateInitialized() {
        if (initializedFromConfigCenter || !routes.isEmpty() || !pluginBindings.isEmpty()) {
            return;
        }
        synchronized (initLock) {
            if (initializedFromConfigCenter || !routes.isEmpty() || !pluginBindings.isEmpty()) {
                return;
            }
            try {
                String payload = configProvider.get(ROUTES_DATA_ID, ROUTES_GROUP).toCompletableFuture().join();
                if (payload == null || payload.isBlank()) {
                    initializedFromConfigCenter = true;
                    return;
                }
                JsonNode root = objectMapper.readTree(payload);
                List<PublishRequest.RouteItem> loadedRoutes = readRoutes(root.path("routes"));
                List<PluginBinding> loadedBindings = readTopLevelBindings(root.path("pluginBindings"));
                loadedBindings = mergeBindings(loadedBindings, readRouteLevelBindings(root.path("routes")));
                routes.clear();
                routes.addAll(loadedRoutes);
                pluginBindings.clear();
                pluginBindings.addAll(mergeAndDedupeBindings(loadedRoutes, loadedBindings));
                initializedFromConfigCenter = true;
            } catch (Exception ignored) {
                // Keep in-memory state and retry initialization on the next request.
            }
        }
    }

    private List<PublishRequest.RouteItem> readRoutes(JsonNode routesNode) {
        if (routesNode == null || !routesNode.isArray()) {
            return List.of();
        }
        List<PublishRequest.RouteItem> loaded = new ArrayList<>();
        for (JsonNode routeNode : routesNode) {
            if (routeNode == null || !routeNode.isObject()) {
                continue;
            }
            String id = text(routeNode.path("id"));
            if (id.isBlank()) {
                continue;
            }
            String pathPrefix = text(routeNode.path("pathPrefix"));
            if (pathPrefix.isBlank()) {
                pathPrefix = text(routeNode.path("path"));
            }
            String upstreamService = text(routeNode.path("upstreamService"));
            if (upstreamService.isBlank()) {
                upstreamService = text(routeNode.path("upstream"));
            }
            String method = text(routeNode.path("method"));
            loaded.add(new PublishRequest.RouteItem(
                    id,
                    text(routeNode.path("host")),
                    pathPrefix,
                    method.isBlank() ? "GET" : method,
                    readHeaders(routeNode.path("headers")),
                    upstreamService,
                    text(routeNode.path("upstreamPath"))
            ));
        }
        return List.copyOf(loaded);
    }

    private List<PluginBinding> readTopLevelBindings(JsonNode bindingsNode) {
        return readPluginBindings(bindingsNode, "");
    }

    private List<PluginBinding> readRouteLevelBindings(JsonNode routesNode) {
        if (routesNode == null || !routesNode.isArray()) {
            return List.of();
        }
        List<PluginBinding> all = new ArrayList<>();
        for (JsonNode routeNode : routesNode) {
            if (routeNode == null || !routeNode.isObject()) {
                continue;
            }
            String routeId = text(routeNode.path("id"));
            all.addAll(readPluginBindings(routeNode.path("plugins"), routeId));
        }
        return List.copyOf(all);
    }

    private List<PluginBinding> mergeAndDedupeBindings(
            List<PublishRequest.RouteItem> loadedRoutes,
            List<PluginBinding> topLevelBindings
    ) {
        Map<String, PluginBinding> merged = new LinkedHashMap<>();
        if (topLevelBindings != null) {
            for (PluginBinding binding : topLevelBindings) {
                if (binding == null || isBlank(binding.name())) {
                    continue;
                }
                merged.put(bindingKey(binding.name(), binding.routeId()), binding);
            }
        }
        return List.copyOf(merged.values());
    }

    private List<PluginBinding> mergeBindings(List<PluginBinding> left, List<PluginBinding> right) {
        List<PluginBinding> merged = new ArrayList<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return List.copyOf(merged);
    }

    private List<PluginBinding> readPluginBindings(JsonNode node, String defaultRouteId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<PluginBinding> bindings = new ArrayList<>();
        if (node.isArray()) {
            int index = 0;
            for (JsonNode pluginNode : node) {
                PluginBinding binding = parseBindingNode(pluginNode, defaultRouteId, index++);
                if (binding != null) {
                    bindings.add(binding);
                }
            }
            return List.copyOf(bindings);
        }
        if (node.isObject()) {
            if (node.has("name") || node.has("id")) {
                PluginBinding single = parseBindingNode(node, defaultRouteId, 0);
                if (single != null) {
                    bindings.add(single);
                }
                return List.copyOf(bindings);
            }
            int index = 0;
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String pluginName = field.getKey();
                if (isBlank(pluginName)) {
                    continue;
                }
                bindings.add(new PluginBinding(
                        pluginName,
                        defaultRouteId,
                        index++,
                        true,
                        FilterFailPolicy.FAIL_CLOSE,
                        readObject(field.getValue())
                ));
            }
            return List.copyOf(bindings);
        }
        return List.of();
    }

    private PluginBinding parseBindingNode(JsonNode pluginNode, String defaultRouteId, int defaultOrder) {
        if (pluginNode == null || pluginNode.isNull()) {
            return null;
        }
        if (pluginNode.isTextual()) {
            String name = pluginNode.asText();
            if (isBlank(name)) {
                return null;
            }
            return new PluginBinding(
                    name,
                    defaultRouteId,
                    defaultOrder,
                    true,
                    FilterFailPolicy.FAIL_CLOSE,
                    Map.of()
            );
        }
        if (!pluginNode.isObject()) {
            return null;
        }
        String name = text(pluginNode.path("name"));
        if (name.isBlank()) {
            name = text(pluginNode.path("id"));
        }
        if (name.isBlank()) {
            return null;
        }
        String routeId = text(pluginNode.path("routeId"));
        if (routeId.isBlank()) {
            routeId = defaultRouteId;
        }
        return new PluginBinding(
                name,
                routeId,
                pluginNode.path("order").asInt(defaultOrder),
                pluginNode.path("enabled").asBoolean(true),
                parseFailPolicy(text(pluginNode.path("failPolicy"))),
                readObject(pluginNode.path("config"))
        );
    }

    private FilterFailPolicy parseFailPolicy(String raw) {
        if ("FAIL_OPEN".equalsIgnoreCase(raw)) {
            return FilterFailPolicy.FAIL_OPEN;
        }
        return FilterFailPolicy.FAIL_CLOSE;
    }

    private Map<String, String> readHeaders(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            headers.put(field.getKey(), field.getValue().asText());
        }
        return Map.copyOf(headers);
    }

    private Map<String, Object> readObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            values.put(field.getKey(), convertNode(field.getValue()));
        }
        return Map.copyOf(values);
    }

    private Object convertNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode item : node) {
                values.add(convertNode(item));
            }
            return List.copyOf(values);
        }
        if (node.isObject()) {
            return readObject(node);
        }
        return node.asText();
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private String bindingKey(String name, String routeId) {
        return name + "@" + (routeId == null ? "" : routeId);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PublishCommand(
            String operator,
            String summary,
            Map<String, Object> policySet
    ) {
    }

    public record ReleaseDraftCommand(
            String operator,
            String summary,
            Map<String, Object> policySet,
            CanaryCommand canary
    ) {
    }

    public record CanaryCommand(
            String mode,
            String header,
            String value,
            boolean enabled
    ) {
        ReleaseRecord.CanaryPolicy toPolicy() {
            return new ReleaseRecord.CanaryPolicy(mode, header, value, enabled);
        }
    }

    public record ApproveCommand(
            String approver,
            String comment
    ) {
    }

    public record RollbackCommand(
            String operator,
            String comment,
            String targetReleaseId
    ) {
    }
}
