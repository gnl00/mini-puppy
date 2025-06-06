package one.mini;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import one.mini.servlet.ServletRegistry;
import one.mini.servlet.TestServlet;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@Slf4j
@Data
public class PPServer {

    private ServerSocket ss;
    private String host;
    private int port;

    public PPServer(String host, int port) {
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

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new java.net.InetSocketAddress(host, port), 128); // backlog 表示处理请求的队列长度
            log.info("[server] - server started, host {} port {}", host, port);
            this.ss = serverSocket;
            PPSocketHandler socketHandler = new PPSocketHandler();
            initServletMapping();
            while (true) {
                Socket socket = serverSocket.accept();
                log.info("[server] - client {}:{} connected", socket.getInetAddress(), socket.getPort());
                socketHandler.handle(socket);
                /*OutputStream outputStream = socket.getOutputStream();
                String response = """
                        HTTP/1.1 200 OK\r
                        Content-Type: text/plain\r
                        \r
                        hello from mini-puppy""";
                outputStream.write(response.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                socket.close();*/
            }
        } catch (IOException e) {
            log.error("[server] - start error", e);
        }
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

    public static void main(String[] args) {
        log.info("[main] ready to start server");
        new PPServer("localhost", 5555).startSync();
    }
}
