package io.homeey.gateway.core;

import io.homeey.gateway.core.route.RadixRouteLocator;
import io.homeey.gateway.core.route.RouteDefinition;
import io.homeey.gateway.core.route.RouteTableSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadixRouteLocatorTest {

    @Test
    void shouldMatchByHostThenPathThenMethod() {
        RouteDefinition route = new RouteDefinition(
                "route-1",
                "api.example.com",
                "/orders",
                "GET",
                Map.of("x-env", "prod")
        );

        RouteTableSnapshot snapshot = new RouteTableSnapshot("v1", List.of(route));
        RadixRouteLocator locator = new RadixRouteLocator(snapshot);

        Optional<RouteDefinition> match = locator.locate(
                "api.example.com",
                "/orders/100",
                "GET",
                Map.of("x-env", "prod", "x-trace", "abc")
        );

        assertTrue(match.isPresent());
        assertEquals("route-1", match.get().id());
    }

    @Test
    void shouldReturnNotFoundWhenNoRouteMatches() {
        RouteDefinition route = new RouteDefinition(
                "route-1",
                "api.example.com",
                "/orders",
                "GET",
                Map.of("x-env", "prod")
        );

        RouteTableSnapshot snapshot = new RouteTableSnapshot("v1", List.of(route));
        RadixRouteLocator locator = new RadixRouteLocator(snapshot);

        Optional<RouteDefinition> match = locator.locate(
                "api.example.com",
                "/users",
                "GET",
                Map.of("x-env", "prod")
        );

        assertTrue(match.isEmpty());
    }
}
