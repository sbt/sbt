package coursier;

import java.io.File;

import io.github.soc.directories.ProjectDirectories;

/**
 * Computes Coursier's directories according to the standard
 * defined by operating system Coursier is running on.
 *
 * @implNote If more paths e. g. for configuration or application data is required,
 * use {@link #coursierDirectories} and do not roll your own logic.
 */
public final class CoursierPaths {
    private CoursierPaths() {
        throw new Error();
    }

    private static ProjectDirectories coursierDirectories;

    private static volatile File cacheDirectory0 = null;

    private static final Object lock = new Object();

    // TODO After switching to nio, that logic can be unit tested with mock filesystems.

    private static File computeCacheDirectory() {
        String path = System.getenv("COURSIER_CACHE");

        if (path == null)
            path = System.getProperty("coursier.cache");

        String xdgPath = coursierDirectories.projectCacheDir;
        File xdgDir = new File(xdgPath);

        if (path == null) {
            if (xdgDir.isDirectory())
              path = xdgPath;
        }

        if (path == null) {
            File coursierDotFile = new File(System.getProperty("user.home") + "/.coursier");
            if (coursierDotFile.isDirectory())
                path = System.getProperty("user.home") + "/.coursier/cache/";
        }

        if (path == null) {
            path = xdgPath;
            xdgDir.mkdirs();
        }

        File coursierCacheDirectory = new File(path).getAbsoluteFile();

        if (coursierCacheDirectory.getName().equals("v1"))
            coursierCacheDirectory = coursierCacheDirectory.getParentFile();

        return coursierCacheDirectory;
    }

    public static File cacheDirectory() {

        if (cacheDirectory0 == null)
            synchronized (lock) {
                if (cacheDirectory0 == null) {
                    coursierDirectories = ProjectDirectories.fromProjectName("Coursier");
                    cacheDirectory0 = computeCacheDirectory();
                }
            }

        return cacheDirectory0;
    }
}
