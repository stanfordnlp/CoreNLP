package edu.stanford.nlp.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.NoSuchElementException;
import java.util.zip.GZIPOutputStream;

/**
 * Provides various filesystem operations common to scripting languages such
 * as Perl and Python but not present (currently) in the Java standard libraries.
 * 
 * @author Spence Green
 *
 */
public final class FileSystem {

  private FileSystem() {}
  
  /**
   * Copies a file. The ordering of the parameters corresponds to the Unix cp command.
   * 
   * @param sourceFile The file to copy.
   * @param destFile The path to copy to which the file should be copied.
   * @throws IOException
   */
  public static void copyFile(File sourceFile, File destFile) throws IOException {
    if(!destFile.exists())
      destFile.createNewFile();

    FileChannel source = null;
    FileChannel destination = null;
    try {
      source = new FileInputStream(sourceFile).getChannel();
      destination = new FileOutputStream(destFile).getChannel();
      destination.transferFrom(source, 0, source.size());
    } catch (Exception e) {
      System.err.printf("FileSystem: Error copying %s to %s\n", sourceFile.getPath(), destFile.getPath());
      e.printStackTrace();
    } finally {
      if(source != null)
        source.close();
      if(destination != null)
        destination.close();
    }
  }

  /**
   * Similar to the unix gzip command, only it does not delete the file after compressing it.
   * 
   * @param uncompressedFileName The file to gzip
   * @param compressedFileName The file name for the compressed file
   * @throws IOException
   */
  public static void gzipFile(File uncompressedFileName, File compressedFileName) throws IOException {
    GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(compressedFileName));
    FileInputStream in = new FileInputStream(uncompressedFileName);

    byte[] buf = new byte[1024];
    int len;
    while ((len = in.read(buf)) > 0) {
      out.write(buf, 0, len);
    }
    in.close();

    out.finish();
    out.close();
  }

  /**
   * Recursively deletes a directory, including all files and sub-directories.
   * 
   * @param dir The directory to delete
   * @return true on success; false, otherwise.
   */
  public static boolean deleteDir(File dir) {
    if (dir.isDirectory()) {
      String[] children = dir.list();
      for (int i = 0; i < children.length; i++) {
        boolean success = deleteDir(new File(dir, children[i]));
        if (!success)
          return false;
      }
    }

    return dir.delete();
  }

  /**
   * Returns whether a file object both exists and has contents (i.e. the size of the file is greater than 0)
   * @param file
   * @return true if the file exists and is non-empty
   */
  public static boolean existsAndNonEmpty(File file) {
    if (!file.exists()) {
      return false;
    }
    
    Iterable<String> lines = IOUtils.readLines(file);
    String firstLine;
    try {
      firstLine = lines.iterator().next();
    } catch (NoSuchElementException nsee) {
      return false;
    }
    
    return firstLine.length() > 0;
  }
  
  /**
   * Unit test code
   */
  public static void main(String[] args) {
    String testDirName = "FileSystemTest";
    String testFileName = "Pair.java";
    
    File testDir = new File(testDirName);
    testDir.mkdir();
    
    try {
      copyFile(new File(testFileName),new File(testDirName + "/" + testFileName));
    } catch (IOException e) {
      System.err.println("Copy failed");
      System.exit(-1);
    }
    
    try {
      Runtime r = Runtime.getRuntime();
      Process p = r.exec(String.format("tar -cf %s.tar %s",testDirName,testDirName));
      
      int ret_val;
      if((ret_val = p.waitFor()) != 0) {
        System.err.printf("tar command returned %d\n",ret_val);
        System.exit(-1);
      }
      
    } catch (IOException e) {
      System.err.println("Tar command failed");
      System.exit(-1);
    } catch(InterruptedException e) {
      System.err.println("Tar command interrupted");
      e.printStackTrace();
      System.exit(-1);
    }
    
    try {
      gzipFile(new File(String.format(testDirName + ".tar")), new File(testDirName + ".tar.gz"));
    } catch (IOException e) {
      System.err.println("gzip command failed");
      System.exit(-1);
    }
    
    boolean deleteSuccess = deleteDir(new File(testDirName));
    if(!deleteSuccess) {
      System.err.println("Could not delete directory");
      System.exit(-1);
    }
    
    System.out.println("Success!");
  }

}
