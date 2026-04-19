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
 * 手动冒烟测试入口，用于验证Netty URI路径解析行为。
 * <p>
 * 该测试类启动一个独立的Netty HTTP服务器（端口9999），用于验证：
 * <ul>
 *   <li>{@link FullHttpRequest#uri()} 返回的完整URI格式</li>
 *   <li>{@link NettyTransportServer#toRequestMessage(FullHttpRequest)} 转换后的路径是否包含前导斜杠</li>
 *   <li>不同请求路径（如 /test、/api/users、/）的处理结果</li>
 * </ul>
 * </p>
 * <p>
 * 使用方法：
 * <pre>{@code
 * // 1. 运行 main 方法启动测试服务器
 * // 2. 访问以下URL进行测试：
 * //    - http://localhost:9999/test
 * //    - http://localhost:9999/api/users
 * //    - http://localhost:9999/
 * // 3. 观察控制台输出和浏览器响应，验证路径格式
 * }</pre>
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/19
 */
public class PathTest {
    /**
     * 启动测试服务器并验证路径解析行为。
     * <p>
     * 该方法创建一个Netty HTTP服务器，监听9999端口，对每个请求：
     * <ul>
     *   <li>打印完整的URI和转换后的路径信息</li>
     *   <li>检查路径是否以斜杠开头</li>
     *   <li>返回包含这些信息的HTTP响应</li>
     * </ul>
     * </p>
     *
     * @param args 命令行参数（未使用）
     */
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
