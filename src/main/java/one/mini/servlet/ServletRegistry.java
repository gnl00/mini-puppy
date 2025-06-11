package one.mini.servlet;

import javax.servlet.http.HttpServlet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServletRegistry {

    private static final Map<String, HttpServlet> SERVLET_HOLDER = new ConcurrentHashMap<>(32);

    public static void registerServlet(String url, HttpServlet servlet) {
        SERVLET_HOLDER.put(url, servlet);
    }

    public static HttpServlet getServlet(String url) {
        return SERVLET_HOLDER.get(url);
    }

    private static final Map<Integer, PPWebServletContext> WEB_CONTEXT_HOLDER = new ConcurrentHashMap<>(64);

    public static PPWebServletContext getWebContext(Integer port) {
        return WEB_CONTEXT_HOLDER.get(port);
    }

    public static void addWebContext(Integer port, PPWebServletContext context) {
        WEB_CONTEXT_HOLDER.put(port, context);
    }

}
