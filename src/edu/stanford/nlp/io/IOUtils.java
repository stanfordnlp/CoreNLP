package edu.stanford.nlp.io;

import edu.stanford.nlp.util.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Helper Class for various I/O related things.
 *
 * @author Kayur Patel, Teg Grenager
 */

public class IOUtils {

  private static final int SLURPBUFFSIZE = 16000;

  public static final String eolChar = System.getProperty("line.separator");
  public static final String defaultEncoding = "utf-8";

  // A class of static methods
  private IOUtils() { }

  /**
   * Write object to a file with the specified name.
   *
   * @param o Object to be written to file
   * @param filename Name of the temp file
   * @throws IOException If can't write file.
   * @return File containing the object
   */
  public static File writeObjectToFile(Object o, String filename)
          throws IOException {
    return writeObjectToFile(o, new File(filename));
  }

  /**
   * Write an object to a specified File.
   *
   * @param o Object to be written to file
   * @param file The temp File
   * @throws IOException If File cannot be written
   * @return File containing the object
   */
  public static File writeObjectToFile(Object o, File file) throws IOException {
    return writeObjectToFile(o, file, false);
  }

  /**
   * Write an object to a specified File.
   *
   * @param o Object to be written to file
   * @param file The temp File
   * @param append If true, append to this file instead of overwriting it
   * @throws IOException If File cannot be written
   * @return File containing the object
   */
  public static File writeObjectToFile(Object o, File file, boolean append) throws IOException {
    // file.createNewFile(); // cdm may 2005: does nothing needed
    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(
            new GZIPOutputStream(new FileOutputStream(file, append))));
    oos.writeObject(o);
    oos.close();
    return file;
  }

  /**
   * Write object to a file with the specified name.
   *
   * @param o Object to be written to file
   * @param filename Name of the temp file
   * @return File containing the object, or null if an exception was caught
   */
  public static File writeObjectToFileNoExceptions(Object o, String filename) {
    File file = null;
    ObjectOutputStream oos = null;
    try {
      file = new File(filename);
      // file.createNewFile(); // cdm may 2005: does nothing needed
      oos = new ObjectOutputStream(new BufferedOutputStream(
              new GZIPOutputStream(new FileOutputStream(file))));
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
   * @param o Object to be written to file
   * @param filename Name of the temp file
   * @throws IOException If file cannot be written
   * @return File containing the object
   */
  public static File writeObjectToTempFile(Object o, String filename)
          throws IOException {
    File file = File.createTempFile(filename, ".tmp");
    file.deleteOnExit();
    writeObjectToFile(o, file);
    return file;
  }

  /**
   * Write object to a temp file and ignore exceptions.
   *
   * @param o Object to be written to file
   * @param filename Name of the temp file
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

  //++ todo [cdm, Aug 2012]: None of the methods below in this block are used. Delete them all?
  //++ They're also kind of weird in unnecessarily bypassing using a Writer.

  /**
   * Writes a string to a file.
   *
   * @param contents The string to write
   * @param path The file path
   * @param encoding The encoding to encode in
   * @throws IOException In case of failure
   */
  public static void writeStringToFile(String contents, String path, String encoding) throws IOException {
    OutputStream writer;
    if (path.endsWith(".gz")) {
      writer = new GZIPOutputStream(new FileOutputStream(path));
    } else {
      writer = new BufferedOutputStream(new FileOutputStream(path));
    }
    writer.write(contents.getBytes(encoding));
    writer.close();
  }

  /**
   * Writes a string to a file, as UTF-8.
   *
   * @param contents The string to write
   * @param path The file path
   * @throws IOException In case of failure
   */

  /**
   * Writes a string to a file, squashing exceptions
   *
   * @param contents The string to write
   * @param path The file path
   * @param encoding The encoding to encode in
   * */
  public static void writeStringToFileNoExceptions(String contents, String path, String encoding) {
    OutputStream writer = null;
    try{
      if (path.endsWith(".gz")) {
        writer = new GZIPOutputStream(new FileOutputStream(path));
      } else {
        writer = new BufferedOutputStream(new FileOutputStream(path));
      }
      writer.write(contents.getBytes(encoding));
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if(writer != null){ closeIgnoringExceptions(writer); }
    }
  }

  /**
   * Writes a string to a temporary file
   *
   * @param contents The string to write
   * @param path The file path
   * @param encoding The encoding to encode in
   * @throws IOException In case of failure
   * @return The File written to
   */
  public static File writeStringToTempFile(String contents, String path, String encoding) throws IOException {
    OutputStream writer;
    File tmp = File.createTempFile(path,".tmp");
    if (path.endsWith(".gz")) {
      writer = new GZIPOutputStream(new FileOutputStream(tmp));
    } else {
      writer = new BufferedOutputStream(new FileOutputStream(tmp));
    }
    writer.write(contents.getBytes(encoding));
    return tmp;
  }

  /**
   * Writes a string to a temporary file, as UTF-8
   *
   * @param contents The string to write
   * @param path The file path
   * @throws IOException In case of failure
   */
  public static void writeStringToTempFile(String contents, String path) throws IOException {
    writeStringToTempFile(contents, path, "UTF-8");
  }

  /**
   * Writes a string to a temporary file, squashing exceptions
   *
   * @param contents The string to write
   * @param path The file path
   * @param encoding The encoding to encode in
   * @return The File that was written to
   */
  public static File writeStringToTempFileNoExceptions(String contents, String path, String encoding) {
    OutputStream writer = null;
    File tmp = null;
    try {
      tmp = File.createTempFile(path,".tmp");
      if (path.endsWith(".gz")) {
        writer = new GZIPOutputStream(new FileOutputStream(tmp));
      } else {
        writer = new BufferedOutputStream(new FileOutputStream(tmp));
      }
      writer.write(contents.getBytes(encoding));
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      closeIgnoringExceptions(writer);
    }
    return tmp;
  }

  /**
   * Writes a string to a temporary file with UTF-8 encoding, squashing exceptions
   *
   * @param contents The string to write
   * @param path The file path
   */
  public static void writeStringToTempFileNoExceptions(String contents, String path) {
    writeStringToTempFileNoExceptions(contents, path, "UTF-8");
  }

  //-- todo [cdm, Aug 2012]: None of the methods above in the block are used. Delete them all?


  /**
   * Read an object from a stored file.
   *
   * @param file The file pointing to the object to be retrieved
   * @throws IOException If file cannot be read
   * @throws ClassNotFoundException If reading serialized object fails
   * @return The object read from the file.
   */
  public static <T> T readObjectFromFile(File file) throws IOException,
          ClassNotFoundException {
    ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
            new GZIPInputStream(new FileInputStream(file))));
    Object o = ois.readObject();
    ois.close();
    return ErasureUtils.uncheckedCast(o);
  }

  /**
   * Read an object from a stored file.  The file can be anything obtained
   * via a URL, the filesystem, or the classpath (eg in a jar file).
   *
   * @param filename The file pointing to the object to be retrieved
   * @throws IOException If file cannot be read
   * @throws ClassNotFoundException If reading serialized object fails
   * @return The object read from the file.
   */
  public static <T> T readObjectFromURLOrClasspathOrFileSystem(String filename) throws IOException, ClassNotFoundException {
    ObjectInputStream ois = new ObjectInputStream(getInputStreamFromURLOrClasspathOrFileSystem(filename));
    Object o = ois.readObject();
    ois.close();
    return ErasureUtils.uncheckedCast(o);
  }

  public static <T> T readObjectFromObjectStream(ObjectInputStream ois) throws IOException,
          ClassNotFoundException {
    Object o = ois.readObject();
    return ErasureUtils.uncheckedCast(o);
  }

  /**
   * Read an object from a stored file.
   *
   * @param filename The filename of the object to be retrieved
   * @throws IOException If file cannot be read
   * @throws ClassNotFoundException If reading serialized object fails
   * @return The object read from the file.
   */
  public static <T> T readObjectFromFile(String filename) throws IOException,
          ClassNotFoundException {
    return ErasureUtils.uncheckedCast(readObjectFromFile(new File(filename)));
  }

  /**
   * Read an object from a stored file without throwing exceptions.
   *
   * @param file The file pointing to the object to be retrieved
   * @return The object read from the file, or null if an exception occurred.
   */
  public static <T> T readObjectFromFileNoExceptions(File file) {
    Object o = null;
    try {
      ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
              new GZIPInputStream(new FileInputStream(file))));
      o = ois.readObject();
      ois.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    return ErasureUtils.uncheckedCast(o);
  }

  public static int lineCount(File textFile) throws IOException {
    BufferedReader r = new BufferedReader(new FileReader(textFile));
    int numLines = 0;
    while (r.readLine() != null) {
      numLines++;
    }
    return numLines;
  }

  public static ObjectOutputStream writeStreamFromString(String serializePath)
          throws IOException {
    ObjectOutputStream oos;
    if (serializePath.endsWith(".gz")) {
      oos = new ObjectOutputStream(new BufferedOutputStream(
              new GZIPOutputStream(new FileOutputStream(serializePath))));
    } else {
      oos = new ObjectOutputStream(new BufferedOutputStream(
              new FileOutputStream(serializePath)));
    }

    return oos;
  }

  public static ObjectInputStream readStreamFromString(String filenameOrUrl)
          throws IOException {
    InputStream is = getInputStreamFromURLOrClasspathOrFileSystem(filenameOrUrl);
    return new ObjectInputStream(is);
  }

  /**
   * Locates this file either in the CLASSPATH or in the file system. The CLASSPATH takes priority.
   * @param name The file or resource name
   * @throws FileNotFoundException If the file does not exist
   * @return The InputStream of name, or null if not found
   */
  private static InputStream findStreamInClasspathOrFileSystem(String name) throws FileNotFoundException {
    // ms 10-04-2010:
    // - even though this may look like a regular file, it may be a path inside a jar in the CLASSPATH
    // - check for this first. This takes precedence over the file system.
    InputStream is = IOUtils.class.getClassLoader().getResourceAsStream(name);
    // windows File.separator is \, but getting resources only works with /
    if (is == null) {
      is = IOUtils.class.getClassLoader().getResourceAsStream(name.replaceAll("\\\\", "/"));
    }
    // if not found in the CLASSPATH, load from the file system
    if (is == null) is = new FileInputStream(name);
    return is;
  }

  /**
   * Locates this file either using the given URL, or in the CLASSPATH, or in the file system
   * The CLASSPATH takes priority over the file system!
   * This stream is buffered and gunzipped (if necessary).
   *
   * @param textFileOrUrl
   * @return An InputStream for loading a resource
   * @throws IOException
   */
  public static InputStream getInputStreamFromURLOrClasspathOrFileSystem(String textFileOrUrl)
    throws IOException
  {
    InputStream in;
    if (textFileOrUrl.matches("https?://.*")) {
      URL u = new URL(textFileOrUrl);
      URLConnection uc = u.openConnection();
      in = uc.getInputStream();
    } else {
      try {
        in = findStreamInClasspathOrFileSystem(textFileOrUrl);
      } catch (FileNotFoundException e) {
        try {
          // Maybe this happens to be some other format of URL?
          URL u = new URL(textFileOrUrl);
          URLConnection uc = u.openConnection();
          in = uc.getInputStream();
        } catch (IOException e2) {
          // Don't make the original exception a cause, since it is almost certainly bogus
          throw new IOException("Unable to resolve \"" +
                  textFileOrUrl + "\" as either " +
                  "class path, filename or URL"); // , e2);
        }
      }
    }

    // buffer this stream
    in = new BufferedInputStream(in);

    // gzip it if necessary
    if (textFileOrUrl.endsWith(".gz"))
      in = new GZIPInputStream(in);

    return in;
  }

  /**
   * Open a BufferedReader to a file or URL specified by a String name. If the
   * String starts with https?://, then it is first tried as a URL, otherwise it
   * is next tried as a resource on the CLASSPATH, and then finally it is tried
   * as a local file or other network-available file . If the String ends in .gz, it
   * is interpreted as a gzipped file (and uncompressed). The file is then
   * interpreted as a utf-8 text file.
   *
   * @param textFileOrUrl What to read from
   * @return The BufferedReader
   * @throws IOException If there is an I/O problem
   */
  public static BufferedReader readerFromString(String textFileOrUrl)
          throws IOException {
    return new BufferedReader(new InputStreamReader(
            getInputStreamFromURLOrClasspathOrFileSystem(textFileOrUrl), "UTF-8"));
  }

  /**
   * Open a BufferedReader to a file or URL specified by a String name. If the
   * String starts with https?://, then it is first tried as a URL, otherwise it
   * is next tried as a resource on the CLASSPATH, and then finally it is tried
   * as a local file or other network-available file . If the String ends in .gz, it
   * is interpreted as a gzipped file (and uncompressed), else it is interpreted as
   * a regular text file in the given encoding.
   *
   * @param textFileOrUrl What to read from
   * @param encoding CharSet encoding. Maybe be null, in which case the
   *         platform default encoding is used
   * @return The BufferedReader
   * @throws IOException If there is an I/O problem
   */
  public static BufferedReader readerFromString(String textFileOrUrl,
                                                String encoding) throws IOException {
    InputStream is = getInputStreamFromURLOrClasspathOrFileSystem(textFileOrUrl);
    if (encoding == null) {
      return new BufferedReader(new InputStreamReader(is));
    }
    return new BufferedReader(new InputStreamReader(is, encoding));
  }

  /**
   * Returns an Iterable of the lines in the file.
   *
   * The file reader will be closed when the iterator is exhausted. IO errors
   * will throw an (unchecked) RuntimeIOException
   *
   * @param path The file whose lines are to be read.
   * @return An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(String path) {
    return readLines(new File(path));
  }

  /**
   * Returns an Iterable of the lines in the file.
   *
   * The file reader will be closed when the iterator is exhausted. IO errors
   * will throw an (unchecked) RuntimeIOException
   *
   * @param path The file whose lines are to be read.
   * @param encoding The encoding to use when reading lines.
   * @return An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(String path, String encoding) {
    return readLines(new File(path), null, encoding);
  }

  /**
   * Returns an Iterable of the lines in the file.
   *
   * The file reader will be closed when the iterator is exhausted.
   *
   * @param file The file whose lines are to be read.
   * @return An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(final File file) {
    return readLines(file, null, null);
  }

  /**
   * Returns an Iterable of the lines in the file.
   *
   * The file reader will be closed when the iterator is exhausted.
   *
   * @param file The file whose lines are to be read.
   * @param fileInputStreamWrapper
   *          The class to wrap the InputStream with, e.g. GZIPInputStream. Note
   *          that the class must have a constructor that accepts an
   *          InputStream.
   * @return An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(final File file,
                                           final Class<? extends InputStream> fileInputStreamWrapper) {
    return readLines(file, fileInputStreamWrapper, null);
  }

  /**
   * Returns an Iterable of the lines in the file, wrapping the generated
   * FileInputStream with an instance of the supplied class. IO errors will
   * throw an (unchecked) RuntimeIOException
   *
   * @param file The file whose lines are to be read.
   * @param fileInputStreamWrapper
   *          The class to wrap the InputStream with, e.g. GZIPInputStream. Note
   *          that the class must have a constructor that accepts an
   *          InputStream.
   * @param encoding The encoding to use when reading lines.
   * @return An Iterable containing the lines from the file.
   */
  public static Iterable<String> readLines(final File file,
                                           final Class<? extends InputStream> fileInputStreamWrapper,
                                           final String encoding) {

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
                stream = fileInputStreamWrapper.getConstructor(
                        InputStream.class).newInstance(stream);
              }
              if (encoding == null) {
                return new BufferedReader(new InputStreamReader(stream));
              } else {
                return new BufferedReader(new InputStreamReader(stream, encoding));
              }
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
   * Quietly opens a File. If the file ends with a ".gz" extension,
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
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Provides an implementation of closing a file for use in a finally block so
   * you can correctly close a file without even more exception handling stuff.
   * From a suggestion in a talk by Josh Bloch.
   *
   * @param c The IO resource to close (e.g., a Stream/Reader)
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
   * @param dir
   *          The root directory.
   * @return All files within the directory.
   */
  public static Iterable<File> iterFilesRecursive(final File dir) {
    return iterFilesRecursive(dir, (Pattern) null);
  }

  /**
   * Iterate over all the files in the directory, recursively.
   *
   * @param dir
   *          The root directory.
   * @param ext
   *          A string that must be at the end of all files (e.g. ".txt")
   * @return All files within the directory ending in the given extension.
   */
  public static Iterable<File> iterFilesRecursive(final File dir,
                                                  final String ext) {
    return iterFilesRecursive(dir, Pattern.compile(Pattern.quote(ext) + "$"));
  }

  /**
   * Iterate over all the files in the directory, recursively.
   *
   * @param dir
   *          The root directory.
   * @param pattern
   *          A regular expression that the file path must match. This uses
   *          Matcher.find(), so use ^ and $ to specify endpoints.
   * @return All files within the directory.
   */
  public static Iterable<File> iterFilesRecursive(final File dir,
                                                  final Pattern pattern) {
    return new Iterable<File>() {
      public Iterator<File> iterator() {
        return new AbstractIterator<File>() {
          private final Queue<File> files = new LinkedList<File>(Collections
                  .singleton(dir));
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
   *
   * @param file The file to read from
   * @param encoding The character encoding to assume.  This may be null, and
   *       the platform default character encoding is used.
   */
  public static String slurpFile(File file, String encoding) throws IOException {
    return IOUtils.slurpReader(IOUtils.encodedInputStreamReader(
            new FileInputStream(file), encoding));
  }

  /**
   * Returns all the text in the given File.
   */
  public static String slurpGZippedFile(String filename) throws IOException {
    Reader r = new InputStreamReader(new GZIPInputStream(new FileInputStream(
            filename)));
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given File.
   */
  public static String slurpGZippedFile(File file) throws IOException {
    Reader r = new InputStreamReader(new GZIPInputStream(new FileInputStream(
            file)));
    return IOUtils.slurpReader(r);
  }

  public static String slurpGBFileNoExceptions(String filename) {
    return IOUtils.slurpFileNoExceptions(filename, "GB18030");
  }

  /**
   * Returns all the text in the given file with the given encoding.
   */
  public static String slurpFile(String filename, String encoding)
          throws IOException {
    Reader r = new InputStreamReader(new FileInputStream(filename), encoding);
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given file with the given encoding. If the file
   * cannot be read (non-existent, etc.), then and only then the method returns
   * <code>null</code>.
   */
  public static String slurpFileNoExceptions(String filename, String encoding) {
    try {
      return slurpFile(filename, encoding);
    } catch (IOException e) {
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
      // e.printStackTrace();
      System.err.println("Time out. Return empty string");
      return "";
    }
    BufferedReader br = new BufferedReader(new InputStreamReader(is, encoding));
    String temp;
    StringBuilder buff = new StringBuilder(16000); // make biggish
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
    StringBuilder buff = new StringBuilder(16000); // make biggish
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
   * Returns all the text at the given URL. If the file cannot be read
   * (non-existent, etc.), then and only then the method returns
   * <code>null</code>.
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
   * @return The text in the file. May be an empty string if the file is empty.
   *         If the file cannot be read (non-existent, etc.), then and only then
   *         the method returns <code>null</code>.
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
   * @return The text in the file. May be an empty string if the file is empty.
   *         If the file cannot be read (non-existent, etc.), then and only then
   *         the method returns <code>null</code>.
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
   * Closes the Reader when done.
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
   * @param input
   *          The input bytes.
   * @param output
   *          Where the bytes should be written.
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

  /**
   * Read in a CSV formatted file with a header row
   * @param path - path to CSV file
   * @param quoteChar - character for enclosing strings, defaults to "
   * @param escapeChar - character for escaping quotes appearing in quoted strings; defaults to " (i.e. "" is used for " inside quotes, consistent with Excel)
   * @return a list of maps representing the rows of the csv. The maps' keys are the header strings and their values are the row contents
   * @throws IOException
   */
  public static List<Map<String,String>> readCSVWithHeader(String path, char quoteChar, char escapeChar) throws IOException {
    String[] labels = null;
    List<Map<String,String>> rows = Generics.newArrayList();
    for (String line : IOUtils.readLines(path)) {
      System.out.println("Splitting "+line);
      if (labels == null) {
        labels = StringUtils.splitOnCharWithQuoting(line,',','"',escapeChar);
      } else {
        String[] cells = StringUtils.splitOnCharWithQuoting(line,',',quoteChar,escapeChar);
        assert(cells.length == labels.length);
        Map<String,String> cellMap = Generics.newHashMap();
        for (int i=0; i<labels.length; i++) cellMap.put(labels[i],cells[i]);
        rows.add(cellMap);
      }
    }
    return rows;
  }
  public static List<Map<String,String>> readCSVWithHeader(String path) throws IOException {
    return readCSVWithHeader(path, '"', '"');
  }

  /**
   * Read a CSV file character by character. Allows for multi-line CSV files (in quotes), but
   * is less flexible and likely slower than readCSVWithHeader()
   * @param csvContents The char[] array corresponding to the contents of the file
   * @param numColumns The number of columns in the file (for verification, primarily)
   * @return A list of lines in the file
   */
  public static LinkedList<String[]> readCSVStrictly(char[] csvContents, int numColumns){
    //--Variables
    StringBuilder[] buffer = new StringBuilder[numColumns];
    buffer[0] = new StringBuilder();
    LinkedList<String[]> lines = new LinkedList<String[]>();
    //--State
    boolean inQuotes = false;
    boolean nextIsEscaped = false;
    int columnI = 0;
    //--Read
    for(int offset=0; offset<csvContents.length; offset++){
      if(nextIsEscaped){
        buffer[columnI].append(csvContents[offset]);
        nextIsEscaped = false;
      } else {
        switch(csvContents[offset]){
          case '"':
            //(case: quotes)
            inQuotes = !inQuotes;
            break;
          case ',':
            //(case: field separator)
            if(inQuotes){
              buffer[columnI].append(',');
            } else {
              columnI += 1;
              if(columnI >= numColumns){
                throw new IllegalArgumentException("Too many columns: "+columnI+"/"+numColumns+" (offset: " + offset + ")");
              }
              buffer[columnI] = new StringBuilder();
            }
            break;
          case '\n':
            //(case: newline)
            if(inQuotes){
              buffer[columnI].append('\n');
            } else {
              //((error checks))
              if(columnI != numColumns-1){
                throw new IllegalArgumentException("Too few columns: "+columnI+"/"+numColumns+" (offset: " + offset + ")");
              }
              //((create line))
              String[] rtn = new String[buffer.length];
              for(int i=0; i<buffer.length; i++){ rtn[i] = buffer[i].toString(); }
              lines.add(rtn);
              //((update state))
              columnI = 0;
              buffer[columnI] = new StringBuilder();
            }
            break;
          case '\\':
            nextIsEscaped = true;
            break;
          default:
            buffer[columnI].append(csvContents[offset]);
        }
      }
    }
    //--Return
    return lines;
  }
  public static LinkedList<String[]> readCSVStrictly(String filename, int numColumns) throws IOException {
    return readCSVStrictly(slurpFile(filename).toCharArray(), numColumns);
  }

  /**
   * Get a input file stream (automatically gunzip/bunzip2 depending on file extension)
   * @param filename Name of file to open
   * @return Input stream that can be used to read from the file
   * @throws IOException if there are exceptions opening the file
   */
  public static InputStream getFileInputStream(String filename) throws IOException {
    InputStream in = new FileInputStream(filename);
    if (filename.endsWith(".gz")) {
      in = new GZIPInputStream(in);
    } else if (filename.endsWith(".bz2")) {
      //in = new CBZip2InputStream(in);
      in = getBZip2PipedInputStream(filename);
    }
    return in;
  }

  /**
   * Get a output file stream (automatically gzip/bzip2 depending on file extension)
   * @param filename Name of file to open
   * @return Output stream that can be used to write to the file
   * @throws IOException if there are exceptions opening the file
   */
  public static OutputStream getFileOutputStream(String filename) throws IOException {
    OutputStream out = new FileOutputStream(filename);
    if (filename.endsWith(".gz")) {
      out = new GZIPOutputStream(out);
    } else if (filename.endsWith(".bz2")) {
      //out = new CBZip2OutputStream(out);
      out = getBZip2PipedOutputStream(filename);
    }
    return out;
  }

  public static BufferedReader getBufferedFileReader(String filename) throws IOException {
    return getBufferedFileReader(filename, defaultEncoding);
  }

  public static BufferedReader getBufferedFileReader(String filename, String encoding) throws IOException {
    InputStream in = getFileInputStream(filename);
    return new BufferedReader(new InputStreamReader(in, encoding));
  }

  public static BufferedReader getBufferedReaderFromClasspathOrFileSystem(String filename) throws IOException {
    return getBufferedReaderFromClasspathOrFileSystem(filename, defaultEncoding);
  }

  public static BufferedReader getBufferedReaderFromClasspathOrFileSystem(String filename, String encoding) throws IOException {
    InputStream in = findStreamInClasspathOrFileSystem(filename);
    return new BufferedReader(new InputStreamReader(in, encoding));
  }

  public static PrintWriter getPrintWriter(File textFile) throws IOException {
    File f = textFile.getAbsoluteFile();
    return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f))), true);
  }

  public static PrintWriter getPrintWriter(String filename) throws IOException {
    return getPrintWriter(filename, defaultEncoding);
  }

  public static PrintWriter getPrintWriterIgnoringExceptions(String filename) {
    try {
      return getPrintWriter(filename, defaultEncoding);
    } catch (IOException ioe) {
      return null;
    }
  }

  public static PrintWriter getPrintWriterOrDie(String filename) {
    try {
      return getPrintWriter(filename, defaultEncoding);
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe);
    }
  }

  public static PrintWriter getPrintWriter(String filename, String encoding) throws IOException {
    OutputStream out = getFileOutputStream(filename);
    if (encoding == null) {
      encoding = defaultEncoding;
    }
    return new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, encoding)), true);
  }

  public static InputStream getBZip2PipedInputStream(String filename) throws IOException
  {
    String bzcat = System.getProperty("bzcat", "bzcat");
    Runtime rt = Runtime.getRuntime();
    String cmd = bzcat + " " + filename;
    //System.err.println("getBZip2PipedInputStream: Running command: "+cmd);
    Process p = rt.exec(cmd);
    Writer errWriter = new BufferedWriter(new OutputStreamWriter(System.err));
    StreamGobbler errGobler = new StreamGobbler(p.getErrorStream(), errWriter);
    errGobler.start();
    return p.getInputStream();
  }

  public static OutputStream getBZip2PipedOutputStream(String filename) throws IOException
  {
    return new BZip2PipedOutputStream(filename);
  }

  private static final Pattern tab = Pattern.compile("\t");
  /**
   * Read column as set
   * @param infile - filename
   * @param field  index of field to read
   * @return a set of the entries in column field
   * @throws IOException
   */
  public static Set<String> readColumnSet(String infile, int field) throws IOException
  {
    BufferedReader br = IOUtils.getBufferedFileReader(infile);
    String line;
    Set<String> set = Generics.newHashSet();
    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.length() > 0) {
        if (field < 0) {
          set.add(line);
        } else {
          String[] fields = tab.split(line);
          if (field < fields.length) {
            set.add(fields[field]);
          }
        }
      }
    }
    br.close();
    return set;
  }

  public static <C> List<C> readObjectFromColumns(Class objClass, String filename, String[] fieldNames, String delimiter)
          throws IOException, InstantiationException, IllegalAccessException,
          NoSuchFieldException, NoSuchMethodException, InvocationTargetException
  {
    Pattern delimiterPattern = Pattern.compile(delimiter);
    List<C> list = new ArrayList<C>();
    BufferedReader br = IOUtils.getBufferedFileReader(filename);
    String line;
    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.length() > 0) {
        C item = StringUtils.columnStringToObject(objClass, line, delimiterPattern, fieldNames);
        list.add(item);
      }
    }
    br.close();
    return list;
  }

  public static Map<String,String> readMap(String filename) throws IOException
  {
    Map<String,String> map = Generics.newHashMap();
    try {
      BufferedReader br = IOUtils.getBufferedFileReader(filename);
      String line;
      while ((line = br.readLine()) != null) {
        String[] fields = tab.split(line,2);
        map.put(fields[0], fields[1]);
      }
      br.close();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return map;
  }


  /**
   * Returns the contents of a file as a single string.  The string may be
   * empty, if the file is empty.  If there is an IOException, it is caught
   * and null is returned.
   */
  public static String stringFromFile(String filename) {
    return stringFromFile(filename, defaultEncoding);
  }

  /**
   * Returns the contents of a file as a single string.  The string may be
   * empty, if the file is empty.  If there is an IOException, it is caught
   * and null is returned.  Encoding can also be specified.
   */
  public static String stringFromFile(String filename, String encoding) {
    try {
      StringBuilder sb = new StringBuilder();
      BufferedReader in = new BufferedReader(new EncodingFileReader(filename,encoding));
      String line;
      while ((line = in.readLine()) != null) {
        sb.append(line);
        sb.append(eolChar);
      }
      in.close();
      return sb.toString();
    }
    catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }


  /**
   * Returns the contents of a file as a list of strings.  The list may be
   * empty, if the file is empty.  If there is an IOException, it is caught
   * and null is returned.
   */
  public static List<String> linesFromFile(String filename) {
    return linesFromFile(filename, defaultEncoding);
  }

  /**
   * Returns the contents of a file as a list of strings.  The list may be
   * empty, if the file is empty.  If there is an IOException, it is caught
   * and null is returned. Encoding can also be specified
   */
  public static List<String> linesFromFile(String filename,String encoding) {
    try {
      List<String> lines = new ArrayList<String>();
      BufferedReader in = new BufferedReader(new EncodingFileReader(filename,encoding));
      String line;
      while ((line = in.readLine()) != null) {
        lines.add(line);
      }
      in.close();
      return lines;
    }
    catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static String backupName(String filename) {
    return backupFile(new File(filename)).toString();
  }

  public static File backupFile(File file) {
    int max = 1000;
    String filename = file.toString();
    File backup = new File(filename + "~");
    if (!backup.exists()) { return backup; }
    for (int i = 1; i <= max; i++) {
      backup = new File(filename + ".~" + i + ".~");
      if (!backup.exists()) { return backup; }
    }
    return null;
  }

  public static boolean renameToBackupName(File file) {
    return file.renameTo(backupFile(file));
  }


  /**
   * A JavaNLP specific convenience routine for obtaining the current
   * scratch directory for the machine you're currently running on.
   */
  public static File getJNLPLocalScratch()  {
    try {
      String machineName = InetAddress.getLocalHost().getHostName().split("\\.")[0];
      String username = System.getProperty("user.name");
      return new File("/"+machineName+"/scr1/"+username);
    } catch (Exception e) {
      return new File("./scr/"); // default scratch
    }
  }

  /**
   * Given a filepath, makes sure a directory exists there.  If not, creates and returns it.
   * Same as ENSURE-DIRECTORY in CL.
   *
   * @param tgtDir The directory that you wish to ensure exists
   * @throws IOException If directory can't be created, is an existing file, or for other reasons
   */
  public static File ensureDir(File tgtDir) throws IOException {
    if (tgtDir.exists()) {
      if (tgtDir.isDirectory()) {
        return tgtDir;
      } else {
        throw new IOException("Could not create directory "+tgtDir.getAbsolutePath()+", as a file already exists at that path.");
      }
    } else {
      if ( ! tgtDir.mkdirs()) {
        throw new IOException("Could not create directory "+tgtDir.getAbsolutePath());
      }
      return tgtDir;
    }
  }

  public static void main(String[] args) {
    System.out.println(backupName(args[0]));
  }

  public static String getExtension(String fileName) {
    if(!fileName.contains("."))
      return null;
    int idx = fileName.lastIndexOf('.');
    return fileName.substring(idx+1);
  }


  /** Create a Reader with an explicit encoding around an InputStream.
   *  This static method will treat null as meaning to use the platform default,
   *  unlike the Java library methods that disallow a null encoding.
   *
   *  @param stream An InputStream
   *  @param encoding A charset encoding
   *  @return A Reader
   *  @throws IOException If any IO problem
   */
  public static Reader encodedInputStreamReader(InputStream stream, String encoding) throws IOException {
    // InputStreamReader doesn't allow encoding to be null;
    if (encoding == null) {
      return new InputStreamReader(stream);
    } else {
      return new InputStreamReader(stream, encoding);
    }
  }


  /** Create a Reader with an explicit encoding around an InputStream.
   *  This static method will treat null as meaning to use the platform default,
   *  unlike the Java library methods that disallow a null encoding.
   *
   *  @param stream An InputStream
   *  @param encoding A charset encoding
   *  @return A Reader
   *  @throws IOException If any IO problem
   */
  public static Writer encodedOutputStreamWriter(OutputStream stream, String encoding) throws IOException {
    // OutputStreamWriter doesn't allow encoding to be null;
    if (encoding == null) {
      return new OutputStreamWriter(stream);
    } else {
      return new OutputStreamWriter(stream, encoding);
    }
  }


  /** Create a Reader with an explicit encoding around an InputStream.
   *  This static method will treat null as meaning to use the platform default,
   *  unlike the Java library methods that disallow a null encoding.
   *
   *  @param stream An InputStream
   *  @param encoding A charset encoding
   *  @param autoFlush Whether to make an autoflushing Writer
   *  @return A Reader
   *  @throws IOException If any IO problem
   */
  public static PrintWriter encodedOutputStreamPrintWriter(OutputStream stream,
                                                           String encoding, boolean autoFlush) throws IOException {
    // PrintWriter doesn't allow encoding to be null; or to have charset and flush
    if (encoding == null) {
      return new PrintWriter(stream, autoFlush);
    } else {
      return new PrintWriter(new OutputStreamWriter(stream, encoding), autoFlush);
    }
  }


}
