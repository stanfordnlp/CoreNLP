package edu.stanford.nlp.io;

import java.io.*;

/**
 * Static class for copying Files, as byte streams or as character streams,
 * where the encoding can be changed.
 * (Oddly, this is not supported by java.io.File.)
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Christopher Manning
 */
public class FileCopier {

  /**
   * Private constructor to prevent direct instantiation.
   */
  private FileCopier() {
  }

  /**
   * Copies the contents of the given input file to the given output file.
   * Does nothing if inFile and outFile are the same file.
   *
   * @throws FileNotFoundException if the input file can't be found.
   * @throws IOException      if an I/O exception occurs while reading/writing.
   */
  public static void copyFile(File inFile, File outFile) throws FileNotFoundException, IOException {
    if (inFile.equals(outFile)) {
      return; // bail out if they're equal
    }

    FileInputStream fis = new FileInputStream(inFile);
    FileOutputStream fos = new FileOutputStream(outFile);
    byte[] buf = new byte[1024];
    int i = 0;
    while ((i = fis.read(buf)) != -1) {
      fos.write(buf, 0, i);
    }
    fis.close();
    fos.close();
  }

  /**
   * Copies the contents of the given text file to the given output file.
   * Does nothing if inFile and outFile are the same file.
   *
   * @throws FileNotFoundException if the input file can't be found.
   * @throws IOException      if an I/O exception occurs while reading/writing.
   */
  public static void convertEncoding(File inFile, String inEncoding, 
                                         File outFile, String outEncoding) throws FileNotFoundException, IOException {
    if (inFile.equals(outFile)) {
      return; // bail out if they're equal
    }

    FileInputStream fis = new FileInputStream(inFile);
    FileOutputStream fos = new FileOutputStream(outFile);
    Reader r = new BufferedReader(new InputStreamReader(fis, inEncoding));
    Writer w = new BufferedWriter(new OutputStreamWriter(fos, outEncoding));
    int i;
    while ((i = r.read()) >= 0) {
      w.write(i);
    }
    r.close();
    w.close();
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      System.err.println("FileCopier inFile inEncoding outFile outEncoding");
    } else {
      convertEncoding(new File(args[0]), args[1], new File(args[2]), args[3]);
    }
  }

}
