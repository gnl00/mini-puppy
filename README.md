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



## Reference

- https://github.com/houbb/minicat?tab=readme-ov-file