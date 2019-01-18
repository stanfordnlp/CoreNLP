package edu.stanford.nlp.io; 

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.NoSuchElementException;
import java.util.zip.GZIPOutputStream;

import edu.stanford.nlp.util.logging.Redwood;

/**
 * Provides various filesystem operations common to scripting languages such
 * as Perl and Python but not present (currently) in the Java standard libraries.
 * 
 * @author Spence Green
 */
public final class FileSystem  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(FileSystem.class);

  private FileSystem() {}
  
  /**
   * Copies a file. The ordering of the parameters corresponds to the Unix cp command.
   * 
   * @param sourceFile The file to copy.
   * @param destFile The path to copy to which the file should be copied.
   * @throws RuntimeIOException If any IO problem
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void copyFile(File sourceFile, File destFile) {
    try {
      if (!destFile.exists()) {
        destFile.createNewFile();
      }
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe);
    }

    try (FileChannel source = new FileInputStream(sourceFile).getChannel();
         FileChannel destination = new FileOutputStream(destFile).getChannel()) {
      destination.transferFrom(source, 0, source.size());
    } catch (IOException e) {
      throw new RuntimeIOException(String.format("FileSystem: Error copying %s to %s%n",
              sourceFile.getPath(), destFile.getPath()), e);
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
    try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(compressedFileName));
         FileInputStream in = new FileInputStream(uncompressedFileName)) {
      byte[] buf = new byte[1024];
      for (int len; (len = in.read(buf)) > 0; ) {
        out.write(buf, 0, len);
      }
    }
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
      if (children == null) {
        return false;
      }
      for (String aChildren : children) {
        boolean success = deleteDir(new File(dir, aChildren));
        if (!success) {
          return false;
        }
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
   * Make the given directory or throw a RuntimeException
   */
  public static void mkdirOrFail(String dir) {
    mkdirOrFail(new File(dir));
  }

  /**
   * Make the given directory or throw a RuntimeException
   */
  public static void mkdirOrFail(File dir) {
    if (!dir.mkdirs()) {
      String error = "Could not create " + dir;
      log.info(error);
      throw new RuntimeException(error);
    }
  }

  public static void checkExistsOrFail(File file) {
    if (!file.exists()) {
      String error = "Output path " + file + " does not exist";
      log.info(error);
      throw new RuntimeException(error);
    }
  }

  public static void checkNotExistsOrFail(File file) {
    if (file.exists()) {
      String error = "Output path " + file + " already exists";
      log.info(error);
      throw new RuntimeException(error);
    }
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
    } catch (RuntimeIOException e) {
      log.info("Copy failed");
      System.exit(-1);
    }
    
    try {
      Runtime r = Runtime.getRuntime();
      Process p = r.exec(String.format("tar -cf %s.tar %s",testDirName,testDirName));
      
      int ret_val;
      if ((ret_val = p.waitFor()) != 0) {
        System.err.printf("tar command returned %d%n",ret_val);
        System.exit(-1);
      }
      
    } catch (IOException e) {
      log.info("Tar command failed");
      System.exit(-1);
    } catch(InterruptedException e) {
      log.info("Tar command interrupted");
      e.printStackTrace();
      System.exit(-1);
    }
    
    try {
      gzipFile(new File(testDirName + ".tar"), new File(testDirName + ".tar.gz"));
    } catch (IOException e) {
      log.info("gzip command failed");
      System.exit(-1);
    }
    
    boolean deleteSuccess = deleteDir(new File(testDirName));
    if(!deleteSuccess) {
      log.info("Could not delete directory");
      System.exit(-1);
    }
    
    System.out.println("Success!");
  }

}
