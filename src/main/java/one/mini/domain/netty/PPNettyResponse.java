package one.mini.domain.netty;

import io.netty.channel.ChannelHandlerContext;
import lombok.EqualsAndHashCode;
import one.mini.domain.AbstractPPResponse;

@EqualsAndHashCode(callSuper = true)
public class PPNettyResponse extends AbstractPPResponse {

    private final ChannelHandlerContext ctx;

    public PPNettyResponse(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public void send(String content) {
        ctx.writeAndFlush(content);
    }

}