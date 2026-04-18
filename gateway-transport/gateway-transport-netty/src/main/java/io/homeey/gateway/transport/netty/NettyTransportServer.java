package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.TransportServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class NettyTransportServer implements TransportServer {
    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyTransportServer(int port) {
        this.port = port;
    }

    @Override
    public CompletionStage<Void> start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        CompletableFuture<Void> startFuture = new CompletableFuture<>();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, FullHttpRequest msg) {
                                NettyRequestAdapter request = new NettyRequestAdapter(msg);
                                String body = "/ping".equals(request.path()) ? "ok" : "not found";
                                var response = NettyResponseAdapter.okText(body);
                                if (!"/ping".equals(request.path())) {
                                    response.setStatus(io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND);
                                }
                                boolean keepAlive = HttpUtil.isKeepAlive(msg);
                                if (keepAlive) {
                                    response.headers().set(io.netty.handler.codec.http.HttpHeaderNames.CONNECTION, "keep-alive");
                                }
                                var writeFuture = ctx.writeAndFlush(response);
                                if (!keepAlive || msg.protocolVersion().equals(HttpVersion.HTTP_1_0)) {
                                    writeFuture.addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        });
                    }
                });

        bootstrap.bind(port).addListener((io.netty.channel.ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                serverChannel = future.channel();
                startFuture.complete(null);
            } else {
                startFuture.completeExceptionally(future.cause());
            }
        });

        return startFuture;
    }

    @Override
    public CompletionStage<Void> stop() {
        CompletableFuture<Void> stopFuture = new CompletableFuture<>();
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();

        if (serverChannel != null) {
            serverChannel.close().addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    closeFuture.complete(null);
                } else {
                    closeFuture.completeExceptionally(f.cause());
                }
            });
        } else {
            closeFuture.complete(null);
        }

        closeFuture.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                shutdownGroups();
                stopFuture.completeExceptionally(throwable);
                return;
            }
            shutdownGroups();
            stopFuture.complete(null);
        });
        return stopFuture;
    }

    private void shutdownGroups() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}
