
package edu.stanford.nlp.net; 

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.io.InputStream;

public class ClasspathURLStreamHandler extends URLStreamHandler {
  class ClasspathURLConnection extends URLConnection {
    InputStream stream;

    public ClasspathURLConnection(URL url) {
      super(url);
    }

    public void connect() {
      stream = ClasspathURLConnection.class.getClassLoader().getResourceAsStream(url.getFile());
    }

    public InputStream getInputStream() {
      if (stream == null) {
        connect();
      }
      return stream;
    }
  }

  public URLConnection openConnection(URL u) {
    return new ClasspathURLConnection(u);
  }
}
