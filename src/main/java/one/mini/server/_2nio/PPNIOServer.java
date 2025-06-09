package one.mini.server._2nio;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import one.mini.servlet.ServletRegistry;
import one.mini.servlet.TestServlet;
import one.mini.utils.InnerHTMLUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Data
public class PPNIOServer {

    private ServerSocket ss;
    private String host;
    private int port;

    private Selector selector;

    public PPNIOServer(String host, int port) {
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

                /*StringBuilder sb = new StringBuilder();
                int receivedLen = 0;
                while (readLen > 0) {
                    receivedLen += readLen;
                    buffer.flip(); // 切换到读模式再读取数据
                    while (buffer.hasRemaining()) {
                        sb.append(Charset.defaultCharset().decode(buffer));
                    }
                    buffer.clear();
                    readLen = clientChannel.read(buffer);
                }
                TimeUnit.MILLISECONDS.sleep(10);
                log.info("[server] - received DONE");
                clientChannel.close();
                log.info("[server] - client {} closed", clientChannel);
                log.info("[server] - received from client len: {} content: {}", receivedLen, sb);*/

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

    public static void main(String[] args) {
        log.info("[main] ready to start server");
        new PPNIOServer("localhost", 5555).startSync();
    }
}
