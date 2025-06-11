package one.mini.domain.netty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import one.mini.domain.AbstractPPRequest;

@EqualsAndHashCode(callSuper = true)
public class PPNettyRequest extends AbstractPPRequest {

    @Getter
    private String url;
    @Getter
    private String method;
    @Getter
    private String host;
    @Getter
    private Integer port;
    private final String request;

    public PPNettyRequest(String request) {
        this.request = request;
        this.readFromRequest(request);
    }

    public void readFromRequest(String reqStr) {
        if (reqStr.contains("\n")) {
            String[] lines = reqStr.split("\r\n");
            String[] protocols = lines[0].split(" ");
            method = protocols[0];
            url = protocols[1];
            String[] hosts = lines[1].split(" ");
            String[] hp = hosts[1].split(":");
            this.host = hp[0];
            this.port = Integer.parseInt(hp[1].trim());
        }
    }
}