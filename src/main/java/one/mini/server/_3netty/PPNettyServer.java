package one.mini.server._3netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import one.mini.servlet.ServletRegistry;
import one.mini.servlet.TestServlet;
import one.mini.utils.InnerHTMLUtil;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
@Data
public class PPNettyServer {

    private ServerSocket ss;
    private String host;
    private int port;

    private Selector selector;

    public PPNettyServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public synchronized void startSync() {
        Thread th = new Thread(this::start);
        th.start();
    }

    public void initServletMapping() {
        ServletRegistry.registerServlet("/test", new TestServlet());
    }

    public static class PPChannelRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buffer = (ByteBuf) msg;
            log.info("[server] - received from client: {}", buffer.toString(Charset.defaultCharset()));
            ctx.write(ByteBuffer.wrap(InnerHTMLUtil.htmlResponse("hello from mini-puppy server based on netty").getBytes(StandardCharsets.UTF_8)));
            ctx.flush();
            /*ctx.writeAndFlush(ByteBuffer.wrap(InnerHTMLUtil.htmlResponse("hello from mini-puppy server based on netty").getBytes(StandardCharsets.UTF_8)))
                    .addListener(ChannelFutureListener.CLOSE); // Close the channel after sending the response*/
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.close();
        }
    }

    public void start() {
        try (
                MultiThreadIoEventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
                MultiThreadIoEventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        ) {
            // base on netty
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new PPChannelRequestHandler());
                        }
                    });

            initServletMapping();

            ChannelFuture future = serverBootstrap.bind(port);
            log.info("[server] - server started at port: {}", port);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("[server] - server error", e);
        }
    }

    public static void main(String[] args) {
        log.info("[main] ready to start server");
        new PPNettyServer("localhost", 5555).startSync();
    }
}
