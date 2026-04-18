package io.homeey.gateway.bootstrap.config;

public record BootstrapConfig(String transportType, int port) {

    public static BootstrapConfig of(String transportType, int port) {
        return new BootstrapConfig(transportType, port);
    }
}
