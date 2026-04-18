package io.homeey.gateway.core.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.homeey.gateway.core.route.RouteDefinition;
import io.homeey.gateway.core.route.RouteTableSnapshot;
import io.homeey.gateway.plugin.api.FilterFailPolicy;
import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PolicySet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 路由快照编解码器，负责将JSON格式的路由配置转换为路由表快照。
 * <p>
 * 支持从JSON字符串中解析路由定义、插件绑定和策略集，并构建 {@link RouteTableSnapshot}。
 * 同时处理不同版本的schema兼容性。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class RouteSnapshotCodec {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SnapshotSchemaValidator schemaValidator = new SnapshotSchemaValidator();

    /**
     * 解码JSON字符串为路由表快照。
     *
     * @param json JSON格式的路由配置
     * @return 路由表快照
     * @throws SnapshotCodecException 如果解码失败
     */
    public RouteTableSnapshot decode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            int schemaVersion = schemaValidator.resolveAndValidateRoot(root);
            String version = root.path("version").asText("v0");
            List<PluginBinding> topLevelBindings = readPluginBindings(root.path("pluginBindings"), "");
            Map<String, Object> topLevelPolicies = readObject(root.path("policySet"));
            List<RouteDefinition> routes = new ArrayList<>();
            for (JsonNode routeNode : root.path("routes")) {
                schemaValidator.validateRouteNode(routeNode, schemaVersion);
                String pathPrefix = routeNode.path("pathPrefix").asText("");
                if (pathPrefix.isBlank()) {
                    pathPrefix = routeNode.path("path").asText("");
                }
                String upstreamService = routeNode.path("upstreamService").asText("");
                if (upstreamService.isBlank()) {
                    upstreamService = routeNode.path("upstream").asText("");
                }
                String routeId = routeNode.path("id").asText("");
                List<PluginBinding> mergedBindings = mergeBindings(
                        topLevelBindings,
                        readPluginBindings(routeNode.path("plugins"), routeId),
                        routeId
                );
                Map<String, Object> routePolicies = readObject(routeNode.path("policies"));
                Map<String, Object> mergedPolicies = new java.util.LinkedHashMap<>(topLevelPolicies);
                mergedPolicies.putAll(routePolicies);
                routes.add(new RouteDefinition(
                        routeId,
                        routeNode.path("host").asText(),
                        pathPrefix,
                        routeNode.path("method").asText("GET"),
                        readHeaders(routeNode.path("headers")),
                        upstreamService,
                        routeNode.path("upstreamPath").asText(""),
                        mergedBindings,
                        new PolicySet(mergedPolicies)
                ));
            }
            return new RouteTableSnapshot(version, routes);
        } catch (SnapshotCodecException ex) {
            throw ex;
        } catch (Exception e) {
            throw new SnapshotCodecException("SNAPSHOT_DECODE_FAILED", "Failed to decode route snapshot", e);
        }
    }

    private Map<String, String> readHeaders(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return Map.of();
        }
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            headers.put(field.getKey(), field.getValue().asText());
        }
        return Map.copyOf(headers);
    }

    private List<PluginBinding> readPluginBindings(JsonNode node, String routeId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<PluginBinding> bindings = new ArrayList<>();
        if (node.isArray()) {
            int index = 0;
            for (JsonNode pluginNode : node) {
                if (pluginNode.isTextual()) {
                    bindings.add(new PluginBinding(
                            pluginNode.asText(),
                            routeId,
                            index++,
                            true,
                            FilterFailPolicy.FAIL_CLOSE,
                            Map.of()
                    ));
                    continue;
                }
                String name = pluginNode.path("name").asText(pluginNode.path("id").asText(""));
                if (name.isBlank()) {
                    continue;
                }
                String failPolicyText = pluginNode.path("failPolicy").asText("FAIL_CLOSE");
                FilterFailPolicy failPolicy = parseFailPolicy(failPolicyText);
                String bindingRouteId = pluginNode.path("routeId").asText(routeId);
                if (bindingRouteId == null || bindingRouteId.isBlank()) {
                    bindingRouteId = routeId;
                }
                bindings.add(new PluginBinding(
                        name,
                        bindingRouteId,
                        pluginNode.path("order").asInt(index++),
                        pluginNode.path("enabled").asBoolean(true),
                        failPolicy,
                        readObject(pluginNode.path("config"))
                ));
            }
        } else if (node.isObject()) {
            int index = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                bindings.add(new PluginBinding(
                        field.getKey(),
                        routeId,
                        index++,
                        true,
                        FilterFailPolicy.FAIL_CLOSE,
                        readObject(field.getValue())
                ));
            }
        }
        return List.copyOf(bindings);
    }

    private List<PluginBinding> mergeBindings(
            List<PluginBinding> topLevelBindings,
            List<PluginBinding> routeLevelBindings,
            String routeId
    ) {
        List<PluginBinding> merged = new ArrayList<>();
        for (PluginBinding binding : topLevelBindings) {
            if (binding.routeId() == null || binding.routeId().isBlank() || binding.routeId().equals(routeId)) {
                merged.add(binding);
            }
        }
        if (routeLevelBindings != null) {
            merged.addAll(routeLevelBindings);
        }
        return List.copyOf(merged);
    }

    private FilterFailPolicy parseFailPolicy(String text) {
        if ("FAIL_OPEN".equalsIgnoreCase(text)) {
            return FilterFailPolicy.FAIL_OPEN;
        }
        return FilterFailPolicy.FAIL_CLOSE;
    }

    private Map<String, Object> readObject(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
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
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble()) {
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
}
