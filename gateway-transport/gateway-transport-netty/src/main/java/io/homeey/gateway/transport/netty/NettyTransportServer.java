package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.GatewayRequestHandler;
import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.homeey.gateway.transport.api.HttpResponseMessage;
import io.homeey.gateway.transport.api.TransportServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

public final class NettyTransportServer implements TransportServer {
    private final int port;
    private final GatewayRequestHandler requestHandler;
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;

    public NettyTransportServer(int port) {
        this(port, NettyTransportServer::defaultHandler);
    }

    public NettyTransportServer(int port, GatewayRequestHandler requestHandler) {
        this.port = port;
        this.requestHandler = requestHandler;
    }

    @Override
    public CompletionStage<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        this.bossGroup = boss;
        this.workerGroup = worker;

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(10 * 1024 * 1024));
                        ch.pipeline().addLast(new InboundHandler(requestHandler));
                    }
                });

        bootstrap.bind("0.0.0.0", port).addListener((ChannelFuture bindFuture) -> {
            if (!bindFuture.isSuccess()) {
                shutdownQuietly(boss, worker);
                future.completeExceptionally(bindFuture.cause());
                return;
            }
            this.serverChannel = bindFuture.channel();
            future.complete(null);
        });
        return future;
    }

    @Override
    public CompletionStage<Void> stop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Channel currentChannel = this.serverChannel;
        EventLoopGroup currentBoss = this.bossGroup;
        EventLoopGroup currentWorker = this.workerGroup;
        this.serverChannel = null;
        this.bossGroup = null;
        this.workerGroup = null;

        if (currentBoss == null && currentWorker == null) {
            future.complete(null);
            return future;
        }

        Runnable shutdownAction = () -> shutdownAsync(currentBoss, currentWorker, future);
        if (currentChannel != null) {
            currentChannel.close().addListener(ignored -> shutdownAction.run());
        } else {
            shutdownAction.run();
        }
        return future;
    }

    private static void shutdownAsync(EventLoopGroup boss, EventLoopGroup worker, CompletableFuture<Void> future) {
        int waitCount = 0;
        if (boss != null) {
            waitCount++;
        }
        if (worker != null) {
            waitCount++;
        }
        if (waitCount == 0) {
            future.complete(null);
            return;
        }

        AtomicInteger counter = new AtomicInteger(waitCount);
        if (boss != null) {
            boss.shutdownGracefully().addListener(ignored -> {
                if (counter.decrementAndGet() == 0) {
                    future.complete(null);
                }
            });
        }
        if (worker != null) {
            worker.shutdownGracefully().addListener(ignored -> {
                if (counter.decrementAndGet() == 0) {
                    future.complete(null);
                }
            });
        }
    }

    private static void shutdownQuietly(EventLoopGroup boss, EventLoopGroup worker) {
        if (boss != null) {
            boss.shutdownGracefully();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }
    }

    private static CompletionStage<HttpResponseMessage> defaultHandler(HttpRequestMessage request) {
        String path = request.path() == null ? "" : request.path().toLowerCase();
        if (path.startsWith("/ping") || path.contains("ping")) {
            return CompletableFuture.completedFuture(new HttpResponseMessage(
                    200,
                    Map.of("content-type", "text/plain; charset=UTF-8"),
                    "ok".getBytes(StandardCharsets.UTF_8)
            ));
        }
        return CompletableFuture.completedFuture(new HttpResponseMessage(
                404,
                Map.of("content-type", "text/plain; charset=UTF-8"),
                "not found".getBytes(StandardCharsets.UTF_8)
        ));
    }

    static HttpRequestMessage toRequestMessage(FullHttpRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.headers().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
        String host = request.headers().get(HttpHeaderNames.HOST, "");

        String rawUri = request.uri();
        String path = rawUri;
        String query = "";
        int queryIndex = rawUri.indexOf('?');
        if (queryIndex >= 0) {
            path = rawUri.substring(0, queryIndex);
            if (queryIndex + 1 < rawUri.length()) {
                query = rawUri.substring(queryIndex + 1);
            }
        }
        if (path != null && !path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }

        byte[] body = new byte[request.content().readableBytes()];
        request.content().getBytes(request.content().readerIndex(), body);
        return new HttpRequestMessage(
                request.method().name(),
                host == null ? "" : host,
                path,
                query,
                headers,
                body
        );
    }

    static FullHttpResponse internalErrorResponse() {
        byte[] body = "internal error".getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Unpooled.wrappedBuffer(body)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
        return response;
    }

    private static final class InboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final GatewayRequestHandler requestHandler;

        private InboundHandler(GatewayRequestHandler requestHandler) {
            this.requestHandler = requestHandler;
        }

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, FullHttpRequest msg) {
            FullHttpRequest request = msg.retainedDuplicate();
            HttpRequestMessage requestMessage;
            try {
                requestMessage = toRequestMessage(request);
            } finally {
                request.release();
            }

            requestHandler.handle(requestMessage).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    writeAndClose(ctx, internalErrorResponse());
                    return;
                }
                writeAndClose(ctx, NettyResponseAdapter.fromMessage(response));
            });
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            writeAndClose(ctx, internalErrorResponse());
        }

        private static void writeAndClose(io.netty.channel.ChannelHandlerContext ctx, FullHttpResponse response) {
            ctx.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        }
    }
}
