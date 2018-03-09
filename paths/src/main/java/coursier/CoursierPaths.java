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

        File baseXdgDir = new File(coursierDirectories.cacheDir);
        File xdgDir = new File(baseXdgDir, "v1");
        String xdgPath = xdgDir.getAbsolutePath();

        if (path == null) {
            if (baseXdgDir.isDirectory())
              path = xdgPath;
        }

        if (path == null) {
            File coursierDotFile = new File(System.getProperty("user.home") + "/.coursier");
            if (coursierDotFile.isDirectory())
                path = System.getProperty("user.home") + "/.coursier/cache/v1/";
        }

        if (path == null) {
            path = xdgPath;
            xdgDir.mkdirs();
        }

        return new File(path).getAbsoluteFile();
    }

    public static File cacheDirectory() {

        if (cacheDirectory0 == null)
            synchronized (lock) {
                if (cacheDirectory0 == null) {
                    coursierDirectories = ProjectDirectories.from(null, null, "Coursier");
                    cacheDirectory0 = computeCacheDirectory();
                }
            }

        return cacheDirectory0;
    }
}
