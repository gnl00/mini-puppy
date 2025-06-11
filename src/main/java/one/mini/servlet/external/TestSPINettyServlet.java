package one.mini.servlet.external;

import one.mini.anno.ReqPath;
import one.mini.domain.netty.PPNettyResponse;
import one.mini.utils.InnerHTMLUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@ReqPath(path = {"/test-spi-netty"}, method = "GET")
public class TestSPINettyServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        PPNettyResponse response = (PPNettyResponse) resp;
        response.send(InnerHTMLUtil.textResponse("GET response from mini-puppy base on netty TestSPINettyServlet"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        PPNettyResponse response = (PPNettyResponse) resp;
        response.send(InnerHTMLUtil.textResponse("POST response from mini-puppy base on netty TestSPINettyServlet"));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getMethod().equals("GET")) {
            doGet(req, resp);
        } else {
            doPost(req, resp);
        }
    }
}
