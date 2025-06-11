# mini-puppy
> mini tomcat again...

## MiniTomcat

### 作用

核心工作：
- 接受请求
- 处理请求
- 响应请求

### StepByStep

1、首先需要一个服务器，使用 Java 中的 ServerSocket 即可。

```java
package one.mini;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
@Slf4j
@Data
public class Server {
    private String host;
    private int port;
    public Server(String host, int port) {
        this.host = host;
        this.port = port;
    }
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new java.net.InetSocketAddress(host, port), 128); // backlog 表示处理请求的队列长度
            log.info("[server] - started host {} port {}", host, port);
            while (true) {
                //  1、接受请求
                Socket socket = serverSocket.accept();
                log.info("[server] - client {}:{} connected", socket.getInetAddress(), socket.getPort());
                // 2、处理请求
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write("hello from mini-puppy".getBytes(StandardCharsets.UTF_8));
                socket.close();
            }
        } catch (IOException e) {
            log.error("[server] - start error", e);
        }
    }
    public static void main(String[] args) {
        new Server("localhost", 5555).start();
    }
}
```

在上面这个小 demo 中我们就已经完成了接收请求和处理请求这两步了。

接下来从浏览器打开 http://localhost:5555 访问我们的服务。期望的情况是我们能在浏览器页面上看到 `hello from mini-puppy`。

但是并没有，浏览器显示

```
localhost 发送的响应无效。
ERR_INVALID_HTTP_RESPONSE
```

**为什么呢？**

因为 HTTP 响应是需要按照指定格式来组装数据返回的，上面的字符串并没有遵循 HTTP 规定的响应报文格式。

让我们修改一下

```java
String response = "HTTP/1.1 200 OK\r\n" +
        "Content-Type: text/plain\r\n" +
        "\r\n" +
        "hello from mini-puppy";
outputStream.write(response.getBytes(StandardCharsets.UTF_8));
```

现在，重启服务，再从浏览器访问 http://localhost:5555 就发现页面上已经能正常显示了。

### 不阻塞主线程

为了不阻塞主线程，将 server start 操作放到其他线程中，同时加上服务器 close 方法

```java
public synchronized void startSync() {
    Thread th = new Thread(this::start);
    th.start();
}

public void close() {
    if (null != ss) {
        try {
            ss.close();
        } catch (IOException e) {
            log.error("[server] server close ERROR", e);
        }
    }
}
```

### 拆分处理线程

目前处理请求的操作都是放在 server 线程中的，当有多个请求过来时，就会导致 server 线程被阻塞。可以将处理操作放到其他线程中处理。

### 封装 Request 和 Response

