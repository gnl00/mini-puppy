package one.mini.server._3netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.internal.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import one.mini.anno.ReqPath;
import one.mini.domain.netty.PPNettyRequest;
import one.mini.domain.netty.PPNettyResponse;
import one.mini.classloader.ExternalClassLoader;
import one.mini.servlet.ServletRegistry;
import one.mini.servlet.TestNettyServlet;
import one.mini.utils.AnnotationUtils;
import one.mini.utils.InnerHTMLUtil;

import javax.servlet.http.HttpServlet;
import java.io.File;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.channels.Selector;
import java.nio.file.Paths;
import java.util.ServiceLoader;

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
                            ch.pipeline()
                                    .addLast(new StringDecoder())
                                    .addLast(new StringEncoder())
                                    // .addLast(new LineBasedFrameDecoder(1024))
                                    // .addLast(new LineEncoder())
                                    // .addLast(new CorsHandler(CorsConfigBuilder.forAnyOrigin().build()))
                                    .addLast(new PPChannelStringRequestHandler())
                            ;
                        }
                    });
            initServletMapping();
            String rootPath = getClass().getClassLoader().getResource("").getPath();
            String externalJarFilename = "mini-puppy-1.0-SNAPSHOT.jar";
            loadExternalJar(Paths.get(rootPath, externalJarFilename).toString());
            ChannelFuture future = serverBootstrap.bind(port);
            log.info("[server] - server started at port: {}", port);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("[server] - server error", e);
        }
    }

    public static class PPChannelStringRequestHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String requestStr) throws Exception {
            log.info("[server] - received from client: {}", requestStr);
            PPNettyRequest request = new PPNettyRequest(requestStr);
            if ("/".equals(request.getUrl())) {
                ctx.writeAndFlush(InnerHTMLUtil.htmlResponse("<h1>Welcome - puppy-server base on netty-4.2-final</h1>"));
                return;
            }
            if (null == ServletRegistry.getServlet(request.getUrl())) {
                ctx.writeAndFlush(InnerHTMLUtil.htmlResponse("<h1>404 Not Found</h1>"));
                return;
            }
            ServletRegistry.getServlet(request.getUrl()).service(request, new PPNettyResponse(ctx));
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("[server] - read from http request error, channel: {}", ctx.channel(), cause);
            ctx.close();
        }
    }

    public void initServletMapping() {
        ServletRegistry.registerServlet("/test-netty", new TestNettyServlet());
    }

    /**
     * 利用自定义 classloader 加载外部的 servlet jar 包，并注册 servlet
     */
    public void loadExternalJar(String filePath) {
        if (StringUtil.isNullOrEmpty(filePath) || !filePath.endsWith(".jar")) {
            return;
        }
        ExternalClassLoader ecl = new ExternalClassLoader(new URL[]{});
        try {
            ecl.addURL(new File(filePath).toURL());
        } catch (MalformedURLException e) {
            log.error("[server] - load external jar error, transform file to url error", e);
        }
        registerServlets(ecl);
    }

    public void registerServlets(ClassLoader cl) {
        /*
         * 需要设置当前线程的 classloader 为我们自定义的 classloader，否则会报 java.lang.NoClassDefFoundError: javax/servlet/ServletException
         */
        Thread.currentThread().setContextClassLoader(cl);
        ServiceLoader<HttpServlet> services = ServiceLoader.load(HttpServlet.class);
        for (HttpServlet service : services) {
            if (AnnotationUtils.isAnnotationPresent(service.getClass(), one.mini.anno.ReqPath.class)) {
                ReqPath reqPath = AnnotationUtils.getAnnotation(service.getClass(), ReqPath.class);
                for (String p : reqPath.path()) {
                    ServletRegistry.registerServlet(p, service);
                    log.info("[server] - registered path: {} servlet: {}", p, service.toString());
                }
            }
        }
        // 操作完成还原当前线程的 classloader
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }

    public static void main(String[] args) {
        log.info("[main] ready to start server");
        new PPNettyServer("localhost", 5555).startSync();
    }
}
