package coursier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    final static String defaultURLResource = "bootstrap-jar-urls";
    final static String isolationIDsResource = "bootstrap-isolation-ids";

    static String[] readStringSequence(String resource) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            return new String[] {};
        byte[] rawContent = readFullySync(is);
        String content = new String(rawContent, "UTF-8");
        if (content.length() == 0)
            return new String[] {};
        return content.split("\n");
    }

    static Map<String, URL[]> readIsolationContexts(File jarDir, String[] isolationIDs) throws IOException {
        final Map<String, URL[]> perContextURLs = new LinkedHashMap<>();

        for (String isolationID: isolationIDs) {
            String[] contextURLs = readStringSequence("bootstrap-isolation-" + isolationID + "-jar-urls");
            List<URL> urls = new ArrayList<>();
            for (String strURL : contextURLs) {
                URL url = new URL(strURL);
                File local = localFile(jarDir, url);
                if (local.exists())
                    urls.add(local.toURI().toURL());
                else
                    System.err.println("Warning: " + local + " not found.");
            }
            perContextURLs.put(isolationID, urls.toArray(new URL[urls.size()]));
        }

        return perContextURLs;
    }

    final static int concurrentDownloadCount = 6;

    // http://stackoverflow.com/questions/872272/how-to-reference-another-property-in-java-util-properties/27724276#27724276
    public static Map<String,String> loadPropertiesMap(InputStream s) throws IOException {
        final Map<String, String> ordered = new LinkedHashMap<>();
        //Hack to use properties class to parse but our map for preserved order
        Properties bp = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                ordered.put((String)key, (String)value);
                return super.put(key, value);
            }
        };
        bp.load(s);


        final Pattern propertyRegex = Pattern.compile(Pattern.quote("${") + "[^" + Pattern.quote("{[()]}") + "]*" + Pattern.quote("}"));

        final Map<String,String> resolved = new LinkedHashMap<>(ordered.size());

        for (String k : ordered.keySet()) {
            String value = ordered.get(k);

            Matcher matcher = propertyRegex.matcher(value);

            // cycles would loop indefinitely here :-|
            while (matcher.find()) {
                int start = matcher.start(0);
                int end = matcher.end(0);
                String subKey = value.substring(start + 2, end - 1);
                String subValue = resolved.get(subKey);
                if (subValue == null)
                    subValue = System.getProperty(subKey);
                value = value.substring(0, start) + subValue + value.substring(end);
            }

            resolved.put(k, value);
        }
        return resolved;
    }

    static String mainJarPath() {
        ProtectionDomain protectionDomain = Bootstrap.class.getProtectionDomain();
        if (protectionDomain != null) {
            CodeSource source = protectionDomain.getCodeSource();
            if (source != null) {
                URL location = source.getLocation();
                if (location != null && location.getProtocol().equals("file")) {
                    return location.getPath();
                }
            }
        }

        return "";
    }

    static File localFile(File jarDir, URL url) {
        if (url.getProtocol().equals("file"))
            return new File(url.getPath());

        String path = url.getPath();
        int idx = path.lastIndexOf('/');
        // FIXME Add other components in path to prevent conflicts?
        String fileName = path.substring(idx + 1);
        return new File(jarDir, fileName);
    }


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

        System.setProperty("coursier.mainJar", mainJarPath());

        for (int i = 0; i < args.length; i++) {
            System.setProperty("coursier.main.arg-" + i, args[i]);
        }

        Map<String,String> properties = loadPropertiesMap(Thread.currentThread().getContextClassLoader().getResourceAsStream("bootstrap.properties"));
        for (Map.Entry<String, String> ent : properties.entrySet()) {
            System.setProperty(ent.getKey(), ent.getValue());
        }

        String mainClass0 = System.getProperty("bootstrap.mainClass");
        String jarDir0 = System.getProperty("bootstrap.jarDir");

        final File jarDir = new File(jarDir0);

        if (jarDir.exists()) {
            if (!jarDir.isDirectory())
                exit("Error: " + jarDir0 + " is not a directory");
        } else if (!jarDir.mkdirs())
            System.err.println("Warning: cannot create " + jarDir0 + ", continuing anyway.");

        String[] jarStrUrls = readStringSequence(defaultURLResource);

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
                        File dest = localFile(jarDir, url0);

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

        final String[] isolationIDs = readStringSequence(isolationIDsResource);
        final Map<String, URL[]> perIsolationContextURLs = readIsolationContexts(jarDir, isolationIDs);

        Thread thread = Thread.currentThread();
        ClassLoader parentClassLoader = thread.getContextClassLoader();

        for (String isolationID: isolationIDs) {
            URL[] contextURLs = perIsolationContextURLs.get(isolationID);
            parentClassLoader = new IsolatedClassLoader(contextURLs, parentClassLoader, new String[]{ isolationID });
        }

        ClassLoader classLoader = new BootstrapClassLoader(localURLs.toArray(new URL[localURLs.size()]), parentClassLoader);

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

        for (int i = 0; i < args.length; i++)
            userArgs0.add(args[i]);

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
