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

## Reference

- https://github.com/houbb/minicat?tab=readme-ov-file