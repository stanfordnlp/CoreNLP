package edu.stanford.nlp.ie.pnp;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Generates a shell script file to run batch tests for PnpClassifier.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #main
 */
public class TestScriptGenerator {
  /**
   * Number of train/test folds to run: {@value}.
   */
  public static final int numFolds = 10;
  /**
   * Comment prefix: {@value}.
   */
  public static final String comment = "#";

  /**
   * Constructs a new TestScriptGenerator to write a shell script to the given outdir
   * to test the given testdir.
   */
  public TestScriptGenerator(String testdir, String outdir) {
    try {
      // parses the category names from testdir (e.g. "data\drug_nyse_place_person")
      String allCategories = new File(testdir).getName();
      List categories = new ArrayList();
      StringTokenizer st = new StringTokenizer(allCategories, "-");
      while (st.hasMoreTokens()) {
        categories.add(st.nextToken());
      }
      String[] cats = new String[categories.size()];
      cats = (String[]) categories.toArray(cats);

      PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(new File(outdir, allCategories + ".csh"))));
      System.err.println("Writing test script: " + new File(outdir, allCategories + ".csh"));

      // header
      out.println("#!/bin/csh");
      out.println(comment + " Test script for " + testdir);
      out.println(comment + " Auto-generated at " + new Date());
      out.println();

      // pairwise
      out.println("echo Pairwise tests:");
      out.println();
      for (int i = 0; i < cats.length; i++) {
        for (int j = i + 1; j < cats.length; j++) {
          printTests(out, testdir + File.separator + "pairwise" + File.separator + cats[i] + "-" + cats[j]);
        }
      }

      // 1-all
      out.println("echo 1-all tests:");
      out.println();
      for (int i = 0; i < cats.length; i++) {
        String name = testdir + File.separator + "1-all" + File.separator + cats[i] + "-";
        for (int j = 0; j < cats.length; j++) {
          if (i != j) {
            name += (name.endsWith("-") ? "" : "_") + cats[j];
          }
        }
        printTests(out, name);
      }

      // n-way
      out.println("echo n-way test:");
      out.println();
      String name = testdir + File.separator;
      for (int i = 0; i < cats.length; i++) {
        name += (i > 0 ? "-" : "") + cats[i];
      }
      printTests(out, name);

      out.flush();
      out.close();
      System.err.println("Done");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Writes a java command with an echo to annotate it.
   */
  private void printTests(PrintWriter out, String file) throws IOException {
    for (int i = 1; i <= numFolds; i++) {
      out.println("echo " + file + " " + i);
      out.println("java edu.stanford.nlp.ie.pnp.PnpClassifier " + file + ".train." + i + " " + file + ".test." + i + " | ~/javanlp/bin/pnp-eval-score.pl " + file + ".answers." + i);
      out.println();
    }
  }

  /**
   * Generates a shell script to test PnpClassifier with a given data set.
   * <p><tt>Usage: java TestScriptGenerator testdir outdir</tt></p>
   * <tt>testdir</tt> is the dir of train/test/answer files made by DataGenerator.
   * <tt>outdir</tt> is where the shell script goes. The shell script will be
   * named based on the testdir. The shell script will run 10 train/test folds.
   * Parses the category names from testdir (e.g. "data\drug_nyse_place_person").
   */
  public static void main(String args[]) {
    if (args.length < 2) {
      System.err.println("Usage: java TestScriptGenerator testdir outdir");
      System.exit(-1);
    }
    new TestScriptGenerator(args[0], args[1]);
  }

}
