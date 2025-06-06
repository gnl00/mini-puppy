package one.mini;

import lombok.Data;

import java.io.OutputStream;

@Data
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