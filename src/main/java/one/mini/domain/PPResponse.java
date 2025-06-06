package one.mini.domain;

import lombok.EqualsAndHashCode;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@EqualsAndHashCode(callSuper = true)
public class PPResponse extends AbstractPPResponse {

    private final OutputStream outputStream;

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

    public void write(String content) {
        try {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}