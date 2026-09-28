package edu.stanford.nlp.io;

import java.io.*;

/**
 * This is a convenience class which works almost exactly like
 * {@link java.io.FileReader}
 * but allows for the specification of input encoding.
 * Unless another encoding is given, files are read as UTF-8.
 *
 * @author	Alex Kleeman
 */

public class EncodingFileReader extends InputStreamReader {

  /** Encoding used when none is specified. */
  private static final String DEFAULT_ENCODING = "UTF-8";
  /**
   * Creates a new {@code EncodingFileReader}, given the name of the
   * file to read from, using UTF-8.
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
   * Creates a new {@code EncodingFileReader}, given the name of the
   * file to read from and an encoding.
   *
   * @param fileName the name of the file to read from
   * @param encoding the name of the encoding to use; if null, UTF-8 is used
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
   * Creates a new {@code EncodingFileReader}, given the {@link File}
   * to read from, using UTF-8.
   *
   * @param file the {@code File} to read from
   * @exception  FileNotFoundException  if the file does not exist,
   *                   is a directory rather than a regular file,
   *                   or for some other reason cannot be opened for
   *                   reading.
   * @exception java.io.UnsupportedEncodingException  if the encoding does not exist.
   */
  public EncodingFileReader(File file) throws  UnsupportedEncodingException, FileNotFoundException {
    super(new FileInputStream(file), DEFAULT_ENCODING);
  }

  /**
   * Creates a new {@code EncodingFileReader}, given the {@link File}
   * to read from and an encoding.
   *
   * @param file the {@code File} to read from
   * @param encoding the name of the encoding to use; if null, UTF-8 is used
   * @exception  FileNotFoundException  if the file does not exist,
   *                   is a directory rather than a regular file,
   *                   or for some other reason cannot be opened for
   *                   reading.
   * @exception java.io.UnsupportedEncodingException  if the encoding does not exist.
   */
  public EncodingFileReader(File file,String encoding) throws  UnsupportedEncodingException, FileNotFoundException {
    super(new FileInputStream(file),
          encoding == null ? DEFAULT_ENCODING: encoding);
  }

  /**
   * Creates a new {@code EncodingFileReader}, given the
   * {@link FileDescriptor} to read from.
   * Unlike the other constructors, this one reads using the
   * platform's default charset, not UTF-8.
   *
   * @param fd the {@code FileDescriptor} to read from
   */
  public EncodingFileReader(FileDescriptor fd) {
    super(new FileInputStream(fd));
  }

}
