package old.edu.stanford.nlp.io;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

import old.edu.stanford.nlp.util.AbstractIterator;
import old.edu.stanford.nlp.util.ErasureUtils;

/**
 * Helper Class for storing serialized objects to disk.
 *
 * @author Kayur Patel, Teg Grenager
 */

public class IOUtils {

  private static final int SLURPBUFFSIZE = 16000;

  // A class of static methods
  private IOUtils() {
  }

  /**
   * Write object to a file with the specified name.
   *
   * @param o object to be written to file
   * @param filename name of the temp file
   * @throws IOException If can't write file.
   * @return File containing the object
   */
  public static File writeObjectToFile(Object o, String filename) throws IOException {
    return writeObjectToFile(o, new File(filename));
  }

    /**
     * Write an object to a specified File.
     *
     * @param o object to be written to file
     * @param file The temp File
     * @throws IOException If File cannot be written
     * @return File containing the object
     */
  public static File writeObjectToFile(Object o, File file) throws IOException {
    // file.createNewFile(); // cdm may 2005: does nothing needed
    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
    oos.writeObject(o);
    oos.close();
    return file;
  }

  /**
   * Write object to a file with the specified name.
   *
   * @param o object to be written to file
   * @param filename name of the temp file
   *
   * @return File containing the object, or null if an exception was caught
   */
  public static File writeObjectToFileNoExceptions(Object o, String filename) {
    File file = null;
    ObjectOutputStream oos = null;
    try {
      file = new File(filename);
      // file.createNewFile(); // cdm may 2005: does nothing needed
      oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
      oos.writeObject(o);
      oos.close();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      closeIgnoringExceptions(oos);
    }
    return file;
  }

  /**
   * Write object to temp file which is destroyed when the program exits.
   *
   * @param o object to be written to file
   * @param filename name of the temp file
   * @throws IOException If file cannot be written
   * @return File containing the object
   */
  public static File writeObjectToTempFile(Object o, String filename) throws IOException {
    File file = File.createTempFile(filename, ".tmp");
    file.deleteOnExit();
    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
    oos.writeObject(o);
    oos.close();
    return file;
  }

  /**
   * Write object to a temp file and ignore exceptions.
   *
   * @param o object to be written to file
   * @param filename name of the temp file
   * @return File containing the object
   */
  public static File writeObjectToTempFileNoExceptions(Object o, String filename) {
    try {
      return writeObjectToTempFile(o, filename);
    } catch (Exception e) {
      System.err.println("Error writing object to file " + filename);
      e.printStackTrace();
      return null;
    }
  }


  /**
   * Read an object from a stored file.
   *
   * @param file the file pointing to the object to be retrived
   * @throws IOException If file cannot be read
   * @throws ClassNotFoundException If reading serialized object fails
   * @return the object read from the file.
   */
  public static <T> T readObjectFromFile(File file) throws IOException, ClassNotFoundException {
    ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
    Object o = ois.readObject();
    ois.close();
    return ErasureUtils.<T>uncheckedCast(o);
  }

  /**
   * Read an object from a stored file.
   *
   * @param filename The filename of the object to be retrived
   * @throws IOException If file cannot be read
   * @throws ClassNotFoundException If reading serialized object fails
   * @return The object read from the file.
   */
  public static <T> T readObjectFromFile(String filename) throws IOException, ClassNotFoundException {
    return ErasureUtils.<T>uncheckedCast(readObjectFromFile(new File(filename)));
  }


