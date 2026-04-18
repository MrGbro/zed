package io.homeey.gateway.core.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public final class SnapshotSchemaValidator {
    public static final int DEFAULT_SCHEMA_VERSION = 1;
    public static final int LATEST_SCHEMA_VERSION = 2;

    public int resolveAndValidateRoot(JsonNode root) {
        int schemaVersion = root.path("schemaVersion").asInt(DEFAULT_SCHEMA_VERSION);
        if (schemaVersion < DEFAULT_SCHEMA_VERSION || schemaVersion > LATEST_SCHEMA_VERSION) {
            throw new SnapshotCodecException(
                    "SNAPSHOT_UNSUPPORTED_SCHEMA",
                    "Unsupported schemaVersion: " + schemaVersion,
                    "schemaVersion"
            );
        }
        if (!root.has("routes") || !root.path("routes").isArray()) {
            throw new SnapshotCodecException(
                    "SNAPSHOT_INVALID_ROOT",
                    "Invalid snapshot: routes must be array",
                    "routes"
            );
        }
        if (schemaVersion >= 2 && !root.has("schemaVersion")) {
            throw new SnapshotCodecException(
                    "SNAPSHOT_INVALID_ROOT",
                    "Invalid snapshot: schemaVersion missing",
                    "schemaVersion"
            );
        }
        return schemaVersion;
    }

    public void validateRouteNode(JsonNode routeNode, int schemaVersion) {
        String routeId = routeNode.path("id").asText("");
        if (routeId.isBlank()) {
            throw new SnapshotCodecException(
                    "SNAPSHOT_INVALID_ROUTE",
                    "Invalid route: id is required",
                    "routes[].id"
            );
        }
        String host = routeNode.path("host").asText("");
        if (host.isBlank()) {
            throw new SnapshotCodecException(
                    "SNAPSHOT_INVALID_ROUTE",
                    "Invalid route " + routeId + ": host is required",
                    "routes[" + routeId + "].host"
            );
        }
        String pathPrefix = routeNode.path("pathPrefix").asText("");
        if (pathPrefix.isBlank()) {
            pathPrefix = routeNode.path("path").asText("");
        }
        if (pathPrefix.isBlank()) {
            throw new SnapshotCodecException(
                    "SNAPSHOT_INVALID_ROUTE",
                    "Invalid route " + routeId + ": pathPrefix/path is required",
                    "routes[" + routeId + "].pathPrefix"
            );
        }
        String upstreamService = routeNode.path("upstreamService").asText("");
        if (upstreamService.isBlank()) {
            upstreamService = routeNode.path("upstream").asText("");
        }
        if (upstreamService.isBlank()) {
            throw new SnapshotCodecException(
                    "SNAPSHOT_INVALID_ROUTE",
                    "Invalid route " + routeId + ": upstreamService/upstream is required",
                    "routes[" + routeId + "].upstreamService"
            );
        }
        if (schemaVersion >= 2) {
            if (!routeNode.has("method")) {
                throw new SnapshotCodecException(
                        "SNAPSHOT_INVALID_ROUTE",
                        "Invalid route " + routeId + ": method is required for schema v2",
                        "routes[" + routeId + "].method"
                );
            }
            if (!routeNode.has("headers")) {
                throw new SnapshotCodecException(
                        "SNAPSHOT_INVALID_ROUTE",
                        "Invalid route " + routeId + ": headers is required for schema v2",
                        "routes[" + routeId + "].headers"
                );
            }
        }
    }
}
