package edu.stanford.nlp.trees.treebank; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.io.FileSystem;
import edu.stanford.nlp.util.RuntimeInterruptedException;

/**
 * Adds data files to a tar'd / gzip'd distribution package. Data sets marked with the DISTRIB parameter
 * in {@link ConfigParser} are added to the archive.
 *
 * @author Spence Green
 */
public class DistributionPackage  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DistributionPackage.class);

  private final List<String> distFiles;
  private String lastCreatedDistribution = "UNKNOWN";

  public DistributionPackage() {
    distFiles = new ArrayList<>();
  }

  /**
   * Adds a listing of files to the distribution archive
   *
   * @param fileList List of full file paths
   */
  public void addFiles(List<String> fileList) {
    distFiles.addAll(fileList);
  }

  /**
   * Create the distribution and name the file according to the specified parameter.
   *
   * @param distribName The name of distribution
   * @return True if the distribution is built. False otherwise.
   */
  public boolean make(String distribName) {
    boolean createdDir = (new File(distribName)).mkdir();
    if(createdDir) {

      String currentFile = "";
      try {

        for(String filename : distFiles) {
          currentFile = filename;
          File destFile = new File(filename);
          String relativePath = distribName + "/" + destFile.getName();
          destFile = new File(relativePath);
          FileSystem.copyFile(new File(filename),destFile);
        }

        String tarFileName = String.format("%s.tar", distribName);
        Runtime r = Runtime.getRuntime();
        Process p = r.exec(String.format("tar -cf %s %s/", tarFileName, distribName));

        if(p.waitFor() == 0) {

          File tarFile = new File(tarFileName);
          FileSystem.gzipFile(tarFile, new File(tarFileName + ".gz"));
          tarFile.delete();
          FileSystem.deleteDir(new File(distribName));

          lastCreatedDistribution = distribName;

          return true;

        } else {
          System.err.printf("%s: Unable to create tar file %s\n", this.getClass().getName(),tarFileName);
        }
      } catch (IOException e) {
        System.err.printf("%s: Unable to add file %s to distribution %s\n", this.getClass().getName(),currentFile,distribName);
      } catch (InterruptedException e) {
        System.err.printf("%s: tar did not return from building %s.tar\n", this.getClass().getName(),distribName);
        throw new RuntimeInterruptedException(e);
      }
    } else {
      System.err.printf("%s: Unable to create temp directory %s\n", this.getClass().getName(), distribName);
    }

    return false;
  }

  @Override
  public String toString() {
    String header = String.format("Distributable package %s (%d files)\n", lastCreatedDistribution,distFiles.size());
    StringBuilder sb = new StringBuilder(header);
    sb.append("--------------------------------------------------------------------\n");

    for(String filename : distFiles)
      sb.append(String.format("  %s\n", filename));

    return sb.toString();
  }

}