```java
public class PPRequest {
    private String url;
    private String method;
    private InputStream inputStream;
    public PPRequest(InputStream inputStream) {
        this.inputStream = inputStream;
        this.readFromStream(inputStream);
    }
    public void readFromStream(InputStream inputStream) {
        byte[] bytes = new byte[1024];
        try {
            int len = -1;
            while ((len = inputStream.read(bytes)) != -1) {
                String request = new String(bytes, 0, len);
                if (request.contains("\n")) {
                    String[] requestLines = request.split("\r\n");
                    String[] lines = requestLines[0].split(" ");
                    method = lines[0];
                    url = lines[1];
                    // log.info("[server] - read from request stream method={} url={}", method, url);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

public class PPResponse {
    private OutputStream outputStream;
    public PPResponse(OutputStream outputStream) {
        this.outputStream = outputStream;
    }
    public void write(byte[] bytes) {
        try {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 响应静态资源文件

现在我们已经可以拿到请求的方法和 url 了，下面让我们根据 URL 来返回对应的静态资源文件。

```java
if ("GET".equals(ppRequest.getMethod()) && url.endsWith(".html")) {
    String classesPath = Objects.requireNonNull(getClass().getClassLoader().getResource("")).getPath();
    byte[] bytes = Files.readAllBytes(Path.of(classesPath, url));
    log.info("[server] - static file content: {}", new String(bytes, StandardCharsets.UTF_8));
    ppResponse.write(InnerHTMLUtil.htmlResponse(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
} else {
    ppResponse.write(InnerHTMLUtil.httpResponse("hello from mini-puppy").getBytes(StandardCharsets.UTF_8));
}
```

根据 url 来判断是否是静态资源文件，如果是则读取文件内容。返回静态文件的方式也很简单粗暴，直接输出到 OutputStream 中即可。

### Servlet支持

接触过 spring-mvc 至少会知道 servlet 是和一个 url 相对应的。url 请求过来之后我们要找到支持处理这条 url 的 servlet，用对应的 servlet 处理对应的请求。

那我们就需要一个 url 和 servlet 对应的容器。在这里我们就使用 Map 来实现。

```java
public class ServletRegistry {
    private static final Map<String, HttpServlet> SERVLET_HOLDER = new ConcurrentHashMap<>(32);
    public static void registerServlet(String url, HttpServlet servlet) {
        SERVLET_HOLDER.put(url, servlet);
    }
    public static HttpServlet getServlet(String url) {
        return SERVLET_HOLDER.get(url);
    }
}
```

通过上面这个类我们就实现了 url 和 servlet 的对应关系。支持 servlet 注册和查找。其实这就是类似下面的 `web.xml`

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<web-app>

    <servlet>
        <servlet-name>test</servlet-name>
        <servlet-class>one.mini.servlet.TestServlet</servlet-class>
    </servlet>

    <servlet-mapping>
        <servlet-name>test</servlet-name>
        <url-pattern>/test</url-pattern>
    </servlet-mapping>

</web-app>
```

上面这段 xml 的意思就是通过 xml 来配置注册一个 servlet，并和 url 映射起来。

我们的小 demo 还是怎么简单怎么来，直接从代码中注册即可。

```java
public void initServletMapping() {
    ServletRegistry.registerServlet("/test", new TestServlet());
    ServletRegistry.registerServlet("/aaa", new AAAServlet());
    ServletRegistry.registerServlet("/bbb", new BBBServlet());
}
```

只要在服务启动的时候调用上面的方法将对应的 url 和 servlet 注册即可。在处理请求的时候我们就可以使用到对应的 servlet 来处理了。

```java
public void doHandle(Socket socket) {
    try {
        PPRequest ppRequest = new PPRequest(socket.getInputStream());
        log.info("[server] - processing request for client {}:{} method={} url={}", socket.getInetAddress(), socket.getPort(), ppRequest.getMethod(), ppRequest.getUrl());
        OutputStream outputStream = socket.getOutputStream();
        PPResponse ppResponse = new PPResponse(outputStream);
        String url = ppRequest.getUrl();

        // 3 使用 servlet
        HttpServlet servlet = ServletRegistry.getServlet(url);
        servlet.service(ppRequest, ppResponse);

        // 2 抽象 request 和 response
        /*if ("GET".equals(ppRequest.getMethod()) && url.endsWith(".html")) {
            String classesPath = Objects.requireNonNull(getClass().getClassLoader().getResource("")).getPath();
            byte[] bytes = Files.readAllBytes(Path.of(classesPath, url));
            log.info("[server] - static file content: {}", new String(bytes, StandardCharsets.UTF_8));
            // URL resource = getClass().getClassLoader().getResource(url);
            // Files.readAllBytes(Path.of(resource.getPath()));
            // String htmlFile = findMappingStaticsHtml(url);
            ppResponse.write(InnerHTMLUtil.htmlResponse(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        } else {
            ppResponse.write(InnerHTMLUtil.httpResponse("hello from mini-puppy").getBytes(StandardCharsets.UTF_8));
        }*/

        // 1 手动处理
        /*String response = """
                        HTTP/1.1 200 OK\r
                        Content-Type: text/plain\r
                        \r
                        hello from mini-puppy""";
        outputStream.write(response.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();*/

        socket.close();
    } catch (IOException | ServletException e) {
        log.error("[server] - process client request error", e);
    }
}
```

至此，简单的请求处理和 servlet 处理都基本完成了，接下来会看一下之前的程序有什么可以优化的地方？

其实刚开始的时候我们已经小小的优化过一波了：1、抽离启动线程；2、使用多线程处理多个请求；

前面的优化逻辑都是从减少阻塞，增大并发量出发的。此外我们还可以发现，目前使用的 ServerSocket 是基于 BIO，那么从 IO 层面出发常见的优化方向还有使用 NIO/AIO 或者使用 Netty 来作为基建服务等手段。

### NIO/Netty

基于 NIO 修改服务端：

```java
public void start() {
    try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
        ssc.bind(new java.net.InetSocketAddress(host, port), 128); // backlog 表示处理请求的队列长度
        ssc.configureBlocking(false);

        selector = Selector.open();
        ssc.register(selector, SelectionKey.OP_ACCEPT);

        log.info("[server] - server started, host {} port {}", host, port);
        initServletMapping();
        while (true) {
            int select = selector.select();
            if (select == 0) continue;
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                if (selectionKey.isAcceptable()) {
                    handleAccept(selectionKey);
                }
                if (selectionKey.isReadable()) {
                    handleReadAsync(selectionKey);
                }
            }
        }
    } catch (IOException e) {
        log.error("[server] - start error", e);
    }
}

private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(32);

public void handleAccept(SelectionKey selectionKey) {
    try {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        log.info("[server] - client {} connected", clientChannel);
    } catch (IOException e) {
        log.error("[server] - handle ACCEPT event error", e);
    }
}
public void handleReadAsync(SelectionKey selectionKey) throws IOException {
    EXECUTOR_SERVICE.execute(() -> {
        try {
            SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            if (null == clientChannel) return;
            int readLen = clientChannel.read(buffer);
            if (readLen > 0) {
                buffer.flip(); // 切换到读模式再读取数据
                String readableContent = Charset.defaultCharset().decode(buffer).toString();
                log.info("[server] - received from client: {}", readableContent);
                /*String[] lines = readableContent.split("\n");
                String[] line = lines[0].split(" ");
                String method = line[0];
                String url = line[1];
                // 拿到这些信息之后就可以和之前一样根据 url 处理 servlet
                */
                clientChannel.write(ByteBuffer.wrap(InnerHTMLUtil.htmlResponse("hello from mini-puppy server based on nio").getBytes(StandardCharsets.UTF_8)));
                TimeUnit.MILLISECONDS.sleep(100);
                log.info("[server] - received DONE");
                clientChannel.close();
                log.info("[server] - client {} closed", clientChannel);
            }

        } catch (IOException | RuntimeException | InterruptedException e) {
            log.error("[server] - handle READ event error", e);
        }
    });
}
public void handleRead(SelectionKey selectionKey) throws IOException {
    try {
        SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int readLen = clientChannel.read(buffer);
        if (readLen > 0) {
            buffer.flip(); // 切换到读模式再读取数据
            log.info("[server] - received from client: {}", Charset.defaultCharset().decode(buffer));
            clientChannel.write(ByteBuffer.wrap(InnerHTMLUtil.htmlResponse("hello from mini-puppy server based on nio").getBytes(StandardCharsets.UTF_8)));
        } else {
            clientChannel.close();
            log.info("[server] - client {} closed", clientChannel);
        }
    } catch (IOException e) {
        log.error("[server] - handle READ event error", e);
    }
}
```

基于 NIO 的代码比之前的 BIO 复杂了许多，涉及到 Selector/Channel/ByteBuffer。使用 Netty 的话代码就好理解多了。

```java
public static class PPChannelRequestHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buffer = (ByteBuf) msg;
        log.info("[server] - received from client: {}", buffer.toString(Charset.defaultCharset()));
        ctx.write(ByteBuffer.wrap(InnerHTMLUtil.htmlResponse("hello from mini-puppy server based on netty").getBytes(StandardCharsets.UTF_8)));
        ctx.flush();
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
```

可以看到，相比于 NIO，我们需要编写的代码少了很多，很快就能实现一个服务。

### 加载 Jar 包

在前面的代码中，我们已经实现了内部 servlet 的加载，但是想想，我们在使用 tomcat 的时候往往都是加载自己自定义的 war/jar 包的，这块逻辑如何实现呢？

简单一点出发，我们可能会想到 SPI，加载外部类。

```java
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
    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
}
```

这样就完成加载外部 jar 包的逻辑了。这里的 SPI 文件就相当于 tomcat 中需要的 web.xml 文件，里面存放了 servlet 的信息。

`resources/META-INF/services/javax.servlet.http.HttpServlet`

...

```
one.mini.servlet.external.TestSPINettyServlet
one.mini.servlet.external.AboutNettyServlet
one.mini.servlet.external.DocumentNettyServlet
one.mini.servlet.external.RegisterNettyServlet
```

为了简化操作，使用了一个自定义注解 @ReqPath 来注册 servlet 的路径，这样在加载 jar 包的时候，只需要扫描这个注解即可。

```java
@ReqPath(path = {"/test-spi-netty"}, method = "GET")
public class TestSPINettyServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        PPNettyResponse response = (PPNettyResponse) resp;
        response.send(InnerHTMLUtil.textResponse("GET response from mini-puppy base on netty TestSPINettyServlet"));
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        PPNettyResponse response = (PPNettyResponse) resp;
        response.send(InnerHTMLUtil.textResponse("POST response from mini-puppy base on netty TestSPINettyServlet"));
    }
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getMethod().equals("GET")) {
            doGet(req, resp);
        } else {
            doPost(req, resp);
        }
    }
}
```

目前硬编码了外部 jar 的路径为 target/classes/xxx.jar，只需要 `mvn clean & package` 后启动 netty 服务器即可测试。

### 关于 SPI

SPI 的原理其实很简单，就是通过 `META-INF/services/` 文件夹下的文件，来加载类。但是如果我们需要加载很多个 Servlet 类，就需要把所有需要加载的全限定类名写进去，这样维护起来可能有点不方便。

能不能使用一个通配符来实现呢？比如 `one.mini.servlet.external.*`。 查了一下发现 Java 本身是不支持在 SPI 中使用通配符的。

**SPI 的设计规范**
> 根据 Java SPI 的实现规则，META-INF/services/ 下的配置文件需要明确列出所有实现类的全限定名，不能使用通配符（如 .*）或模糊匹配 。
> 这是为了确保在运行时能够精确加载所有已声明的服务实现。

**通配符的适用场景**
> 通配符（如 * 或 ?）通常用于文件系统路径匹配或正则表达式，但在 SPI 的配置文件中，其语法规则仅支持直接的类名字符串。
> SPI 的加载逻辑依赖 ServiceLoader 类，它会按行读取并尝试加载每个类名，若遇到 .* 会将其视为非法类名并抛出异常。

如果需要批量加载某个包下的所有实现类，可以通过以下方式实现：
- 手动维护配置文件 ：显式列出所有实现类（推荐标准做法）。
- 自定义扩展加载器 ：参考 Dubbo SPI 或 Apache ShenYu 的 SPI 扩展机制，结合注解（如 @SPI）和包扫描能力实现动态加载。
- 构建时生成配置 ：通过构建工具（如 Maven 插件）在编译阶段自动生成 META-INF/services/ 文件内容。

### 多 WebApp 加载支持

前面用到了自定义的 classloader 来加载外部的 webapp，测试情况下我们只加载了一个 webapp，但是 tomcat 通常是可以启动多个 webapp 的。

如果要加载多个 webapp，那就需要考虑到 webapp 隔离的问题。

只使用一个单例 classloader 来加载，如果遇到多个 webapp 使用同一个 servlet 名的或者多个 webapp 使用同一条 url 名字的话就会遇到冲突，同类名的 servlet 类只能被加载一次。

在这里我们的做法可以是每加载一个新的 webapp，都创建一个新的 classloader，这样加载的 servlet 类就不会冲突了。并且将 classloader 和 servlet 映射都保存到一个 WebAppContext 中。

这样子一来，我们的 PPMultiWebAppNettyServer 就可以兼容多个 webapp 了。此外 PPMultiWebAppNettyServer 也应该改名了，它的内部现在可以包含多个 WebServer，可以叫 PPMultiWebAppNettyContainer 了。

Tomcat 做得还要再复杂，具体可以参考 [Tomcat是如何隔离Web应用的？](https://houbb.github.io/2016/11/07/web-server-tomcat-07-hand-write-war)

### Filter/Listener

Filter/Listener 的实现也简单，只要在服务启动前注册一下，等到请求来了，调用对应的类和处理方法就可以了。

### 后续

上文按照 tomcat 的基本功能实现了一个小服务，结构上来说不是那么的“Tomcat”，如果想要从结构上也学习一下 Tomcat 的实现，可以参考廖雪峰老师的 [手写 Tomcat](https://liaoxuefeng.com/books/jerrymouse/introduction/index.html)

## Reference

- https://github.com/houbb/minicat