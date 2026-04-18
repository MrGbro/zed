package io.homeey.gateway.proxy.api;

import java.util.concurrent.CompletionStage;

public interface ProxyClient extends AutoCloseable {
    CompletionStage<ProxyResponse> execute(ProxyRequest request);

    @Override
    void close();
}
