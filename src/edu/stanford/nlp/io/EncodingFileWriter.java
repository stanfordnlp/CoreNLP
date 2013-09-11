package edu.stanford.nlp.io;

import java.io.*;


/**
 * This is a convenience class which works almost exactly like <code>FileWriter</code>
 * but allows for the specification of input encoding.  Unlike FileInputStream,
 * giving no encoding ends up giving you utf-8.
 *
 * @author	Alex Kleeman
 */

public class EncodingFileWriter extends OutputStreamWriter {

  /**
   * Constructs a EncodingFileWriter object given a file name and encoding.
   *
   * @param fileName  String The system-dependent filename.
   * @param encoding <tt>String</tt> specifying the encoding to be used
   * @throws java.io.IOException  if the named file exists but is a directory rather
   *                  than a regular file, does not exist but cannot be
   *                  created, or cannot be opened for any other reason
   */
  public EncodingFileWriter(String fileName, String encoding) throws  IOException {
    super(new FileOutputStream(fileName), encoding);
  }

  /**
   * Constructs a utf-8 EncodingFileWriter object given a file name.
   *
   * @param fileName  String The system-dependent filename.
   * @throws java.io.IOException  if the named file exists but is a directory rather
   *                  than a regular file, does not exist but cannot be
   *                  created, or cannot be opened for any other reason
   */
  public EncodingFileWriter(String fileName) throws IOException {
    super(new FileOutputStream(fileName),"utf-8");
  }


  /**
   * Constructs a EncodingFileWriter object given a file name with a boolean
   * indicating whether or not to append the data written and a <tt>String</tt>
   * indicating which encoding to use.
   *
   * @param fileName  String The system-dependent filename.
   * @param append    boolean if <code>true</code>, then data will be written
   *                  to the end of the file rather than the beginning.
   * @param encoding <tt>String</tt> specifying the encoding to be used
   * @throws IOException  if the named file exists but is a directory rather
   *                  than a regular file, does not exist but cannot be
   *                  created, or cannot be opened for any other reason
   */
  public EncodingFileWriter(String fileName, boolean append,String encoding) throws IOException {
    super(new FileOutputStream(fileName, append),encoding);
  }

    /**
   * Constructs a utf-8 EncodingFileWriter object given a file name with a boolean
   * indicating whether or not to append the data written.
   *
   * @param fileName  String The system-dependent filename.
   * @param append    boolean if <code>true</code>, then data will be written
   *                  to the end of the file rather than the beginning.
   * @throws java.io.UnsupportedEncodingException  if the encoding does not exist.
   * @throws IOException  if the named file exists but is a directory rather
   *                  than a regular file, does not exist but cannot be
   *                  created, or cannot be opened for any other reason
   */
  public EncodingFileWriter(String fileName, boolean append) throws IOException {
    super(new FileOutputStream(fileName, append),"utf-8");
  }

  /**
   * Constructs a EncodingFileWriter object given a File object.
   *
   * @param file  a File object to write to.
   * @param encoding <tt>String</tt> specifying the encoding to be used
   * @throws IOException  if the file exists but is a directory rather than
   *                  a regular file, does not exist but cannot be created,
   *                  or cannot be opened for any other reason
   */
  public EncodingFileWriter(File file, String encoding) throws IOException {
    super(new FileOutputStream(file),encoding);
  }

    /**
   * Constructs a utf-8 EncodingFileWriter object given a File object.
   *
   * @param file  a File object to write to.
   * @throws IOException  if the file exists but is a directory rather than
   *                  a regular file, does not exist but cannot be created,
   *                  or cannot be opened for any other reason
   */
  public EncodingFileWriter(File file) throws IOException {
    super(new FileOutputStream(file),"utf-8");
  }

  /**
   * Constructs a EncodingFileWriter object given a File object. If the second
   * argument is <code>true</code>, then bytes will be written to the end
   * of the file rather than the beginning.
   *
   * @param file  a File object to write to
   * @param     append    if <code>true</code>, then bytes will be written
   *                      to the end of the file rather than the beginning
   * @param encoding <tt>String</tt> specifying the encoding to be used
   * @throws IOException  if the file exists but is a directory rather than
   *                  a regular file, does not exist but cannot be created,
   *                  or cannot be opened for any other reason
   * @since 1.4
   */
  public EncodingFileWriter(File file, boolean append,String encoding) throws IOException {
    super(new FileOutputStream(file, append),encoding);
  }

    /**
   * Constructs a utf-8 EncodingFileWriter object given a File object. If the second
   * argument is <code>true</code>, then bytes will be written to the end
   * of the file rather than the beginning. Defaults to utf-8.
   *
   * @param file  a File object to write to
   * @param     append    if <code>true</code>, then bytes will be written
   *                      to the end of the file rather than the beginning
   * @throws IOException  if the file exists but is a directory rather than
   *                  a regular file, does not exist but cannot be created,
   *                  or cannot be opened for any other reason
   * @since 1.4
   */
  public EncodingFileWriter(File file, boolean append) throws IOException {
    super(new FileOutputStream(file, append),"utf-8");
  }

  /**
   * Constructs a EncodingFileWriter object associated with a file descriptor.
   *
   * @param fd  FileDescriptor object to write to.
   */
  public EncodingFileWriter(FileDescriptor fd) {
    super(new FileOutputStream(fd));
  }


  /** This static method will treat null as meaning to use the platform default,
   *  unlike the Java library methods that disallow a null encoding.
   *
   *  @param filename File path to open
   *  @param encoding Encoding
   *  @return A Writer
   *  @throws IOException If any IO problem
   */
  public static Writer getWriter(String filename, String encoding) throws IOException {
    return (encoding == null) ? new FileWriter(filename) : new EncodingFileWriter(filename, encoding);
  }

  public static Writer getWriter(File file, String encoding) throws IOException {
    return (encoding == null) ? new FileWriter(file) : new EncodingFileWriter(file, encoding);
  }


}

