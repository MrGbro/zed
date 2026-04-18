package io.homeey.gateway.core.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.List;

public final class RadixRouteLocator {
    private final Map<String, HostBucket> hostBuckets;

    public RadixRouteLocator(RouteTableSnapshot snapshot) {
        Map<String, HostBucket> buckets = new LinkedHashMap<>();
        for (RouteDefinition route : snapshot.routes()) {
            String host = normalizeHost(route.host());
            HostBucket bucket = buckets.computeIfAbsent(host, unused -> new HostBucket());
            bucket.insert(normalizePrefix(route.pathPrefix()), route);
        }
        this.hostBuckets = Map.copyOf(buckets);
    }

    public Optional<RouteDefinition> locate(
            String host,
            String path,
            String method,
            Map<String, String> headers
    ) {
        HostBucket bucket = hostBuckets.get(normalizeHost(host));
        if (bucket == null) {
            return Optional.empty();
        }
        List<RouteDefinition> candidates = bucket.match(normalizePath(path));
        for (RouteDefinition route : candidates) {
            if (route.method().equalsIgnoreCase(method) && headersMatch(route.headers(), headers)) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }

    private boolean headersMatch(Map<String, String> required, Map<String, String> actual) {
        for (Map.Entry<String, String> entry : required.entrySet()) {
            String key = entry.getKey();
            String expected = entry.getValue();
            String actualValue = findIgnoreCase(actual, key);
            if (actualValue == null || !expected.equals(actualValue)) {
                return false;
            }
        }
        return true;
    }

    private String findIgnoreCase(Map<String, String> headers, String key) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(key.toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        if (prefix.charAt(0) == '/') {
            return prefix;
        }
        return "/" + prefix;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private static final class HostBucket {
        private final RadixNode root = new RadixNode("");

        private void insert(String pathPrefix, RouteDefinition route) {
            if (pathPrefix.isEmpty()) {
                root.routes.add(route);
                return;
            }
            insertInto(root, pathPrefix, route);
        }

        private void insertInto(RadixNode parent, String path, RouteDefinition route) {
            char first = path.charAt(0);
            RadixNode child = parent.children.get(first);
            if (child == null) {
                RadixNode created = new RadixNode(path);
                created.routes.add(route);
                parent.children.put(first, created);
                return;
            }

            int common = commonPrefixLength(path, child.edgeLabel);
            if (common == child.edgeLabel.length()) {
                if (common == path.length()) {
                    child.routes.add(route);
                    return;
                }
                insertInto(child, path.substring(common), route);
                return;
            }

            String sharedPrefix = child.edgeLabel.substring(0, common);
            String existingSuffix = child.edgeLabel.substring(common);
            String newSuffix = path.substring(common);

            RadixNode splitNode = new RadixNode(sharedPrefix);
            parent.children.put(first, splitNode);

            child.edgeLabel = existingSuffix;
            splitNode.children.put(existingSuffix.charAt(0), child);

            if (newSuffix.isEmpty()) {
                splitNode.routes.add(route);
                return;
            }
            RadixNode newNode = new RadixNode(newSuffix);
            newNode.routes.add(route);
            splitNode.children.put(newSuffix.charAt(0), newNode);
        }

        private List<RouteDefinition> match(String path) {
            List<MatchedRoutes> byDepth = new ArrayList<>();
            if (!root.routes.isEmpty()) {
                byDepth.add(new MatchedRoutes(0, root.routes));
            }
            RadixNode current = root;
            int cursor = 0;
            while (cursor < path.length()) {
                RadixNode next = current.children.get(path.charAt(cursor));
                if (next == null) {
                    break;
                }
                int common = commonPrefixLength(path, cursor, next.edgeLabel);
                if (common < next.edgeLabel.length()) {
                    break;
                }
                cursor += common;
                current = next;
                if (!current.routes.isEmpty()) {
                    byDepth.add(new MatchedRoutes(cursor, current.routes));
                }
            }
            List<RouteDefinition> result = new ArrayList<>();
            byDepth.stream()
                    .sorted(Comparator.comparingInt(MatchedRoutes::depth).reversed())
                    .forEach(matched -> result.addAll(matched.routes()));
            return result;
        }

        private int commonPrefixLength(String left, String right) {
            int bound = Math.min(left.length(), right.length());
            int i = 0;
            while (i < bound && left.charAt(i) == right.charAt(i)) {
                i++;
            }
            return i;
        }

        private int commonPrefixLength(String path, int startIndex, String edgeLabel) {
            int bound = Math.min(path.length() - startIndex, edgeLabel.length());
            int i = 0;
            while (i < bound && path.charAt(startIndex + i) == edgeLabel.charAt(i)) {
                i++;
            }
            return i;
        }
    }

    private static final class RadixNode {
        private String edgeLabel;
        private final Map<Character, RadixNode> children = new LinkedHashMap<>();
        private final List<RouteDefinition> routes = new ArrayList<>();

        private RadixNode(String edgeLabel) {
            this.edgeLabel = edgeLabel;
        }
    }

    private record MatchedRoutes(int depth, List<RouteDefinition> routes) {
    }
}
