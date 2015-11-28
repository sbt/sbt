package coursier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Bootstrap {

    static void exit(String message) {
        System.err.println(message);
        System.exit(255);
    }

    static byte[] readFullySync(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[16384];

        int nRead = is.read(data, 0, data.length);
        while (nRead != -1) {
            buffer.write(data, 0, nRead);
            nRead = is.read(data, 0, data.length);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    final static String usage = "Usage: bootstrap main-class JAR-directory JAR-URLs...";

    final static int concurrentDownloadCount = 6;

    public static void main(String[] args) throws Throwable {

        ThreadFactory threadFactory = new ThreadFactory() {
            // from scalaz Strategy.DefaultDaemonThreadFactory
            ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
            public Thread newThread(Runnable r) {
                Thread t = defaultThreadFactory.newThread(r);
                t.setDaemon(true);
                return t;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(concurrentDownloadCount, threadFactory);

        boolean prependClasspath = false;

        if (args.length > 0 && args[0].equals("-B"))
            prependClasspath = true;

        if (args.length < 2 || (prependClasspath && args.length < 3)) {
            exit(usage);
        }

        int offset = 0;
        if (prependClasspath)
            offset += 1;

        String mainClass0 = args[offset];
        String jarDir0 = args[offset + 1];

        List<String> remainingArgs = new ArrayList<>();
        for (int i = offset + 2; i < args.length; i++)
            remainingArgs.add(args[i]);

        final File jarDir = new File(jarDir0);

        if (jarDir.exists()) {
            if (!jarDir.isDirectory())
                exit("Error: " + jarDir0 + " is not a directory");
        } else if (!jarDir.mkdirs())
            System.err.println("Warning: cannot create " + jarDir0 + ", continuing anyway.");

        int splitIdx = remainingArgs.indexOf("--");
        List<String> jarStrUrls;
        List<String> userArgs;

        if (splitIdx < 0) {
            jarStrUrls = remainingArgs;
            userArgs = new ArrayList<>();
        } else {
            jarStrUrls = remainingArgs.subList(0, splitIdx);
            userArgs = remainingArgs.subList(splitIdx + 1, remainingArgs.size());
        }

        List<String> errors = new ArrayList<>();
        List<URL> urls = new ArrayList<>();

        for (String urlStr : jarStrUrls) {
            try {
                URL url = URI.create(urlStr).toURL();
                urls.add(url);
            } catch (Exception ex) {
                String message = urlStr + ": " + ex.getMessage();
                errors.add(message);
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder builder = new StringBuilder("Error parsing " + errors.size() + " URL(s):");
            for (String error: errors) {
                builder.append('\n');
                builder.append(error);
            }
            exit(builder.toString());
        }

        CompletionService<URL> completionService =
                new ExecutorCompletionService<>(pool);

        List<URL> localURLs = new ArrayList<>();

        for (URL url : urls) {
            if (!url.getProtocol().equals("file")) {
                final URL url0 = url;

                completionService.submit(new Callable<URL>() {
                    @Override
                    public URL call() throws Exception {
                        String path = url0.getPath();
                        int idx = path.lastIndexOf('/');
                        // FIXME Add other components in path to prevent conflicts?
                        String fileName = path.substring(idx + 1);
                        File dest = new File(jarDir, fileName);

                        if (!dest.exists()) {
                            System.err.println("Downloading " + url0);
                            try {
                                URLConnection conn = url0.openConnection();
                                long lastModified = conn.getLastModified();
                                InputStream s = conn.getInputStream();
                                byte[] b = readFullySync(s);
                                Files.write(dest.toPath(), b);
                                dest.setLastModified(lastModified);
                            } catch (Exception e) {
                                System.err.println("Error while downloading " + url0 + ": " + e.getMessage() + ", ignoring it");
                                throw e;
                            }
                        }

                        return dest.toURI().toURL();
                    }
                });
            } else {
                localURLs.add(url);
            }
        }

        try {
            while (localURLs.size() < urls.size()) {
                Future<URL> future = completionService.take();
                try {
                    URL url = future.get();
                    localURLs.add(url);
                } catch (ExecutionException ex) {
                    // Error message already printed from the Callable above
                    System.exit(255);
                }
            }
        } catch (InterruptedException ex) {
            exit("Interrupted");
        }

        Thread thread = Thread.currentThread();
        ClassLoader parentClassLoader = thread.getContextClassLoader();

        URLClassLoader classLoader = new URLClassLoader(localURLs.toArray(new URL[localURLs.size()]), parentClassLoader);

        Class<?> mainClass = null;
        Method mainMethod = null;

        try {
            mainClass = classLoader.loadClass(mainClass0);
        } catch (ClassNotFoundException ex) {
            exit("Error: class " + mainClass0 + " not found");
        }

        try {
            Class params[] = { String[].class };
            mainMethod = mainClass.getMethod("main", params);
        }
        catch (NoSuchMethodException ex) {
            exit("Error: main method not found in class " + mainClass0);
        }

        List<String> userArgs0 = new ArrayList<>();

        if (prependClasspath) {
            for (URL url : localURLs) {
                assert url.getProtocol().equals("file");
                userArgs0.add("-B");
                userArgs0.add(url.getPath());
            }
        }

        userArgs0.addAll(userArgs);

        thread.setContextClassLoader(classLoader);
        try {
            Object mainArgs[] = { userArgs0.toArray(new String[userArgs0.size()]) };
            mainMethod.invoke(null, mainArgs);
        }
        catch (IllegalAccessException ex) {
            exit(ex.getMessage());
        }
        catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
        finally {
            thread.setContextClassLoader(parentClassLoader);
        }
    }

}
