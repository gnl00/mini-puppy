package one.mini;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Slf4j
@Data
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