package one.mini.servlet;

import one.mini.domain.bio.PPRequest;
import one.mini.domain.bio.PPResponse;
import one.mini.utils.InnerHTMLUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        PPResponse response = (PPResponse) resp;
        response.write(InnerHTMLUtil.textResponse("GET response from mini-puppy"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        PPResponse response = (PPResponse) resp;
        response.write(InnerHTMLUtil.textResponse("POST response from mini-puppy"));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        PPRequest request = (PPRequest) req;
        if (request.getMethod().equals("GET")) {
            doGet(req, resp);
        } else {
            doPost(req, resp);
        }
    }
}
