package one.mini.classloader;

import java.net.URL;
import java.net.URLClassLoader;

public class ExternalWebClassLoader extends URLClassLoader {
    public ExternalWebClassLoader(URL[] urls) {
        super(urls);
    }

    public void addURL(URL url) {
        super.addURL(url);
    }
}
