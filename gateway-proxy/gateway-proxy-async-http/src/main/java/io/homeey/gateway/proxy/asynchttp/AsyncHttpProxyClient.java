package io.homeey.gateway.proxy.asynchttp;

import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyRequest;
import io.homeey.gateway.proxy.api.ProxyResponse;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.RequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class AsyncHttpProxyClient implements ProxyClient {
    private final AsyncHttpClient client;

    public AsyncHttpProxyClient() {
        this(new DefaultAsyncHttpClient());
    }

    public AsyncHttpProxyClient(AsyncHttpClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<ProxyResponse> execute(ProxyRequest request) {
        RequestBuilder builder = new RequestBuilder(request.method()).setUrl(request.url());
        if (request.headers() != null) {
            request.headers().forEach(builder::addHeader);
        }
        if (request.body() != null && request.body().length > 0) {
            builder.setBody(request.body());
        }

        CompletableFuture<ProxyResponse> future = new CompletableFuture<>();
        client.executeRequest(builder.build()).toCompletableFuture()
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        return;
                    }
                    Map<String, String> headers = new LinkedHashMap<>();
                    response.getHeaders().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
                    future.complete(new ProxyResponse(
                            response.getStatusCode(),
                            headers,
                            response.getResponseBodyAsBytes()
                    ));
                });
        return future;
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception ignored) {
            // Ignore close failure during shutdown path.
        }
    }
}
