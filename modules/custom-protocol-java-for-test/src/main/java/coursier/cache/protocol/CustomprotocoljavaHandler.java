package coursier.cache.protocol;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import java.io.IOException;

public class CustomprotocoljavaHandler implements URLStreamHandlerFactory {
  public URLStreamHandler createURLStreamHandler(String protocol) {
    return new URLStreamHandler() {
      protected URLConnection openConnection(URL url) throws IOException {
        return new URL("https://repo1.maven.org/maven2" + url.getPath()).openConnection();
      }
    };
  }
}
