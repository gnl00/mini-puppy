package one.mini.servlet.external;

import one.mini.anno.ReqPath;
import one.mini.domain.netty.PPNettyResponse;
import one.mini.utils.InnerHTMLUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@ReqPath(path = {"/register", "/register-netty"}, method = "POST")
public class RegisterNettyServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        PPNettyResponse response = (PPNettyResponse) resp;
        response.send(InnerHTMLUtil.textResponse("GET response from mini-puppy base on netty RegisterNettyServlet"));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getMethod().equals("POST")) {
            doPost(req, resp);
        }
    }
}
