package one.mini.classloader;

import java.net.URL;
import java.net.URLClassLoader;

public class ExternalClassLoader extends URLClassLoader {
    public ExternalClassLoader(URL[] urls) {
        super(urls);
    }

    public void addURL(URL url) {
        super.addURL(url);
    }
}
