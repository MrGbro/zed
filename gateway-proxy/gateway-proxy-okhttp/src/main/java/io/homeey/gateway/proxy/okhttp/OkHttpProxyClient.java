package io.homeey.gateway.proxy.okhttp;

import io.homeey.gateway.proxy.api.ProxyClient;
import io.homeey.gateway.proxy.api.ProxyRequest;
import io.homeey.gateway.proxy.api.ProxyResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class OkHttpProxyClient implements ProxyClient {
    private final OkHttpClient client;

    public OkHttpProxyClient() {
        this(new OkHttpClient());
    }

    public OkHttpProxyClient(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<ProxyResponse> execute(ProxyRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Request.Builder builder = new Request.Builder().url(request.url());
            if (request.headers() != null) {
                request.headers().forEach(builder::addHeader);
            }
            byte[] body = request.body() == null ? new byte[0] : request.body();
            RequestBody requestBody = body.length == 0
                    ? null
                    : RequestBody.create(body, MediaType.parse("application/octet-stream"));
            builder.method(request.method(), requestBody);

            try (Response response = client.newCall(builder.build()).execute()) {
                Map<String, String> headers = new LinkedHashMap<>();
                response.headers().toMultimap().forEach((key, value) -> {
                    if (!value.isEmpty()) {
                        headers.put(key, value.get(0));
                    }
                });
                byte[] responseBody = response.body() == null ? new byte[0] : response.body().bytes();
                return new ProxyResponse(response.code(), headers, responseBody);
            } catch (IOException e) {
                throw new IllegalStateException("OkHttp proxy request failed", e);
            }
        });
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
