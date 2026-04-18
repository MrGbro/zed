package io.homeey.gateway.bootstrap.wiring;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.transport.api.TransportServer;
import io.homeey.gateway.transport.netty.NettyTransportServer;

import java.util.Objects;
import java.util.function.IntFunction;

public final class RuntimeFactory {
    private final IntFunction<TransportServer> nettyBuilder;

    public RuntimeFactory() {
        this(NettyTransportServer::new);
    }

    public RuntimeFactory(IntFunction<TransportServer> nettyBuilder) {
        this.nettyBuilder = Objects.requireNonNull(nettyBuilder, "nettyBuilder");
    }

    public TransportServer createTransport(BootstrapConfig config) {
        if ("netty".equalsIgnoreCase(config.transportType())) {
            return nettyBuilder.apply(config.port());
        }
        throw new IllegalArgumentException("Unsupported transport type: " + config.transportType());
    }
}
