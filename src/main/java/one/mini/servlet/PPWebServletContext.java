package one.mini.servlet;

import lombok.Data;
import one.mini.classloader.ExternalWebClassLoader;

import javax.servlet.http.HttpServlet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class PPWebServletContext {

    private final Map<String, HttpServlet> servletsMap = new ConcurrentHashMap<>(64);
    private ExternalWebClassLoader cl;

    public PPWebServletContext(ExternalWebClassLoader cl) {
        this.cl = cl;
    }

    public void registerServlet(String url, HttpServlet servlet) {
        servletsMap.put(url, servlet);
    }

    public HttpServlet getServlet(String url) {
        return servletsMap.get(url);
    }
}
