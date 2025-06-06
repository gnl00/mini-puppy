package one.mini.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.InputStream;

@EqualsAndHashCode(callSuper = true)
public class PPRequest extends AbstractPPRequest {

    @Getter
    private String url;
    @Getter
    private String method;
    private final InputStream inputStream;

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
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}