package io.homeey.gateway.core;

import io.homeey.gateway.core.route.RouteTableSnapshot;
import io.homeey.gateway.core.runtime.RouteSnapshotCodec;
import io.homeey.gateway.core.runtime.SnapshotCodecException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteSnapshotCodecTest {

    @Test
    void shouldDecodeSchemaV1CompatibilitySnapshot() {
        String json = """
                {
                  "version":"v1",
                  "routes":[
                    {
                      "id":"r1",
                      "host":"api.example.com",
                      "path":"/orders",
                      "upstream":"order-service"
                    }
                  ]
                }
                """;
        RouteSnapshotCodec codec = new RouteSnapshotCodec();
        RouteTableSnapshot snapshot = codec.decode(json);
        assertEquals("v1", snapshot.version());
        assertEquals(1, snapshot.routes().size());
        assertEquals("r1", snapshot.routes().get(0).id());
    }

    @Test
    void shouldDecodeSchemaV2Snapshot() {
        String json = """
                {
                  "schemaVersion":2,
                  "version":"v2",
                  "policySet":{"auth.enabled":true},
                  "pluginBindings":[{"name":"auth","order":1,"enabled":true,"failPolicy":"FAIL_CLOSE","config":{}}],
                  "routes":[
                    {
                      "id":"r2",
                      "host":"api.example.com",
                      "pathPrefix":"/orders",
                      "method":"GET",
                      "headers":{"x-env":"prod"},
                      "upstreamService":"order-service",
                      "upstreamPath":"/orders"
                    }
                  ]
                }
                """;
        RouteSnapshotCodec codec = new RouteSnapshotCodec();
        RouteTableSnapshot snapshot = codec.decode(json);
        assertEquals("v2", snapshot.version());
        assertEquals(1, snapshot.routes().size());
        assertEquals("r2", snapshot.routes().get(0).id());
    }

    @Test
    void shouldRejectUnknownSchemaVersion() {
        String json = """
                {
                  "schemaVersion":99,
                  "version":"v1",
                  "routes":[]
                }
                """;
        RouteSnapshotCodec codec = new RouteSnapshotCodec();
        SnapshotCodecException ex = assertThrows(SnapshotCodecException.class, () -> codec.decode(json));
        assertEquals("SNAPSHOT_UNSUPPORTED_SCHEMA", ex.code());
    }

    @Test
    void shouldRejectInvalidSchemaV2RouteShape() {
        String json = """
                {
                  "schemaVersion":2,
                  "version":"v2",
                  "routes":[
                    {"id":"r3","host":"api.example.com","pathPrefix":"/orders","upstreamService":"order-service"}
                  ]
                }
                """;
        RouteSnapshotCodec codec = new RouteSnapshotCodec();
        SnapshotCodecException ex = assertThrows(SnapshotCodecException.class, () -> codec.decode(json));
        assertEquals("SNAPSHOT_INVALID_ROUTE", ex.code());
    }

    @Test
    void shouldDecodeStaticRouteWithoutUpstreamTarget() {
        String json = """
                {
                  "schemaVersion":2,
                  "version":"v3",
                  "routes":[
                    {
                      "id":"r-static",
                      "host":"api.example.com",
                      "pathPrefix":"/fixtures",
                      "method":"GET",
                      "headers":{},
                      "upstreamService":"static",
                      "upstreamPath":""
                    }
                  ]
                }
                """;
        RouteSnapshotCodec codec = new RouteSnapshotCodec();
        RouteTableSnapshot snapshot = codec.decode(json);
        assertEquals("v3", snapshot.version());
        assertEquals(1, snapshot.routes().size());
        assertEquals("static", snapshot.routes().get(0).upstreamService());
    }
}
