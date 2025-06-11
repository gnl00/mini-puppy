package one.mini.servlet.external;

import one.mini.anno.ReqPath;
import one.mini.domain.netty.PPNettyResponse;
import one.mini.utils.InnerHTMLUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@ReqPath(path = {"/doc", "/document"}, method = "GET")
public class DocumentNettyServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        PPNettyResponse response = (PPNettyResponse) resp;
        response.send(InnerHTMLUtil.textResponse("GET response from mini-puppy base on netty DocumentNettyServlet"));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getMethod().equals("GET")) {
            doGet(req, resp);
        }
    }
}
