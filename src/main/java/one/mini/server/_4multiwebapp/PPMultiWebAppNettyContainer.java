package one.mini.server._4multiwebapp;

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
import one.mini.classloader.ExternalWebClassLoader;
import one.mini.domain.netty.PPNettyRequest;
import one.mini.domain.netty.PPNettyResponse;
import one.mini.servlet.PPWebServletContext;
import one.mini.servlet.ServletRegistry;
import one.mini.utils.AnnotationUtils;
import one.mini.utils.InnerHTMLUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.channels.Selector;
import java.nio.file.Paths;
import java.util.ServiceLoader;

@Slf4j
@Data
public class PPMultiWebAppNettyContainer {

    private ServerSocket ss;
    private String host;
    private int defaultPort = 8080;

    private Selector selector;

    public PPMultiWebAppNettyContainer(String host, int defaultPort) {
        this.host = host;
        this.defaultPort = defaultPort;
    }

    public synchronized void startAsync() {
        Thread th = new Thread(this::init);
        th.start();
    }

    public void init() {
        String rootPath = getClass().getClassLoader().getResource("").getPath();
        String externalJarFilename = "mini-puppy-1.0-SNAPSHOT.jar";
        String externalJarFilename2 = "mini-puppy-1.0-SNAPSHOT.jar";
        int port1 = 5555; // 假设从 externalJar1 配置文件中解析出 port=5555
        int port2 = 5566; // 假设从 externalJar2 配置文件中解析出 port=5566
        loadExternalWebApp(Paths.get(rootPath, externalJarFilename).toString(), port1);
        new Thread(() -> startWebServer(port1)).start();
        loadExternalWebApp(Paths.get(rootPath, externalJarFilename2).toString(), port2);
        new Thread(() -> startWebServer(port2)).start();
    }

    public void startWebServer(int port) {
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
                                    .addLast(new PPServletDispatcher())
                            ;
                        }
                    });
            ChannelFuture future = serverBootstrap.bind(port);
            log.info("[server] - server started at port: {}", port);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("[server] - server error", e);
        }
    }

    public static class PPServletDispatcher extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String requestStr) throws Exception {
            log.info("[server] - received from client: {}", requestStr);
            dispatch(ctx, new PPNettyRequest(requestStr));
        }

        private void dispatch(ChannelHandlerContext ctx, PPNettyRequest request) throws ServletException, IOException {
            if ("/".equals(request.getUrl())) {
                ctx.writeAndFlush(InnerHTMLUtil.htmlResponse("<h1>Welcome - puppy-server base on netty-4.2-final</h1>"));
                return;
            }
            PPWebServletContext webContext = ServletRegistry.getWebContext(request.getPort());
            HttpServlet servlet = webContext.getServlet(request.getUrl());
            if (null == servlet) {
                ctx.writeAndFlush(InnerHTMLUtil.htmlResponse("<h1>404 Not Found</h1>"));
                return;
            }
            servlet.service(request, new PPNettyResponse(ctx));
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

    /**
     * 利用自定义 classloader 加载外部的 servlet jar 包，并注册 servlet
     */
    public void loadExternalWebApp(String filePath, int port) {
        if (StringUtil.isNullOrEmpty(filePath) || !filePath.endsWith(".jar")) {
            return;
        }
        /**
         * 每 load 一次 webapp 都需要一个新的 classloader，避免遇到相同的 servlet url 冲突
         */
        ExternalWebClassLoader ecl = new ExternalWebClassLoader(new URL[]{});
        try {
            File file = new File(filePath);
            if (file.exists()) {
                ecl.addURL(file.toURL());
            } else {
                log.info("[server] - load external jar failed, file not exists: {}", filePath);
                return;
            }
        } catch (MalformedURLException e) {
            log.error("[server] - load external jar error, get file url error", e);
        }
        PPWebServletContext webCtx = new PPWebServletContext(ecl);
        registerServlets(ecl, webCtx);
        ServletRegistry.addWebContext(port, webCtx);
    }

    public void registerServlets(ClassLoader cl, PPWebServletContext webCtx) {
        /*
         * 需要设置当前线程的 classloader 为我们自定义的 classloader，否则会报 java.lang.NoClassDefFoundError: javax/servlet/ServletException
         */
        Thread.currentThread().setContextClassLoader(cl);
        ServiceLoader<HttpServlet> services = ServiceLoader.load(HttpServlet.class);
        for (HttpServlet service : services) {
            if (AnnotationUtils.isAnnotationPresent(service.getClass(), ReqPath.class)) {
                ReqPath reqPath = AnnotationUtils.getAnnotation(service.getClass(), ReqPath.class);
                for (String p : reqPath.path()) {
                    webCtx.registerServlet(p, service);
                    log.info("[server] - registered path: {} servlet: {}", p, service.toString());
                }
            }
        }
        // 操作完成还原当前线程的 classloader
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }

    public static void main(String[] args) {
        log.info("[main] ready to start server");
        new PPMultiWebAppNettyContainer("localhost", 5566).startAsync();
    }
}