  /**
   * Read an object from a stored file without throwing exceptions.
   *
   * @param file the file pointing to the object to be retrived
   * @return the object read from the file, or null if an exception occurred.
   */
  public static <T> T readObjectFromFileNoExceptions(File file) {
    Object o = null;
    try {
      ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
      o = ois.readObject();
      ois.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    return ErasureUtils.<T>uncheckedCast(o);
  }

  public static int lineCount(File textFile) throws IOException {
    BufferedReader r = new BufferedReader(new FileReader(textFile));
    int numLines = 0;
    while (r.readLine()!=null) {
      numLines++;
    }
    return numLines;
  }

  public static ObjectOutputStream writeStreamFromString(String serializePath) throws IOException {
    ObjectOutputStream oos;
    if (serializePath.endsWith(".gz")) {
      oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(serializePath))));
    } else {
      oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(serializePath)));
    }

    return oos;
  }

  public static ObjectInputStream readStreamFromString(String filenameOrUrl) throws IOException {
    ObjectInputStream in;
    InputStream is;
    if (filenameOrUrl.matches("https?://.*")) {
      URL u = new URL(filenameOrUrl);
      URLConnection uc = u.openConnection();
      is = uc.getInputStream();
    } else {
      is = new FileInputStream(filenameOrUrl);
    }
    if (filenameOrUrl.endsWith(".gz")) {
      in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(is)));
    } else {
      in = new ObjectInputStream(new BufferedInputStream(is));
    }
    return in;
  }

  private static InputStream getInputStreamFromString(String textFileOrUrl) throws IOException {
    InputStream is;
    if (textFileOrUrl.matches("https?://.*")) {
      URL u = new URL(textFileOrUrl);
      URLConnection uc = u.openConnection();
      is = uc.getInputStream();
    } else {
      is = new FileInputStream(textFileOrUrl);
    }
    if (textFileOrUrl.endsWith(".gz")) {
      is = new GZIPInputStream(is);
    }
    return is;
  }

  public static BufferedReader readReaderFromString(String textFileOrUrl) throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStreamFromString(textFileOrUrl)));
  }


  /** Open a BufferedReader to a file or URL specified by a String name.
   *  If the String starts with https?://, then it is interpreted as a URL,
   *  otherwise it is interpreted as a local file.  If the String ends in .gz,
   *  it is interpreted as a gzipped file (and uncompressed), else it is
   *  interpreted as a regular text file in the given encoding.
   *
   *  @param textFileOrUrl What to read from
   *  @param encoding CharSet encoding
   *  @return The BufferedReader
   *  @throws IOException If there is an I/O problem
   */
  public static BufferedReader readReaderFromString(String textFileOrUrl, String encoding) throws IOException {
    InputStream is = getInputStreamFromString(textFileOrUrl);
    return new BufferedReader(new InputStreamReader(is, encoding));
  }


  /**
   * Returns an Iterable of the lines in the file.
   *
   * The file reader will be closed when the iterator is exhausted.
   * IO errors will throw an (unchecked) RuntimeIOException
   *
   * @param path  The file whose lines are to be read.
   * @return      An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(String path) {
    return readLines(new File(path));
  }

  /**
   * Returns an Iterable of the lines in the file.
   *
   * The file reader will be closed when the iterator is exhausted.
   *
   * @param file  The file whose lines are to be read.
   * @return      An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(final File file) {
    return readLines(file, null);
  }

  /**
   * Returns an Iterable of the lines in the file, wrapping the generated
   * FileInputStream with an instance of the supplied class.
   * IO errors will throw an (unchecked) RuntimeIOException
   *
   * @param file  The file whose lines are to be read.
   * @param fileInputStreamWrapper  The class to wrap the InputStream with,
   *              e.g. GZIPInputStream. Note that the class must have a
   *              constructor that accepts an InputStream.
   * @return      An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(final File file, final Class<? extends InputStream> fileInputStreamWrapper) {

    return new Iterable<String>() {
      public Iterator<String> iterator() {
        return new Iterator<String>() {

          protected BufferedReader reader = this.getReader();
          protected String line = this.getLine();

          public boolean hasNext() {
            return this.line != null;
          }

          public String next() {
            String nextLine = this.line;
            if (nextLine == null) {
              throw new NoSuchElementException();
            }
            line = getLine();
            return nextLine;
          }

          protected String getLine() {
            try {
              String result = this.reader.readLine();
              if (result == null) {
                this.reader.close();
              }
              return result;
            } catch (IOException e) {
              throw new RuntimeIOException(e);
            }
          }

          protected BufferedReader getReader() {
            try {
              InputStream stream = new FileInputStream(file);
              if (fileInputStreamWrapper != null) {
                stream = fileInputStreamWrapper.getConstructor(InputStream.class).newInstance(stream);
              }
              return new BufferedReader(new InputStreamReader(stream));
            } catch (Exception e) {
              throw new RuntimeIOException(e);
            }
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }


  /**
   * Quietly opens a File.  If the file ends with a ".gz" extension,
   * automatically opens a GZIPInputStream to wrap the constructed
   * FileInputStream.
   */
  public static InputStream openFile(File file) throws RuntimeIOException {
    try {
      InputStream is = new BufferedInputStream(new FileInputStream(file));
      if (file.getName().endsWith(".gz")) {
        is = new GZIPInputStream(is);
      }
      return is;
    } catch (Exception e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Provides an implementation of closing a file for use in a
   *  finally block so you can correctly close a file without even
   *  more exception handling stuff.  From a suggestion in a talk
   *  by Josh Bloch.
   *
   *  @param c The IO resource to close (e.g., a Stream/Reader)
   */
  public static void closeIgnoringExceptions(Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException ioe) {
        // ignore
      }
    }
  }

  /**
   * Iterate over all the files in the directory, recursively.
   *
   * @param dir The root directory.
   * @return    All files within the directory.
   */
  public static Iterable<File> iterFilesRecursive(final File dir) {
    return iterFilesRecursive(dir, (Pattern)null);
  }

  /**
   * Iterate over all the files in the directory, recursively.
   *
   * @param dir The root directory.
   * @param ext A string that must be at the end of all files (e.g. ".txt")
   * @return    All files within the directory ending in the given extension.
   */
  public static Iterable<File> iterFilesRecursive(final File dir, final String ext) {
    return iterFilesRecursive(dir, Pattern.compile(Pattern.quote(ext) + "$"));
  }

  /**
   * Iterate over all the files in the directory, recursively.
   *
   * @param dir     The root directory.
   * @param pattern A regular expression that the file path must match.
   *                This uses Matcher.find(), so use ^ and $ to specify endpoints.
   * @return        All files within the directory.
   */
  public static Iterable<File> iterFilesRecursive(final File dir, final Pattern pattern) {
    return new Iterable<File> () {
      public Iterator<File> iterator() {
        return new AbstractIterator<File>() {
          private Queue<File> files = new LinkedList<File>(Collections.singleton(dir));
          private File file = this.findNext();

          @Override
          public boolean hasNext() {
            return this.file != null;
          }

          @Override
          public File next() {
            File result = this.file;
            if (result == null) {
              throw new NoSuchElementException();
            }
            this.file = this.findNext();
            return result;
          }

          private File findNext() {
            File next = null;
            while (!this.files.isEmpty() && next == null) {
              next = this.files.remove();
              if (next.isDirectory()) {
                files.addAll(Arrays.asList(next.listFiles()));
                next = null;
              } else if (pattern != null) {
                if (!pattern.matcher(next.getPath()).find()) {
                  next = null;
                }
              }
            }
            return next;
          }
        };
      }
    };
  }

  /**
   * Returns all the text in the given File.
   */
  public static String slurpFile(File file) throws IOException {
    Reader r = new FileReader(file);
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given File.
   */
  public static String slurpGZippedFile(String filename) throws IOException {
    Reader r = new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)));
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given File.
   */
  public static String slurpGZippedFile(File file) throws IOException {
    Reader r = new InputStreamReader(new GZIPInputStream(new FileInputStream(file)));
    return IOUtils.slurpReader(r);
  }

  public static String slurpGBFileNoExceptions(String filename) {
    return IOUtils.slurpFileNoExceptions(filename, "GB18030");
  }

  /**
   * Returns all the text in the given file with the given encoding.
   */
  public static String slurpFile(String filename, String encoding) throws IOException {
    Reader r = new InputStreamReader(new FileInputStream(filename), encoding);
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given file with the given encoding.
   * If the file cannot be read (non-existent, etc.),
   * then and only then the method returns <code>null</code>.
   */
  public static String slurpFileNoExceptions(String filename, String encoding) {
    try {
      return slurpFile(filename, encoding);
    } catch (Exception e) {
      throw new RuntimeIOException("slurpFile IO problem", e);
    }
  }

  public static String slurpGBFile(String filename) throws IOException {
    return slurpFile(filename, "GB18030");
  }

  /**
   * Returns all the text in the given file
   *
   * @return The text in the file.
   */
  public static String slurpFile(String filename) throws IOException {
    return IOUtils.slurpReader(new FileReader(filename));
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpGBURL(URL u) throws IOException {
    return IOUtils.slurpURL(u, "GB18030");
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpGBURLNoExceptions(URL u) {
    try {
      return slurpGBURL(u);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURLNoExceptions(URL u, String encoding) {
    try {
      return IOUtils.slurpURL(u, encoding);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURL(URL u, String encoding) throws IOException {
    String lineSeparator = System.getProperty("line.separator");
    URLConnection uc = u.openConnection();
    uc.setReadTimeout(30000);
    InputStream is;
    try {
      is = uc.getInputStream();
    } catch (SocketTimeoutException e) {
      //e.printStackTrace();
      System.err.println("Time out. Return empty string");
      return "";
    }
    BufferedReader br = new BufferedReader(new InputStreamReader(is, encoding));
    String temp;
    StringBuilder buff = new StringBuilder(16000);  // make biggish
    while ((temp = br.readLine()) != null) {
      buff.append(temp);
      buff.append(lineSeparator);
    }
    br.close();
    return buff.toString();
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURL(URL u) throws IOException {
    String lineSeparator = System.getProperty("line.separator");
    URLConnection uc = u.openConnection();
    InputStream is = uc.getInputStream();
    BufferedReader br = new BufferedReader(new InputStreamReader(is));
    String temp;
    StringBuilder buff = new StringBuilder(16000);  // make biggish
    while ((temp = br.readLine()) != null) {
      buff.append(temp);
      buff.append(lineSeparator);
    }
    br.close();
    return buff.toString();
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURLNoExceptions(URL u) {
    try {
      return slurpURL(u);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURL(String path) throws Exception {
    return slurpURL(new URL(path));
  }

  /**
   * Returns all the text at the given URL. If the file cannot be read (non-existent, etc.),
   * then and only then the method returns <code>null</code>.
   */
  public static String slurpURLNoExceptions(String path) {
    try {
      return slurpURL(path);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Returns all the text in the given File.
   *
   * @return The text in the file.  May be an empty string if the file
   *         is empty.  If the file cannot be read (non-existent, etc.),
   *         then and only then the method returns <code>null</code>.
   */
  public static String slurpFileNoExceptions(File file) {
    try {
      return IOUtils.slurpReader(new FileReader(file));
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Returns all the text in the given File.
   *
   * @return The text in the file.  May be an empty string if the file
   *         is empty.  If the file cannot be read (non-existent, etc.),
   *         then and only then the method returns <code>null</code>.
   */
  public static String slurpFileNoExceptions(String filename) {
    try {
      return slurpFile(filename);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Returns all the text from the given Reader.
   *
   * @return The text in the file.
   */
  public static String slurpReader(Reader reader) {
    BufferedReader r = new BufferedReader(reader);
    StringBuilder buff = new StringBuilder();
    try {
      char[] chars = new char[SLURPBUFFSIZE];
      while (true) {
        int amountRead = r.read(chars, 0, SLURPBUFFSIZE);
        if (amountRead < 0) {
          break;
        }
        buff.append(chars, 0, amountRead);
      }
      r.close();
    } catch (Exception e) {
      throw new RuntimeIOException("slurpReader IO problem", e);
    }
    return buff.toString();
  }

  /**
   * Send all bytes from the input stream to the output stream.
   * 
   * @param input  The input bytes.
   * @param output Where the bytes should be written.
   */
  public static void writeStreamToStream(InputStream input, OutputStream output)
  throws IOException {
    byte[] buffer = new byte[4096];
    while (true) {
      int len = input.read(buffer);
      if (len == -1) {
        break;
      }
      output.write(buffer, 0, len);
    }
  }
}
