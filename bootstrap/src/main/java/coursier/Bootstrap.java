package coursier;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    final static String defaultJarResource = "bootstrap-jar-resources";
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

    static Map<String, URL[]> readIsolationContexts(File jarDir, String[] isolationIDs, String bootstrapProtocol, ClassLoader loader) throws IOException {
        final Map<String, URL[]> perContextURLs = new LinkedHashMap<String, URL[]>();

        for (String isolationID: isolationIDs) {
            String[] strUrls = readStringSequence("bootstrap-isolation-" + isolationID + "-jar-urls");
            String[] resources = readStringSequence("bootstrap-isolation-" + isolationID + "-jar-resources");
            List<URL> urls = getURLs(strUrls, resources, bootstrapProtocol, loader);
            List<URL> localURLs = getLocalURLs(urls, jarDir, bootstrapProtocol);

            perContextURLs.put(isolationID, localURLs.toArray(new URL[localURLs.size()]));
        }

        return perContextURLs;
    }

    final static int concurrentDownloadCount = 6;

    // http://stackoverflow.com/questions/872272/how-to-reference-another-property-in-java-util-properties/27724276#27724276
    public static Map<String,String> loadPropertiesMap(InputStream s) throws IOException {
        final Map<String, String> ordered = new LinkedHashMap<String, String>();
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

        final Map<String, String> resolved = new LinkedHashMap<String, String>(ordered.size());

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

    // from http://www.java2s.com/Code/Java/File-Input-Output/Readfiletobytearrayandsavebytearraytofile.htm
    static void writeBytesToFile(File file, byte[] bytes) throws IOException {
        BufferedOutputStream bos = null;

        try {
            FileOutputStream fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bytes);
        } finally {
            if (bos != null) {
                try  {
                    // flush and close the BufferedOutputStream
                    bos.flush();
                    bos.close();
                } catch (Exception e) {}
            }
        }
    }

    static List<URL> getLocalURLs(List<URL> urls, final File jarDir, String bootstrapProtocol) throws MalformedURLException {

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

        CompletionService<URL> completionService =
                new ExecutorCompletionService<URL>(pool);

        List<URL> localURLs = new ArrayList<URL>();
        List<URL> missingURLs = new ArrayList<URL>();

        for (URL url : urls) {

            String protocol = url.getProtocol();

            if (protocol.equals("file") || protocol.equals(bootstrapProtocol)) {
                localURLs.add(url);
            } else {
                File dest = localFile(jarDir, url);

                if (dest.exists()) {
                    localURLs.add(dest.toURI().toURL());
                } else {
                    missingURLs.add(url);
                }
            }
        }

        final Random random = new Random();
        for (final URL url : missingURLs) {
            completionService.submit(new Callable<URL>() {
                @Override
                public URL call() throws Exception {
                    File dest = localFile(jarDir, url);

                    if (!dest.exists()) {
                        try {
                            URLConnection conn = url.openConnection();
                            long lastModified = conn.getLastModified();
                            InputStream s = conn.getInputStream();
                            byte[] b = readFullySync(s);
                            File tmpDest = new File(dest.getParentFile(), dest.getName() + "-" + random.nextInt());
                            tmpDest.deleteOnExit();
                            writeBytesToFile(tmpDest, b);
                            Files.move(tmpDest.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
                            dest.setLastModified(lastModified);
                        } catch (Exception e) {
                            System.err.println("Error while downloading " + url + ": " + e.getMessage() + ", ignoring it");
                            throw e;
                        }
                    }

                    return dest.toURI().toURL();
                }
            });
        }

        String clearLine = "\033[2K";

        try {
            while (localURLs.size() < urls.size()) {
                Future<URL> future = completionService.take();
                try {
                    URL url = future.get();
                    localURLs.add(url);
                    int nowMissing = urls.size() - localURLs.size();
                    String up = "\033[1A";
                    System.err.print(clearLine + "Downloaded " + (missingURLs.size() - nowMissing) + " missing file(s) / " + missingURLs.size() + "\n" + up);
                } catch (ExecutionException ex) {
                    // Error message already printed from the Callable above
                    System.exit(255);
                }
            }
        } catch (InterruptedException ex) {
            exit("Interrupted");
        }

        System.err.print(clearLine);

        return localURLs;
    }

    static void setMainProperties(String mainJarPath, String[] args) {
        System.setProperty("coursier.mainJar", mainJarPath);

        for (int i = 0; i < args.length; i++) {
            System.setProperty("coursier.main.arg-" + i, args[i]);
        }
    }

    static void setExtraProperties(String resource) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        Map<String,String> properties = loadPropertiesMap(loader.getResourceAsStream(resource));
        for (Map.Entry<String, String> ent : properties.entrySet()) {
            System.setProperty(ent.getKey(), ent.getValue());
        }
    }

    static List<URL> getURLs(String[] rawURLs, String[] resources, String bootstrapProtocol, ClassLoader loader) throws MalformedURLException {

        List<String> errors = new ArrayList<String>();
        List<URL> urls = new ArrayList<URL>();

        for (String urlStr : rawURLs) {
            try {
                URL url = URI.create(urlStr).toURL();
                urls.add(url);
            } catch (Exception ex) {
                String message = urlStr + ": " + ex.getMessage();
                errors.add(message);
            }
        }

        for (String resource : resources) {
            URL url = loader.getResource(resource);
            if (url == null) {
                String message = "Resource " + resource + " not found";
                errors.add(message);
            } else {
                URL url0 = new URL(bootstrapProtocol, null, resource);
                urls.add(url0);
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder builder = new StringBuilder("Error:");
            for (String error: errors) {
                builder.append("\n  ");
                builder.append(error);
            }
            exit(builder.toString());
        }

        return urls;
    }

    // JARs from JARs can't be used directly, see:
    // http://stackoverflow.com/questions/183292/classpath-including-jar-within-a-jar/2326775#2326775
    // Loading them via a custom protocol, inspired by:
    // http://stackoverflow.com/questions/26363573/registering-and-using-a-custom-java-net-url-protocol/26409796#26409796
    static void registerBootstrapUnder(final String bootstrapProtocol, final ClassLoader loader) {
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
            public URLStreamHandler createURLStreamHandler(String protocol) {
                return bootstrapProtocol.equals(protocol) ? new URLStreamHandler() {
                    protected URLConnection openConnection(URL url) throws IOException {
                        String path = url.getPath();
                        URL resURL = loader.getResource(path);
                        if (resURL == null)
                            throw new FileNotFoundException("Resource " + path);
                        return resURL.openConnection();
                    }
                } : null;
            }
        });
    }


    public static void main(String[] args) throws Throwable {

        setMainProperties(mainJarPath(), args);
        setExtraProperties("bootstrap.properties");

        String mainClass0 = System.getProperty("bootstrap.mainClass");
        String jarDir0 = System.getProperty("bootstrap.jarDir");

        final File jarDir = new File(jarDir0);

        if (jarDir.exists()) {
            if (!jarDir.isDirectory())
                exit("Error: " + jarDir0 + " is not a directory");
        } else if (!jarDir.mkdirs())
            System.err.println("Warning: cannot create " + jarDir0 + ", continuing anyway.");

        Random rng = new Random();
        String protocol = "bootstrap" + rng.nextLong();

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

        registerBootstrapUnder(protocol, contextLoader);

        String[] strUrls = readStringSequence(defaultURLResource);
        String[] resources = readStringSequence(defaultJarResource);
        List<URL> urls = getURLs(strUrls, resources, protocol, contextLoader);
        List<URL> localURLs = getLocalURLs(urls, jarDir, protocol);

        String[] isolationIDs = readStringSequence(isolationIDsResource);
        Map<String, URL[]> perIsolationContextURLs = readIsolationContexts(jarDir, isolationIDs, protocol, contextLoader);

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

        List<String> userArgs0 = new ArrayList<String>();

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
