package edu.stanford.nlp.io;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * Helper Class for various I/O related things.
 *
 * @author Kayur Patel
 * @author Teg Grenager
 * @author Christopher Manning
 */

public class IOUtils  {

  private static final int SLURP_BUFFER_SIZE = 16384;

  public static final String eolChar = System.lineSeparator();  // todo: Inline
  public static final String defaultEncoding = "utf-8";

  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(IOUtils.class);

  // A class of static methods
  private IOUtils() { }

  /**
   * Write object to a file with the specified name.  The file is silently gzipped if the filename ends with .gz.
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
   * Write an object to a specified File.  The file is silently gzipped if the filename ends with .gz.
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
   * Write an object to a specified File. The file is silently gzipped if the filename ends with .gz.
   *
   * @param o Object to be written to file
   * @param file The temp File
   * @param append If true, append to this file instead of overwriting it
   * @throws IOException If File cannot be written
   * @return File containing the object
   */
  public static File writeObjectToFile(Object o, File file, boolean append) throws IOException {
    // file.createNewFile(); // cdm may 2005: does nothing needed
    OutputStream os = new FileOutputStream(file, append);
    if (file.getName().endsWith(".gz")) {
      os = new GZIPOutputStream(os);
    }
    os = new BufferedOutputStream(os);
    ObjectOutputStream oos = new ObjectOutputStream(os);
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
      logger.err(throwableToStackTrace(e));
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
      logger.error("Error writing object to file " + filename);
      logger.err(throwableToStackTrace(e));
      return null;
    }
  }

  private static OutputStream getBufferedOutputStream(String path) throws IOException {
    OutputStream os = new BufferedOutputStream(new FileOutputStream(path));
    if (path.endsWith(".gz")) {
      os = new GZIPOutputStream(os);
    }
    return os;
  }

  //++ todo [cdm, Aug 2012]: Do we need the below methods? They're kind of weird in unnecessarily bypassing using a Writer.

  /**
   * Writes a string to a file.
   *
   * @param contents The string to write
   * @param path The file path
   * @param encoding The encoding to encode in
   * @throws IOException In case of failure
   */
  public static void writeStringToFile(String contents, String path, String encoding) throws IOException {
    OutputStream writer = getBufferedOutputStream(path);
    writer.write(contents.getBytes(encoding));
    writer.close();
  }

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
      logger.err(throwableToStackTrace(e));
    } finally {
      closeIgnoringExceptions(writer);
    }
  }

  /**
   * Writes a string to a temporary file.
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
    writer.close();
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
      logger.err(throwableToStackTrace(e));
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

  //-- todo [cdm, Aug 2012]: Do we need the below methods? They're kind of weird in unnecessarily bypassing using a Writer.


  // todo [cdm, Sep 2013]: Can we remove this next method and its friends? (Weird in silently gzipping, overlaps other functionality.)
  /**
   * Read an object from a stored file. It is silently ungzipped, regardless of name.
   *
   * @param file The file pointing to the object to be retrieved
   * @throws IOException If file cannot be read
   * @throws ClassNotFoundException If reading serialized object fails
   * @return The object read from the file.
   */
  public static <T> T readObjectFromFile(File file) throws IOException,
          ClassNotFoundException {
    try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
            new GZIPInputStream(new FileInputStream(file))))) {
      Object o = ois.readObject();
      return ErasureUtils.uncheckedCast(o);
    } catch (java.util.zip.ZipException e) {
      try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
              new FileInputStream(file)))) {
        Object o = ois.readObject();
        return ErasureUtils.uncheckedCast(o);
      }
    }
  }

  public static DataInputStream getDataInputStream(String filenameUrlOrClassPath) throws IOException {
    return new DataInputStream(getInputStreamFromURLOrClasspathOrFileSystem(filenameUrlOrClassPath));
  }

  public static DataOutputStream getDataOutputStream(String filename) throws IOException {
    return new DataOutputStream(getBufferedOutputStream((filename)));
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
    try (ObjectInputStream ois = new ObjectInputStream(getInputStreamFromURLOrClasspathOrFileSystem(filename))) {
      Object o = ois.readObject();
      return ErasureUtils.uncheckedCast(o);
    }
  }

  public static <T> T readObjectAnnouncingTimingFromURLOrClasspathOrFileSystem(Redwood.RedwoodChannels log, String msg, String path) {
    T obj;
    Timing timing = new Timing();
    try {
      obj = IOUtils.readObjectFromURLOrClasspathOrFileSystem(path);
      log.info(msg + ' ' + path + " ... done [" + timing.toSecondsString() + " sec].");
    } catch (IOException | ClassNotFoundException e) {
      log.info(msg + ' ' + path + " ... failed! [" + timing.toSecondsString() + " sec].");
      throw new RuntimeIOException(e);
    }
    return obj;
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
    } catch (IOException | ClassNotFoundException e) {
      logger.err(throwableToStackTrace(e));
    }
    return ErasureUtils.uncheckedCast(o);
  }

  public static int lineCount(String textFileOrUrl) throws IOException {
    try (BufferedReader r = readerFromString(textFileOrUrl)) {
      int numLines = 0;
      while (r.readLine() != null) {
        numLines++;
      }
      return numLines;
    }
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

  /**
   * Returns an ObjectInputStream reading from any of a URL, a CLASSPATH resource, or a file.
   * The CLASSPATH takes priority over the file system.
   * This stream is buffered and, if necessary, gunzipped.
   *
   * @param filenameOrUrl The String specifying the URL/resource/file to load
   * @return An ObjectInputStream for loading a resource
   * @throws RuntimeIOException On any IO error
   * @throws NullPointerException Input parameter is null
   */
  public static ObjectInputStream readStreamFromString(String filenameOrUrl)
          throws IOException {
    InputStream is = getInputStreamFromURLOrClasspathOrFileSystem(filenameOrUrl);
    return new ObjectInputStream(is);
  }

  /**
   * Locates a file in the CLASSPATH if it exists.  Checks both the
   * System classloader and the IOUtils classloader, since we had
   * separate users asking for both of those changes.
   */
  private static InputStream findStreamInClassLoader(String name) {
    InputStream is = ClassLoader.getSystemResourceAsStream(name);
    if (is != null)
      return is;

    // windows File.separator is \, but getting resources only works with /
    is = ClassLoader.getSystemResourceAsStream(name.replaceAll("\\\\", "/"));
    if (is != null)
      return is;

    // Classpath doesn't like double slashes (e.g., /home/user//foo.txt)
    is = ClassLoader.getSystemResourceAsStream(name.replaceAll("\\\\", "/").replaceAll("/+", "/"));
    if (is != null)
      return is;

    is = IOUtils.class.getClassLoader().getResourceAsStream(name);
    if (is != null)
      return is;

    is = IOUtils.class.getClassLoader().getResourceAsStream(name.replaceAll("\\\\", "/"));
    if (is != null)
      return is;

    is = IOUtils.class.getClassLoader().getResourceAsStream(name.replaceAll("\\\\", "/").replaceAll("/+", "/"));
    // at this point we've tried everything
    return is;
  }

  /**
   * Locates this file either in the CLASSPATH or in the file system. The CLASSPATH takes priority.
   * Note that this method uses the ClassLoader methods, so that classpath resources must be specified as
   * absolute resource paths without a leading "/".
   *
   * @param name The file or resource name
   * @throws FileNotFoundException If the file does not exist
   * @return The InputStream of name, or null if not found
   */
  private static InputStream findStreamInClasspathOrFileSystem(String name) throws FileNotFoundException {
    // ms 10-04-2010:
    // - even though this may look like a regular file, it may be a path inside a jar in the CLASSPATH
    // - check for this first. This takes precedence over the file system.
    InputStream is = findStreamInClassLoader(name);
    // if not found in the CLASSPATH, load from the file system
    if (is == null) {
      is = new FileInputStream(name);
    }
    return is;
  }

  /**
   * Check if this path exists either in the classpath or on the filesystem.
   *
   * @param name The file or resource name.
   * @return true if a call to {@link IOUtils#getBufferedReaderFromClasspathOrFileSystem(String)} would return a valid stream.
   */
  public static boolean existsInClasspathOrFileSystem(String name) {
    InputStream is = findStreamInClassLoader(name);
    return is != null || new File(name).exists();
  }

  /**
   * Locates this file either using the given URL, or in the CLASSPATH, or in the file system
   * The CLASSPATH takes priority over the file system!
   * This stream is buffered and gunzipped (if necessary).
   *
   * @param textFileOrUrl The String specifying the URL/resource/file to load
   * @return An InputStream for loading a resource
   * @throws IOException On any IO error
   * @throws NullPointerException Input parameter is null
   */
  public static InputStream getInputStreamFromURLOrClasspathOrFileSystem(String textFileOrUrl)
          throws IOException, NullPointerException {
    InputStream in;
    if (textFileOrUrl == null) {
      throw new NullPointerException("Attempt to open file with null name");
    } else if (textFileOrUrl.matches("https?://.*")) {
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
          // Don't make the original exception a cause, since it is usually bogus
          throw new IOException("Unable to open \"" +
                  textFileOrUrl + "\" as " + "class path, filename or URL"); // , e2);
        }
      }
    }

    // If it is a GZIP stream then ungzip it
    if (textFileOrUrl.endsWith(".gz")) {
      try {
        in = new GZIPInputStream(in);
      } catch (Exception e) {
        throw new RuntimeIOException("Resource or file looks like a gzip file, but is not: " + textFileOrUrl, e);
      }
    }

    // buffer this stream.  even gzip streams benefit from buffering,
    // such as for the shift reduce parser [cdm 2016: I think this is only because default buffer is small; see below]
    in = new BufferedInputStream(in);

    return in;
  }


  // todo [cdm 2015]: I think GZIPInputStream has its own buffer and so we don't need to buffer in that case.
  // todo: Though it's default size is 512 bytes so need to make 8K in constructor. Or else buffering outside gzip is faster
  // todo: final InputStream is = new GZIPInputStream( new FileInputStream( file ), 65536 );
  /**
   * Quietly opens a File. If the file ends with a ".gz" extension,
   * automatically opens a GZIPInputStream to wrap the constructed
   * FileInputStream.
   */
  public static InputStream inputStreamFromFile(File file) throws RuntimeIOException {
    InputStream is = null;
    try {
      is = new BufferedInputStream(new FileInputStream(file));
      if (file.getName().endsWith(".gz")) {
        is = new GZIPInputStream(is);
      }
      return is;
    } catch (IOException e) {
      IOUtils.closeIgnoringExceptions(is);
      throw new RuntimeIOException(e);
    }
  }


  /**
   * Open a BufferedReader to a File. If the file's getName() ends in .gz,
   * it is interpreted as a gzipped file (and uncompressed). The file is then
   * interpreted as a utf-8 text file.
   *
   * @param file What to read from
   * @return The BufferedReader
   * @throws RuntimeIOException If there is an I/O problem
   */
  public static BufferedReader readerFromFile(File file) {
    InputStream is = inputStreamFromFile(file);
    return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
  }


  // todo [cdm 2014]: get rid of this method, using other methods. This will change the semantics to null meaning UTF-8, but that seems better in 2015.
  /**
   * Open a BufferedReader to a File. If the file's getName() ends in .gz,
   * it is interpreted as a gzipped file (and uncompressed). The file is then
   * turned into a BufferedReader with the given encoding.
   * If the encoding passed in is null, then the system default encoding is used.
   *
   * @param file What to read from
   * @param encoding What charset to use. A null String is interpreted as platform default encoding
   * @return The BufferedReader
   * @throws RuntimeIOException If there is an I/O problem
   */
  public static BufferedReader readerFromFile(File file, String encoding) {
    InputStream is = null;
    try {
      is = inputStreamFromFile(file);
      if (encoding == null) {
        return new BufferedReader(new InputStreamReader(is));
      } else {
        return new BufferedReader(new InputStreamReader(is, encoding));
      }
    } catch (IOException ioe) {
      IOUtils.closeIgnoringExceptions(is);
      throw new RuntimeIOException(ioe);
    }
  }


  /**
   * Open a BufferedReader on stdin. Use the user's default encoding.
   *
   * @return The BufferedReader
   */
  public static BufferedReader readerFromStdin() {
    return new BufferedReader(new InputStreamReader(System.in));
  }

  /**
   * Open a BufferedReader on stdin. Use the specified character encoding.
   *
   * @param encoding CharSet encoding. Maybe be null, in which case the
   *         platform default encoding is used
   * @return The BufferedReader
   * @throws IOException If there is an I/O problem
   */
  public static BufferedReader readerFromStdin(String encoding) throws IOException {
    if (encoding == null) {
      return new BufferedReader(new InputStreamReader(System.in));
    }
    return new BufferedReader(new InputStreamReader(System.in, encoding));
  }


  // TODO [cdm 2015]: Should we rename these methods. Sort of misleading: They really read files, resources, etc. specified by a String
  /**
   * Open a BufferedReader to a file, class path entry or URL specified by a String name.
   * If the String starts with https?://, then it is first tried as a URL. It
   * is next tried as a resource on the CLASSPATH, and then it is tried
   * as a local file. Finally, it is then tried again in case it is some network-available
   * file accessible by URL. If the String ends in .gz, it
   * is interpreted as a gzipped file (and uncompressed). The file is then
   * interpreted as a utf-8 text file.
   * Note that this method uses the ClassLoader methods, so that classpath resources must be specified as
   * absolute resource paths without a leading "/".
   *
   * @param textFileOrUrl What to read from
   * @return The BufferedReader
   * @throws IOException If there is an I/O problem
   */
  public static BufferedReader readerFromString(String textFileOrUrl)
          throws IOException {
    return new BufferedReader(new InputStreamReader(
            getInputStreamFromURLOrClasspathOrFileSystem(textFileOrUrl), StandardCharsets.UTF_8));
  }

  /**
   * Open a BufferedReader to a file or URL specified by a String name. If the
   * String starts with https?://, then it is first tried as a URL, otherwise it
   * is next tried as a resource on the CLASSPATH, and then finally it is tried
   * as a local file or other network-available file . If the String ends in .gz, it
   * is interpreted as a gzipped file (and uncompressed), else it is interpreted as
   * a regular text file in the given encoding.
   * If the encoding passed in is null, then the system default encoding is used.
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
    return readLines(path, null);
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
    return new GetLinesIterable(path, null, encoding);
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
    return new GetLinesIterable(file, fileInputStreamWrapper, encoding);
  }

  static class GetLinesIterable implements Iterable<String> {
    final File file;
    final String path;
    final Class<? extends InputStream> fileInputStreamWrapper;
    final String encoding;

    // TODO: better programming style would be to make this two
    // separate classes, but we don't expect to make more versions of
    // this class anyway
    GetLinesIterable(final File file,
                     final Class<? extends InputStream> fileInputStreamWrapper,
                     final String encoding) {
      this.file = file;
      this.path = null;
      this.fileInputStreamWrapper = fileInputStreamWrapper;
      this.encoding = encoding;
    }

    GetLinesIterable(final String path,
                     final Class<? extends InputStream> fileInputStreamWrapper,
                     final String encoding) {
      this.file = null;
      this.path = path;
      this.fileInputStreamWrapper = fileInputStreamWrapper;
      this.encoding = encoding;
    }

    private InputStream getStream() throws IOException {
      if (file != null) {
        return inputStreamFromFile(file);
      } else if (path != null) {
        return getInputStreamFromURLOrClasspathOrFileSystem(path);
      } else {
        throw new AssertionError("No known path to read");
      }
    }

    @Override
    public Iterator<String> iterator() {
      return new Iterator<String>() {

        protected final BufferedReader reader = this.getReader();
        protected String line = this.getLine();
        private boolean readerOpen = true;

        @Override
        public boolean hasNext() {
          return this.line != null;
        }

        @Override
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
              readerOpen = false;
              this.reader.close();
            }
            return result;
          } catch (IOException e) {
            throw new RuntimeIOException(e);
          }
        }

        protected BufferedReader getReader() {
          try {
            InputStream stream = getStream();
            if (fileInputStreamWrapper != null) {
              stream = fileInputStreamWrapper.getConstructor(InputStream.class).newInstance(stream);
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

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }

        // todo [cdm 2018]: Probably should remove this but in current implementation reader is internal and can only close by getting to eof.
        protected void finalize() throws Throwable {
          super.finalize();
          if (readerOpen) {
            logger.warn("Forgot to close FileIterable -- closing from finalize()");
            reader.close();
          }
        }
      };
    }

  } // end static class GetLinesIterable

  /**
   * Given a reader, returns the lines from the reader as an Iterable.
   *
   * @param r  input reader
   * @param includeEol whether to keep eol-characters in the returned strings
   * @return iterable of lines (as strings)
   */
  public static Iterable<String> getLineIterable( Reader r, boolean includeEol) {
    if (includeEol) {
      return new EolPreservingLineReaderIterable(r);
    } else {
      return new LineReaderIterable( (r instanceof BufferedReader)? (BufferedReader) r:new BufferedReader(r) );
    }
  }

  public static Iterable<String> getLineIterable( Reader r, int bufferSize, boolean includeEol) {
    if (includeEol) {
      return new EolPreservingLineReaderIterable(r, bufferSize);
    } else {
      return new LineReaderIterable( (r instanceof BufferedReader)? (BufferedReader) r:new BufferedReader(r, bufferSize) );
    }
  }

  /**
   * Line iterator that uses BufferedReader.readLine()
   * EOL-characters are automatically discarded and not included in the strings returns
   */
  private static final class LineReaderIterable implements Iterable<String>
  {
    private final BufferedReader reader;

    private LineReaderIterable( BufferedReader reader )
    {
      this.reader = reader;
    }
    @Override
    public Iterator<String> iterator()
    {
      return new Iterator<String>() {
        private String next = getNext();

        private String getNext() {
          try {
            return reader.readLine();
          } catch (IOException ex) {
            throw new RuntimeIOException(ex);
          }
        }

        @Override
        public boolean hasNext()
        {
          return this.next != null;
        }
        @Override
        public String next()
        {
          String nextLine = this.next;
          if (nextLine == null) {
            throw new NoSuchElementException();
          }
          next = getNext();
          return nextLine;
        }

        @Override
        public void remove()
        {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  /**
   * Line iterator that preserves the eol-character exactly as read from reader.
   * Line endings are: \r\n,\n,\r
   * Lines returns by this iterator will include the eol-characters
   **/
  private static final class EolPreservingLineReaderIterable implements Iterable<String> {

    private final Reader reader;
    private final int bufferSize;

    private EolPreservingLineReaderIterable( Reader reader )
    {
      this(reader, SLURP_BUFFER_SIZE);
    }
    private EolPreservingLineReaderIterable( Reader reader, int bufferSize ) {
      this.reader = reader;
      this.bufferSize = bufferSize;
    }

    @Override
    public Iterator<String> iterator() {
      return new Iterator<String>() {
        private String next;
        private boolean done = false;

        private final StringBuilder sb = new StringBuilder(80);
        private final char[] charBuffer = new char[bufferSize];
        private int charBufferPos = -1;
        private int charsInBuffer = 0;
        boolean lastWasLF = false;

        private String getNext() {
          try {
            while (true) {
              if (charBufferPos < 0) {
                charsInBuffer = reader.read(charBuffer);
                if (charsInBuffer < 0) {
                  // No more!!!
                  if (sb.length() > 0) {
                    String line = sb.toString();
                    // resets the buffer
                    sb.setLength(0);
                    return line;
                  } else {
                    return null;
                  }
                }
                charBufferPos = 0;
              }

              boolean eolReached = copyUntilEol();
              if (eolReached) {
                // eol reached
                String line = sb.toString();
                // resets the buffer
                sb.setLength(0);
                return line;
              }
            }
          } catch (IOException ex) {
            throw new RuntimeIOException(ex);
          }
        }

        private boolean copyUntilEol() {
          for (int i = charBufferPos; i < charsInBuffer; i++) {
            if (charBuffer[i] == '\n') {
              // line end
              // copy into our string builder
              sb.append(charBuffer, charBufferPos, i - charBufferPos + 1);
              // advance character buffer pos
              charBufferPos = i+1;
              lastWasLF = false;
              return true; // end of line reached
            } else if (lastWasLF) {
              // not a '\n' here - still need to terminate line (but don't include current character)
              if (i > charBufferPos) {
                sb.append(charBuffer, charBufferPos, i - charBufferPos);
                // advance character buffer pos
                charBufferPos = i;
                lastWasLF = false;
                return true; // end of line reached
              }
            }
            lastWasLF = (charBuffer[i] == '\r');
          }
          sb.append(charBuffer, charBufferPos, charsInBuffer - charBufferPos);
          // reset character buffer pos
          charBufferPos = -1;
          return false;
        }

        @Override
        public boolean hasNext()
        {
          if (done) return false;
          if (next == null) {
            next = getNext();
          }
          if (next == null) {
            done = true;
          }
          return !done;
        }

        @Override
        public String next()
        {
          if (!hasNext()) { throw new NoSuchElementException(); }
          String res = next;
          next = null;
          return res;
        }

        @Override
        public void remove()
        {
          throw new UnsupportedOperationException();
        }
      };
    } // end iterator()

  } // end static class EolPreservingLineReaderIterable

  /**
   * Provides an implementation of closing a file for use in a finally block so
   * you can correctly close a file without even more exception handling stuff.
   * From a suggestion in a talk by Josh Bloch. Calling close() will flush().
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
   * @param dir The root directory.
   * @return All files within the directory.
   */
  public static Iterable<File> iterFilesRecursive(final File dir) {
    return iterFilesRecursive(dir, (Pattern) null);
  }

  /**
   * Iterate over all the files in the directory, recursively.
   *
   * @param dir The root directory.
   * @param ext A string that must be at the end of all files (e.g. ".txt")
   * @return All files within the directory ending in the given extension.
   */
  public static Iterable<File> iterFilesRecursive(final File dir,
                                                  final String ext) {
    return iterFilesRecursive(dir, Pattern.compile(Pattern.quote(ext) + "$"));
  }

  /**
   * Iterate over all the files in the directory, recursively.
   *
   * @param dir The root directory.
   * @param pattern A regular expression that the file path must match. This uses
   *          Matcher.find(), so use ^ and $ to specify endpoints.
   * @return All files within the directory.
   */
  public static Iterable<File> iterFilesRecursive(final File dir,
                                                  final Pattern pattern) {
    return new Iterable<File>() {
      public Iterator<File> iterator() {
        return new AbstractIterator<File>() {
          private final Queue<File> files = new LinkedList<>(Collections
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
   * Returns all the text in the given File as a single String.
   * If the file's name ends in .gz, it is assumed to be gzipped and is silently uncompressed.
   */
  public static String slurpFile(File file) throws IOException {
    return slurpFile(file, null);
  }

  /**
   * Returns all the text in the given File as a single String.
   * If the file's name ends in .gz, it is assumed to be gzipped and is silently uncompressed.
   *
   * @param file The file to read from
   * @param encoding The character encoding to assume.  This may be null, and
   *       the platform default character encoding is used.
   */
  public static String slurpFile(File file, String encoding) throws IOException {
    return IOUtils.slurpReader(IOUtils.encodedInputStreamReader(
            inputStreamFromFile(file), encoding));
  }

  /**
   * Returns all the text in the given File as a single String.
   */
  public static String slurpGZippedFile(String filename) throws IOException {
    Reader r = encodedInputStreamReader(new GZIPInputStream(new FileInputStream(
            filename)), null);
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given File as a single String.
   */
  public static String slurpGZippedFile(File file) throws IOException {
    Reader r = encodedInputStreamReader(new GZIPInputStream(new FileInputStream(
            file)), null);
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given file with the given encoding.
   * The string may be empty, if the file is empty.
   */
  public static String slurpFile(String filename, String encoding)
          throws IOException {
    Reader r = readerFromString(filename, encoding);
    return IOUtils.slurpReader(r);
  }

  /**
   * Returns all the text in the given file with the given
   * encoding. If the file cannot be read (non-existent, etc.), then
   * the method throws an unchecked RuntimeIOException.  If the caller
   * is willing to tolerate missing files, they should catch that
   * exception.
   */
  public static String slurpFileNoExceptions(String filename, String encoding) {
    try {
      return slurpFile(filename, encoding);
    } catch (IOException e) {
      throw new RuntimeIOException("slurpFile IO problem", e);
    }
  }

  /**
   * Returns all the text in the given file
   *
   * @return The text in the file.
   */
  public static String slurpFile(String filename) throws IOException {
    return slurpFile(filename, defaultEncoding);
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURLNoExceptions(URL u, String encoding) {
    try {
      return IOUtils.slurpURL(u, encoding);
    } catch (Exception e) {
      logger.err(throwableToStackTrace(e));
      return null;
    }
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURL(URL u, String encoding) throws IOException {
    String lineSeparator = System.lineSeparator();
    URLConnection uc = u.openConnection();
    uc.setReadTimeout(30000);
    InputStream is;
    try {
      is = uc.getInputStream();
    } catch (SocketTimeoutException e) {
      logger.error("Socket time out; returning empty string.");
      logger.err(throwableToStackTrace(e));
      return "";
    }
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, encoding))) {
      StringBuilder buff = new StringBuilder(SLURP_BUFFER_SIZE); // make biggish
      for (String temp; (temp = br.readLine()) != null; ) {
        buff.append(temp);
        buff.append(lineSeparator);
      }
      return buff.toString();
    }
  }

  public static String getUrlEncoding(URLConnection connection) {
    String contentType = connection.getContentType();
    String[] values = contentType.split(";");
    String charset = defaultEncoding;  // might or might not be right....

    for (String value : values) {
      value = value.trim();
      if (value.toLowerCase(Locale.ENGLISH).startsWith("charset=")) {
        charset = value.substring("charset=".length());
      }
    }
    return charset;
  }


  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURL(URL u) throws IOException {
    URLConnection uc = u.openConnection();
    String encoding = getUrlEncoding(uc);
    InputStream is = uc.getInputStream();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, encoding))) {
      StringBuilder buff = new StringBuilder(SLURP_BUFFER_SIZE); // make biggish
      String lineSeparator = System.lineSeparator();
      for (String temp; (temp = br.readLine()) != null; ) {
        buff.append(temp);
        buff.append(lineSeparator);
      }
      return buff.toString();
    }
  }

  /**
   * Returns all the text at the given URL.
   */
  public static String slurpURLNoExceptions(URL u) {
    try {
      return slurpURL(u);
    } catch (Exception e) {
      logger.err(throwableToStackTrace(e));
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
   * {@code null}.
   */
  public static String slurpURLNoExceptions(String path) {
    try {
      return slurpURL(path);
    } catch (Exception e) {
      logger.err(throwableToStackTrace(e));
      return null;
    }
  }

  /**
   * Returns all the text in the given file with the given
   * encoding. If the file cannot be read (non-existent, etc.), then
   * the method throws an unchecked RuntimeIOException.  If the caller
   * is willing to tolerate missing files, they should catch that
   * exception.
   */
  public static String slurpFileNoExceptions(File file) {
    try {
      return IOUtils.slurpReader(encodedInputStreamReader(new FileInputStream(file), null));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Returns all the text in the given file with the given
   * encoding. If the file cannot be read (non-existent, etc.), then
   * the method throws an unchecked RuntimeIOException.  If the caller
   * is willing to tolerate missing files, they should catch that
   * exception.
   */
  public static String slurpFileNoExceptions(String filename) {
    try {
      return slurpFile(filename);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Returns all the text from the given Reader.
   * Closes the Reader when done.
   *
   * @return The text in the file.
   */
  public static String slurpReader(Reader reader) {
    StringBuilder buff = new StringBuilder();
    try (BufferedReader r = new BufferedReader(reader)) {
      char[] chars = new char[SLURP_BUFFER_SIZE];
      while (true) {
        int amountRead = r.read(chars, 0, SLURP_BUFFER_SIZE);
        if (amountRead < 0) {
          break;
        }
        buff.append(chars, 0, amountRead);
      }
    } catch (Exception e) {
      throw new RuntimeIOException("slurpReader IO problem", e);
    }
    return buff.toString();
  }

  /**
   * Read the contents of an input stream, decoding it according to the given character encoding.
   * @param input The input stream to read from
   * @return The String representation of that input stream
   */
  public static String slurpInputStream(InputStream input, String encoding) throws IOException {
    return slurpReader(encodedInputStreamReader(input, encoding));
  }

  /**
   * Send all bytes from the input stream to the output stream.
   *
   * @param input The input bytes.
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

  /**
   * Read in a CSV formatted file with a header row.
   *
   * @param path - path to CSV file
   * @param quoteChar - character for enclosing strings, defaults to "
   * @param escapeChar - character for escaping quotes appearing in quoted strings; defaults to " (i.e. "" is used for " inside quotes, consistent with Excel)
   * @return a list of maps representing the rows of the csv. The maps' keys are the header strings and their values are the row contents
   */
  public static List<Map<String,String>> readCSVWithHeader(String path, char quoteChar, char escapeChar) {
    String[] labels = null;
    List<Map<String,String>> rows = Generics.newArrayList();
    for (String line : IOUtils.readLines(path)) {
      // logger.info("Splitting "+line);
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
  public static List<Map<String,String>> readCSVWithHeader(String path) {
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
    LinkedList<String[]> lines = new LinkedList<>();
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
   * Get a input file stream (automatically gunzip depending on file extension)
   * @param filename Name of file to open
   * @return Input stream that can be used to read from the file
   * @throws IOException if there are exceptions opening the file
   */
  public static InputStream getFileInputStream(String filename) throws IOException {
    InputStream in = new FileInputStream(filename);
    if (filename.endsWith(".gz")) {
      in = new GZIPInputStream(in);
    }
    return in;
  }

  /**
   * Get a output file stream (automatically gzip depending on file extension)
   * @param filename Name of file to open
   * @return Output stream that can be used to write to the file
   * @throws IOException if there are exceptions opening the file
   */
  public static OutputStream getFileOutputStream(String filename) throws IOException {
    OutputStream out = new FileOutputStream(filename);
    if (filename.endsWith(".gz")) {
      out = new GZIPOutputStream(out);
    }
    return out;
  }

  public static OutputStream getFileOutputStream(String filename, boolean append) throws IOException {
    OutputStream out = new FileOutputStream(filename, append);
    if (filename.endsWith(".gz")) {
      out = new GZIPOutputStream(out);
    }
    return out;
  }

  /** @deprecated Just call readerFromString(filename) */
  @Deprecated
  public static BufferedReader getBufferedFileReader(String filename) throws IOException {
    return readerFromString(filename, defaultEncoding);
  }

  /** @deprecated Just call readerFromString(filename) */
  @Deprecated
  public static BufferedReader getBufferedReaderFromClasspathOrFileSystem(String filename) throws IOException {
    return readerFromString(filename, defaultEncoding);
  }

  public static PrintWriter getPrintWriter(File textFile) throws IOException {
    return getPrintWriter(textFile, null);
  }

  public static PrintWriter getPrintWriter(File textFile, String encoding) throws IOException {
    File f = textFile.getAbsoluteFile();
    if (encoding == null) {
      encoding = defaultEncoding;
    }
    return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), encoding)), true);
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

  private static final Pattern tab = Pattern.compile("\t");

  /**
   * Read column as set
   * @param infile filename
   * @param field  index of field to read
   * @return a set of the entries in column field
   * @throws IOException If I/O error
   */
  public static Set<String> readColumnSet(String infile, int field) throws IOException {
    BufferedReader br = IOUtils.readerFromString(infile);

    Set<String> set = Generics.newHashSet();
    for (String line; (line = br.readLine()) != null; ) {
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
    List<C> list = new ArrayList<>();
    BufferedReader br = IOUtils.readerFromString(filename);
    for (String line; (line = br.readLine()) != null; ) {
      line = line.trim();
      if (line.length() > 0) {
        C item = StringUtils.columnStringToObject(objClass, line, delimiterPattern, fieldNames);
        list.add(item);
      }
    }
    br.close();
    return list;
  }

  public static Map<String,String> readMap(String filename) {
    Map<String,String> map = Generics.newHashMap();
    try {
      BufferedReader br = IOUtils.readerFromString(filename);

      for (String line; (line = br.readLine()) != null; ) {
        String[] fields = tab.split(line,2);
        map.put(fields[0], fields[1]);
      }
      br.close();
    } catch (IOException ex) {
      throw new RuntimeIOException(ex);
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
  // todo: This is same as slurpFile (!)
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
    } catch (IOException e) {
      logger.err(throwableToStackTrace(e));
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
    return linesFromFile(filename, encoding, false);
  }

  public static List<String> linesFromFile(String filename,String encoding, boolean ignoreHeader) {
    try {
      List<String> lines = new ArrayList<>();
      BufferedReader in = readerFromString(filename, encoding);
      String line;
      int i = 0;
      while ((line = in.readLine()) != null) {
        i++;
        if(ignoreHeader && i == 1)
          continue;
        lines.add(line);
      }
      in.close();
      return lines;
    }
    catch (IOException e) {
      logger.err(throwableToStackTrace(e));
      return null;
    }
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

  /**
   * Given a filepath, delete all files in the directory recursively
   * @param dir Directory from which to delete files
   * @return {@code true} if the deletion is successful, {@code false} otherwise
   */
  public static boolean deleteDirRecursively(File dir) {
    if (dir.isDirectory()) {
      for (File f : dir.listFiles()) {
        boolean success = deleteDirRecursively(f);
        if (!success)
          return false;
      }
    }
    return dir.delete();
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


  /**
   * A raw file copy function -- this is not public since no error checks are made as to the
   * consistency of the file being copied. Use instead:
   * @see IOUtils#cp(java.io.File, java.io.File, boolean)
   * @param source The source file. This is guaranteed to exist, and is guaranteed to be a file.
   * @param target The target file.
   * @throws IOException Throws an exception if the copy fails.
   */
  private static void copyFile(File source, File target) throws IOException {
    FileChannel sourceChannel = new FileInputStream( source ).getChannel();
    FileChannel targetChannel = new FileOutputStream( target ).getChannel();

    // allow for the case that it doesn't all transfer in one go (though it probably does for a file cp)
    long pos = 0;
    long toCopy = sourceChannel.size();
    while (toCopy > 0) {
      long bytes = sourceChannel.transferTo(pos, toCopy, targetChannel);
      pos += bytes;
      toCopy -= bytes;
    }

    sourceChannel.close();
    targetChannel.close();
  }


  /**
   * <p>An implementation of cp, as close to the Unix command as possible.
   * Both directories and files are valid for either the source or the target;
   * if the target exists, the semantics of Unix cp are [intended to be] obeyed.</p>
   *
   * @param source The source file or directory.
   * @param target The target to write this file or directory to.
   * @param recursive If true, recursively copy directory contents
   * @throws IOException If either the copy fails (standard IO Exception), or the command is invalid
   *                     (e.g., copying a directory without the recursive flag)
   */
  public static void cp(File source, File target, boolean recursive) throws IOException {
    // Error checks
    if (source.isDirectory() && !recursive) {
      // cp a b -- a is a directory
      throw new IOException("cp: omitting directory: " + source);
    }
    if (!target.getParentFile().exists()) {
      // cp a b/c/d/e -- b/c/d doesn't exist
      throw new IOException("cp: cannot copy to directory: " + recursive + " (parent doesn't exist)");
    }
    if (!target.getParentFile().isDirectory()) {
      // cp a b/c/d/e -- b/c/d is a regular file
      throw new IOException("cp: cannot copy to directory: " + recursive + " (parent isn't a directory)");
    }
    // Get true target
    File trueTarget;
    if (target.exists() && target.isDirectory()) {
      trueTarget = new File(target.getPath() + File.separator + source.getName());
    } else {
      trueTarget = target;
    }
    // Copy
    if (source.isFile()) {
      // Case: copying a file
      copyFile(source, trueTarget);
    } else if (source.isDirectory()) {
      // Case: copying a directory
      File[] children = source.listFiles();
      if (children == null) { throw new IOException("cp: could not list files in source: " + source); }

      if (target.exists()) {
        // Case: cp -r a b -- b exists
        if (!target.isDirectory()) {
          // cp -r a b -- b is a regular file
          throw new IOException("cp: cannot copy directory into regular file: " + target);
        }
        if (trueTarget.exists() && !trueTarget.isDirectory()) {
          // cp -r a b -- b/a is not a directory
          throw new IOException("cp: overwriting a file with a directory: " + trueTarget);
        }
        if (!trueTarget.exists() && !trueTarget.mkdir()) {
          // cp -r a b -- b/a cannot be created
          throw new IOException("cp: could not create directory: " + trueTarget);
        }
      } else {
        // Case: cp -r a b -- b does not exist
        assert trueTarget == target;
        if (!trueTarget.mkdir()) {
          // cp -r a b -- cannot create b as a directory
          throw new IOException("cp: could not create target directory: " + trueTarget);
        }
      }
      // Actually do the copy
      for (File child : children) {
        File childTarget = new File(trueTarget.getPath() + File.separator + child.getName());
        cp(child, childTarget, recursive);
      }
    } else {
      throw new IOException("cp: unknown file type: " + source);
    }
  }

  /**
   * @see IOUtils#cp(java.io.File, java.io.File, boolean)
   */
  public static void cp(File source, File target) throws IOException { cp(source, target, false); }

  /**
   * A Java implementation of the Unix tail functionality.
   * That is, read the last n lines of the input file f.
   * @param f The file to read the last n lines from
   * @param n The number of lines to read from the end of the file.
   * @param encoding The encoding to read the file in.
   * @return The read lines, one String per line.
   * @throws IOException if the file could not be read.
   */
  public static String[] tail(File f, int n, String encoding) throws IOException {
    if (n == 0) { return new String[0]; }
    // Variables
    RandomAccessFile raf = new RandomAccessFile(f, "r");
    int linesRead = 0;
    List<Byte> bytes = new ArrayList<>();
    List<String> linesReversed = new ArrayList<>();
    // Seek to end of file
    long length = raf.length() - 1;
    raf.seek(length);
    // Read backwards
    for(long seek = length; seek >= 0; --seek){
      // Seek back
      raf.seek(seek);
      // Read the next character
      byte c = raf.readByte();
      if(c == '\n'){
        // If it's a newline, handle adding the line
        byte[] str = new byte[bytes.size()];
        for (int i = 0; i < str.length; ++i) {
          str[i] = bytes.get(str.length - i - 1);
        }
        linesReversed.add(new String(str, encoding));
        bytes = new ArrayList<>();
        linesRead += 1;
        if (linesRead == n){
          break;
        }
      } else {
        // Else, register the character for later
        bytes.add(c);
      }
    }
    // Add any remaining lines
    if (linesRead < n && bytes.size() > 0) {
      byte[] str = new byte[bytes.size()];
      for (int i = 0; i < str.length; ++i) {
        str[i] = bytes.get(str.length - i - 1);
      }
      linesReversed.add(new String(str, encoding));
    }
    // Create output
    String[] rtn = new String[linesReversed.size()];
    for (int i = 0; i < rtn.length; ++i) {
      rtn[i] = linesReversed.get(rtn.length - i - 1);
    }
    raf.close();
    return rtn;
  }

  /** @see edu.stanford.nlp.io.IOUtils#tail(java.io.File, int, String) */
  public static String[] tail(File f, int n) throws IOException { return tail(f, n, "utf-8"); }

  /** Bare minimum sanity checks */
  private static final Set<String> blockListPathsToRemove = new HashSet<String>(){{
    add("/");
    add("/u"); add("/u/");
    add("/u/nlp"); add("/u/nlp/");
    add("/u/nlp/data"); add("/u/nlp/data/");
    add("/scr"); add("/scr/");
    add("/u/scr/nlp/data"); add("/u/scr/nlp/data/");
  }};

  /**
   * Delete this file; or, if it is a directory, delete this directory and all its contents.
   * This is a somewhat dangerous function to call from code, and so a few safety features have been
   * implemented (though you should not rely on these!):
   *
   * <ul>
   *   <li>Certain directories are prohibited from being removed.</li>
   *   <li>More than 100 files cannot be removed with this function.</li>
   *   <li>More than 10GB cannot be removed with this function.</li>
   * </ul>
   *
   * @param file The file or directory to delete.
   */
  public static void deleteRecursively(File file) {
    // Sanity checks
    if (blockListPathsToRemove.contains(file.getPath())) {
      throw new IllegalArgumentException("You're trying to delete " + file + "! I _really_ don't think you want to do that...");
    }
    int count = 0;
    long size = 0;
    for (File f : iterFilesRecursive(file)) {
      count += 1;
      size += f.length();
    }
    if (count > 100) {
      throw new IllegalArgumentException("Deleting more than 100 files; you should do this manually");
    }
    if (size > 10000000000L) {  // 10 GB
      throw new IllegalArgumentException("Deleting more than 10GB; you should do this manually");
    }
    // Do delete
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          deleteRecursively(child);
        }
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }

  /**
   * Start a simple console. Read lines from stdin, and pass each line to the callback.
   * Returns on typing "exit" or "quit".
   *
   * @param callback The function to run for every line of input.
   * @throws IOException Thrown from the underlying input stream.
   */
    public static void console(String prompt, Consumer<String> callback) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    System.out.print(prompt);
    while ( (line = reader.readLine()) != null) {
      switch (line.toLowerCase()) {
        case "":
          break;
        case "exit":
        case "quit":
        case "q":
          return;
        default:
          callback.accept(line);
          break;
      }
      System.out.print(prompt);
    }
  }

  /**
   * Create a prompt, and read a single line of response.
   * @param prompt An optional prompt to show the user.
   * @throws IOException Throw from the underlying reader.
   */
  public static String promptUserInput(Optional<String> prompt) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print(prompt.orElse("> "));
    return reader.readLine();
  }

  /** @see IOUtils#console(String, Consumer) */
  public static void console(Consumer<String> callback) throws IOException {
    console("> ", callback);
  }

  public static String throwableToStackTrace(Throwable t) {
    StringBuilder sb = new StringBuilder();
    sb.append(t).append(eolChar);

    for (StackTraceElement e : t.getStackTrace()) {
        sb.append("\t at ").append(e).append(eolChar);
    }
    return sb.toString();
  }

}
