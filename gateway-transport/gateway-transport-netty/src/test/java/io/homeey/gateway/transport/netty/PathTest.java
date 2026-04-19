package io.homeey.gateway.transport.netty;

import io.homeey.gateway.transport.api.HttpRequestMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * Manual smoke entry for Netty URI path parsing.
 */
public class PathTest {
    public static void main(String[] args) {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
                        ch.pipeline().addLast(new io.netty.channel.SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, FullHttpRequest req) {
                                HttpRequestMessage message = NettyTransportServer.toRequestMessage(req);
                                System.out.println("=== Request Info ===");
                                System.out.println("Full URI: " + req.uri());
                                System.out.println("Path: " + message.path());
                                System.out.println("Path starts with '/': " + message.path().startsWith("/"));
                                System.out.println("===================");
                                ctx.writeAndFlush(NettyResponseAdapter.okText(
                                        "URI: " + req.uri() + "\n" +
                                                "Path: " + message.path() + "\n" +
                                                "Starts with /: " + message.path().startsWith("/")
                                )).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
                            }
                        });
                    }
                });

        ChannelFuture bind = bootstrap.bind(9999).syncUninterruptibly();
        System.out.println("Server started on port 9999");
        System.out.println("Test URLs:");
        System.out.println("  - http://localhost:9999/test");
        System.out.println("  - http://localhost:9999/api/users");
        System.out.println("  - http://localhost:9999/");

        bind.channel().closeFuture().syncUninterruptibly();
        boss.shutdownGracefully();
        worker.shutdownGracefully();
    }
}
