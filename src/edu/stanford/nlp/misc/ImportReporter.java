package edu.stanford.nlp.misc;

import edu.stanford.nlp.fsm.TransducerGraph;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.StringUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Reads a Java source tree and gives a report of the import statements in
 * each package as a rough indication of the dependencies within the source
 * code.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 * @author Christopher Manning
 */
public class ImportReporter {
  private HashMap<String,String> packageUser = new HashMap<String,String>();
  private TransducerGraph depGraph;

  private ClassicCounter<String> processFile(File file, int depth) {
    ClassicCounter<String> packageNames = new ClassicCounter<String>();
    if (file.isDirectory() && file.toString().indexOf("CVS") == -1) {
      File[] files = file.listFiles();
      for (int i = 0; i < files.length; i++) {
        Counters.addInPlace(packageNames, processFile(files[i], depth + 1));
      }

      if (true /* depth==1 && packageNames.size()>0 */) {
        //        for (int j = 0; j < depth; j++) System.out.print('\t');
        String sourcePackage = convertToPackageName(file.getPath());
        System.out.println(sourcePackage);
        print(sourcePackage, packageNames);
        System.out.println();
        return new ClassicCounter<String>();  // don't inherit
      }
      return (packageNames);
    } else {
      return (parseFile(file));
    }
  }

  private static String convertToPackageName(String filename) {
    StringBuilder buff = new StringBuilder();
    for (int i = 0; i < filename.length(); i++) {
      char c = filename.charAt(i);
      if (c == '/' || c == '\\') {
        c = '.';
      }
      buff.append(c);
    }
    int index;
    if ((index = buff.indexOf("edu")) != -1) {
      return buff.substring(index);
    } else {
      return "";
    }
  }

  /**
   * Parses the given file, extracting imported package names
   *
   * @param file the file to parse
   * @return the set of package names imported within the file
   */
  private ClassicCounter<String> parseFile(File file) {
    ClassicCounter<String> packageNames = new ClassicCounter<String>();
    if (!file.getName().endsWith("java")) {
      return (packageNames);
    }
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        int index;
        if (line.startsWith("import") && (index = line.indexOf(';')) != -1) {
          String packageName = line.substring("import".length(), index).trim();
          index = packageName.lastIndexOf('.');
          if (index != -1) {
            String usedFile = packageName.substring(index + 1);
            packageName = filter(packageName.substring(0, index));
            if (packageName != null) {
              packageNames.incrementCount(packageName);
              String filename = file.getName();
              filename = filename.substring(0, filename.length() - 5);
              filename = filename + " uses " + usedFile;
              packageUser.put(packageName, filename);
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return (packageNames);
  }

  /**
   * Select only edu.stanford.nlp packages.
   */
  private String filter(String packageName) {
    if (!packageName.startsWith("edu.stanford.nlp")) {
      return (null);
    } else {
      /*int start = "edu.stanford.nlp.".length();
       int end=packageName.indexOf('.',start);
   if(end==-1) */
      return (packageName);
      /* else return packageName.substring(start,end); */
    }
  }

  void print(String sourcePackage, ClassicCounter<String> packageNames) {
    ArrayList<String> sorted = new ArrayList<String>(packageNames.keySet());
    Collections.sort(sorted);
    for (String packageName : sorted) {
      System.out.print('\t');
      int count = (int) packageNames.getCount(packageName);
      System.out.print(packageName + ": " + count + " ");
      StringBuilder buff = new StringBuilder();
      buff.append("[" + packageUser.get(packageName));
      if ((int) packageNames.getCount(packageName) > 1) {
        buff.append(", ...");
      }
      buff.append("]");
      System.out.println(buff.toString());
      if (depGraph != null) {
        depGraph.addArc(sourcePackage, packageName, Integer.valueOf(count), buff.toString());
      }
    }
  }

  public ImportReporter(TransducerGraph depGraph) {
    this.depGraph = depGraph;
  }

  /**
   * Print out package dependencies.
   * Usage: ImportReporter fileRoot [outputDotFile]
   * Pass in the root of the repository edu as the first argument.
   * (Indeed, this may be the only way it works correctly....)
   */
  public static void main(String[] args) {
    TransducerGraph depGraph = null;
    String outFile = null;
    if (args.length > 1) {
      depGraph = new TransducerGraph();
      outFile = args[1];
    }
    ImportReporter reporter = new ImportReporter(depGraph);
    reporter.processFile(new File(args[0]), 0);
    if (outFile != null && depGraph != null) {
      StringUtils.printToFile(outFile, depGraph.asDOTString(), false);
    }
  }

}
