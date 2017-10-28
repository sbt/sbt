package coursier;

import java.io.File;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.nio.channels.FileLock;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache paths logic, shared by the cache and bootstrap modules
 */
public class CachePath {

    // based on https://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
    // '/' was removed from the unsafe list
    private static String escape(String input) {
        StringBuilder resultStr = new StringBuilder();
        for (char ch : input.toCharArray()) {
            if (isUnsafe(ch)) {
                resultStr.append('%');
                resultStr.append(toHex(ch / 16));
                resultStr.append(toHex(ch % 16));
            } else {
                resultStr.append(ch);
            }
        }
        return resultStr.toString();
    }

    private static char toHex(int ch) {
        return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
    }

    private static boolean isUnsafe(char ch) {
        return ch > 128 || " %$&+,:;=?@<>#%".indexOf(ch) >= 0;
    }

    public static File localFile(String url, File cache, String user) throws MalformedURLException {

        // use the File constructor accepting a URI in case of problem with the two cases below?

        if (url.startsWith("file:///"))
            return new File(url.substring("file://".length()));

        if (url.startsWith("file:/"))
            return new File(url.substring("file:".length()));

        String[] split = url.split(":", 2);
        if (split.length != 2)
            throw new MalformedURLException("No protocol found in URL " + url);

        String protocol = split[0];
        String remaining = split[1];

        if (remaining.startsWith("///"))
            remaining = remaining.substring("///".length());
        else if (remaining.startsWith("/"))
            remaining = remaining.substring("/".length());
        else
            throw new MalformedURLException("URL " + url + " doesn't contain an absolute path");

        if (remaining.endsWith("/"))
            // keeping directory content in .directory files
            remaining = remaining + ".directory";

        while (remaining.startsWith("/"))
            remaining = remaining.substring(1);

        String userPart = "";
        if (user != null)
            userPart = user + "@";

        return new File(cache, escape(protocol + "/" + userPart + remaining));
    }

    public static File temporaryFile(File file) {
        File dir = file.getParentFile();
        String name = file.getName();
        return new File(dir, name + ".part");
    }

    public static File lockFile(File file) {
        return new File(file.getParentFile(), file.getName() + ".lock");
    }

    public static File defaultCacheDirectory() {
        return CoursierPaths.cacheDirectory();
    }

    private static ConcurrentHashMap<File, Object> processStructureLocks = new ConcurrentHashMap<File, Object>();

    public static <V> V withStructureLock(File cache, Callable<V> callable) throws Exception {

        Object intraProcessLock = processStructureLocks.get(cache);

        if (intraProcessLock == null) {
            Object lock = new Object();
            Object prev = processStructureLocks.putIfAbsent(cache, lock);
            if (prev == null)
                intraProcessLock = lock;
            else
                intraProcessLock = prev;
        }

        synchronized (intraProcessLock) {
            File lockFile = new File(cache, ".structure.lock");
            lockFile.getParentFile().mkdirs();
            FileOutputStream out = null;

            try {
                out = new FileOutputStream(lockFile);

                FileLock lock = null;
                try {
                    lock = out.getChannel().lock();

                    try {
                        return callable.call();
                    }
                    finally {
                        lock.release();
                        lock = null;
                        out.close();
                        out = null;
                        lockFile.delete();
                    }
                }
                finally {
                    if (lock != null) lock.release();
                }
            } finally {
                if (out != null) out.close();
            }
        }
    }

}
