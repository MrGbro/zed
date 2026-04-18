package io.homeey.gateway.core;

import io.homeey.gateway.core.route.RadixRouteLocator;
import io.homeey.gateway.core.route.RouteDefinition;
import io.homeey.gateway.core.route.RouteTableSnapshot;
import io.homeey.gateway.plugin.api.PolicySet;
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
                Map.of("x-env", "prod"),
                "order-service",
                "/orders",
                List.of(),
                new PolicySet(Map.of())
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
                Map.of("x-env", "prod"),
                "order-service",
                "/orders",
                List.of(),
                new PolicySet(Map.of())
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

    @Test
    void shouldPreferLongestPrefixRoute() {
        RouteDefinition routeShort = new RouteDefinition(
                "route-short",
                "api.example.com",
                "/orders",
                "GET",
                Map.of(),
                "order-service",
                "/orders",
                List.of(),
                new PolicySet(Map.of())
        );
        RouteDefinition routeLong = new RouteDefinition(
                "route-long",
                "api.example.com",
                "/orders/v2",
                "GET",
                Map.of(),
                "order-service-v2",
                "/orders/v2",
                List.of(),
                new PolicySet(Map.of())
        );

        RouteTableSnapshot snapshot = new RouteTableSnapshot("v1", List.of(routeShort, routeLong));
        RadixRouteLocator locator = new RadixRouteLocator(snapshot);

        Optional<RouteDefinition> match = locator.locate(
                "api.example.com",
                "/orders/v2/items",
                "GET",
                Map.of()
        );

        assertTrue(match.isPresent());
        assertEquals("route-long", match.get().id());
    }

    @Test
    void shouldMatchAfterCompressedNodeSplit() {
        RouteDefinition routeA = new RouteDefinition(
                "route-a",
                "api.example.com",
                "/orders-v1",
                "GET",
                Map.of(),
                "order-service-v1",
                "/orders-v1",
                List.of(),
                new PolicySet(Map.of())
        );
        RouteDefinition routeB = new RouteDefinition(
                "route-b",
                "api.example.com",
                "/orders-v2",
                "GET",
                Map.of(),
                "order-service-v2",
                "/orders-v2",
                List.of(),
                new PolicySet(Map.of())
        );

        RouteTableSnapshot snapshot = new RouteTableSnapshot("v1", List.of(routeA, routeB));
        RadixRouteLocator locator = new RadixRouteLocator(snapshot);

        Optional<RouteDefinition> matchA = locator.locate(
                "api.example.com",
                "/orders-v1/item",
                "GET",
                Map.of()
        );
        Optional<RouteDefinition> matchB = locator.locate(
                "api.example.com",
                "/orders-v2/item",
                "GET",
                Map.of()
        );

        assertTrue(matchA.isPresent());
        assertEquals("route-a", matchA.get().id());
        assertTrue(matchB.isPresent());
        assertEquals("route-b", matchB.get().id());
    }
}
