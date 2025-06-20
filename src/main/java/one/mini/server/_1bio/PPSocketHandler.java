package one.mini.server._1bio;

import lombok.extern.slf4j.Slf4j;
import one.mini.domain.bio.PPRequest;
import one.mini.domain.bio.PPResponse;
import one.mini.servlet.ServletRegistry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class PPSocketHandler {

    private static final ExecutorService EXECUTORS = Executors.newFixedThreadPool(20);

    public void handle(Socket socket) {
        EXECUTORS.execute(() -> doHandle(socket));
    }

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

    public String findMappingStaticsHtml(String name) {
        return "";
    }

}
