package coursier;

import java.net.URL;
import java.net.URLClassLoader;

public class IsolatedClassLoader extends URLClassLoader {

    private String[] isolationTargets;

    public IsolatedClassLoader(
            URL[] urls,
            ClassLoader parent,
            String[] isolationTargets
    ) {
        super(urls, parent);
        this.isolationTargets = isolationTargets;
    }

    /**
     * Applications wanting to access an isolated `ClassLoader` should inspect the hierarchy of
     * loaders, and look into each of them for this method, by reflection. Then they should
     * call it (still by reflection), and look for an agreed in advance target in it. If it is found,
     * then the corresponding `ClassLoader` is the one with isolated dependencies.
     */
    public String[] getIsolationTargets() {
        return isolationTargets;
    }

}
