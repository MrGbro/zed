package io.homeey.gateway.core;

import io.homeey.gateway.plugin.api.GatewayFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiContractCompileTest {

    @Test
    void shouldCompileWithGatewayFilterContract() {
        assertNotNull(GatewayFilter.class);
    }
}
