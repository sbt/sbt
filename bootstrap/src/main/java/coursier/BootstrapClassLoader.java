package coursier;

import java.net.URL;
import java.net.URLClassLoader;

public class BootstrapClassLoader extends URLClassLoader {

    public BootstrapClassLoader(
            URL[] urls,
            ClassLoader parent
    ) {
        super(urls, parent);
    }

    /**
     * Can be called by reflection by launched applications, to find the "main" `ClassLoader`
     * that loaded them, and possibly short-circuit it to load other things for example.
     *
     * The `launch` command of coursier does that.
     */
    public boolean isBootstrapLoader() {
        return true;
    }

}
