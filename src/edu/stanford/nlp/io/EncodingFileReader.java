package edu.stanford.nlp.io;

import java.io.*;

/**
 * This is a convenience class which works almost exactly like 
 * <code>FileReader</code>
 * but allows for the specification of input encoding.
 *
 * @author	Alex Kleeman
 */

public class EncodingFileReader extends InputStreamReader {

  private static final String DEFAULT_ENCODING = "UTF-8";
  /**
   * Creates a new <tt>EncodingFileReader</tt>, given the name of the
   * file to read from.
   *
   * @param fileName the name of the file to read from
   * @exception java.io.FileNotFoundException  if the named file does not 
   *                   exist, is a directory rather than a regular file,
   *                   or for some other reason cannot be opened for
   *                   reading.
   * @exception java.io.UnsupportedEncodingException  if the encoding does not exist.
   *
   */
  public EncodingFileReader(String fileName) throws UnsupportedEncodingException, FileNotFoundException {
    super(new FileInputStream(fileName), DEFAULT_ENCODING);
  }

  /**
   * Creates a new <tt>EncodingFileReader</tt>, given the name of the
   * file to read from and an encoding
   *
   * @param fileName the name of the file to read from
   * @param encoding <tt>String</tt> specifying the encoding to be used
   * @exception java.io.UnsupportedEncodingException  if the encoding does not exist.
   * @exception java.io.FileNotFoundException  if the named file does not exist,
   *                   is a directory rather than a regular file,
   *                   or for some other reason cannot be opened for
   *                   reading.
   *
   */
  public EncodingFileReader(String fileName, String encoding) throws UnsupportedEncodingException, FileNotFoundException {
    super(new FileInputStream(fileName), 
          encoding == null ? DEFAULT_ENCODING: encoding);
  }

  /**
   * Creates a new <tt>EncodingFileReader</tt>, given the <tt>File</tt>
   * to read from, and using default of utf-8.
   *
   * @param file the <tt>File</tt> to read from
   * @exception  FileNotFoundException  if the file does not exist,
   *                   is a directory rather than a regular file,
   *                   or for some other reason cannot be opened for
   *                   reading.
   *  @exception java.io.UnsupportedEncodingException  if the encoding does not exist.
   */
  public EncodingFileReader(File file) throws  UnsupportedEncodingException, FileNotFoundException {
    super(new FileInputStream(file), DEFAULT_ENCODING);
  }

  /**
   * Creates a new <tt>FileReader</tt>, given the <tt>File</tt>
   * to read from and encoding.
   *
   * @param file the <tt>File</tt> to read from
   * @param encoding <tt>String</tt> specifying the encoding to be used
   * @exception  FileNotFoundException  if the file does not exist,
   *                   is a directory rather than a regular file,
   *                   or for some other reason cannot be opened for
   *                   reading.
   *  @exception java.io.UnsupportedEncodingException  if the encoding does not exist.
   */
  public EncodingFileReader(File file,String encoding) throws  UnsupportedEncodingException, FileNotFoundException {
    super(new FileInputStream(file), 
          encoding == null ? DEFAULT_ENCODING: encoding);
  }

  /**
   * Creates a new <tt>FileReader</tt>, given the
   * <tt>FileDescriptor</tt> to read from.
   *
   * @param fd the FileDescriptor to read from
   */
  public EncodingFileReader(FileDescriptor fd) {
    super(new FileInputStream(fd));
  }

}
