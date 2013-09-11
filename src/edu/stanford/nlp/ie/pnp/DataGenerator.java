package edu.stanford.nlp.ie.pnp;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Creates training/test/answer files from input files.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #main
 */
public class DataGenerator {
  final int testPercent = 10; // what fraction of the examples to use for testing
  final int numFolds = 10; // number of different train/test divides to make

  /**
   * Constructs a new DataGenerator with the given args.
   *
   * @see #main
   */
  public DataGenerator(String[] args) {
    List[] examples = new List[args.length - 1]; //list of examples for each data source
    String[] categories = new String[args.length - 1]; // names of each category
    String outdir = args[0]; // directory where files will be output

    try {
      // reads in the files
      for (int i = 1; i < args.length; i++) {
        System.err.println(new Date() + " Reading in file: " + args[i]);
        // pulls out all examples from current file and marks their category
        examples[i - 1] = new ArrayList();
        String filename = new File(args[i]).getName();
        categories[i - 1] = filename.substring(0, filename.lastIndexOf('.'));
        BufferedReader br = new BufferedReader(new FileReader(args[i]));
        String line;
        while ((line = br.readLine()) != null) {
          examples[i - 1].add(line);
        }
      }

      // creates the dir name in which to write all the train/test/answer files
      String dir = outdir + File.separator;
      for (int i = 0; i < categories.length; i++) {
        dir += (i > 0 ? "-" : "") + categories[i];
      }
      dir += File.separator;

      // pairwise tests
      for (int i = 0; i < examples.length; i++) {
        for (int j = i + 1; j < examples.length; j++) {
          List<Example> all = new ArrayList<Example>();
          for (int k = 0; k < examples[i].size(); k++) {
            all.add(new Example(1, (String) examples[i].get(k)));
          }
          for (int k = 0; k < examples[j].size(); k++) {
            all.add(new Example(2, (String) examples[j].get(k)));
          }
          writeFolds(all, 2, dir + "pairwise" + File.separator + categories[i] + "-" + categories[j]);
        }
      }

      if (examples.length == 2) {
        System.exit(0); // no need to do 1-all and n-way for 2 categories
      }

      // 1-all tests
      for (int i = 0; i < examples.length; i++) {
        List<Example> all = new ArrayList<Example>();
        for (int j = 0; j < examples[i].size(); j++) {
          all.add(new Example(1, (String) examples[i].get(j)));
        }
        for (int j = 0; j < examples.length; j++) {
          if (j == i) {
            continue; // skips the "1" category
          }
          for (int k = 0; k < examples[j].size(); k++) {
            all.add(new Example(2, (String) examples[j].get(k)));
          }
        }
        String name = categories[i] + "-";
        for (int j = 0; j < categories.length; j++) {
          if (j != i) {
            name += (name.endsWith("-") ? "" : "_") + categories[j];
          }
        }
        writeFolds(all, 2, dir + "1-all" + File.separator + name);

      }

      // n-way test
      List<Example> all = new ArrayList<Example>();
      for (int i = 0; i < examples.length; i++) {
        for (int j = 0; j < examples[i].size(); j++) {
          all.add(new Example(i + 1, (String) examples[i].get(j)));
        }
      }
      String name = "";
      for (int i = 0; i < examples.length; i++) {
        name += (i > 0 ? "-" : "") + categories[i];
      }
      writeFolds(all, examples.length, dir + name);


      System.err.println(new Date() + " Done.");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates and writes out train/test/answer folds for the given set of examples.
   */
  private void writeFolds(List<Example> examples, int numCategories, String outname) throws IOException {
    System.err.println(new Date() + " Creating " + numFolds + " train/test/answer folds for " + outname);
    new File(outname).getParentFile().mkdirs();
    for (int f = 1; f <= numFolds; f++) {

      // shuffles all examples
      Collections.shuffle(examples);

      PrintWriter train = new PrintWriter(new BufferedWriter(new FileWriter(outname + ".train." + f)));
      train.println(numCategories);
      for (int i = 0; i < examples.size() - examples.size() / testPercent; i++) {
        // prints out training file lines as "# example line" where # is category num (starts at 1)
        Example cur = examples.get(i);
        train.println(cur.category + " " + cur.text);
      }
      train.flush();
      train.close();

      // prints out test file lines (unannotated of course) and corresponding answer file with just correct categories
      PrintWriter test = new PrintWriter(new BufferedWriter(new FileWriter(outname + ".test." + f)));
      PrintWriter answers = new PrintWriter(new BufferedWriter(new FileWriter(outname + ".answers." + f)));
      for (int i = examples.size() - examples.size() / testPercent; i < examples.size(); i++) {
        Example cur = examples.get(i);
        test.println(cur.text);
        answers.println(cur.category);
      }
      test.flush();
      test.close();
      answers.flush();
      answers.close();
    }
  }


  /**
   * Generates training, test, and answer files from files, one per category.
   * <p><tt>Usage: java DataGenerator outdir source1 source2 [source3 ...].</tt></p>
   * The first argument is the dir in which the dir of files should be output.
   * The remaining args are names of category files. Each category file is just a
   * list of lines, where each line is an example of the category. The name of the category
   * is the name of the category file minus its file extention (e.g. a file named "drug.txt"
   * will constitute a category named "drug"). This program reads in the category files
   * and produces a bunch of train/test/answer files. Specifically, it makes pairwise tests
   * for each pair of catgeories, it makes "1-all" tests where one category is put up against
   * the union of all the other categories, and one "n-way" test where all categories are put
   * in on their own. It creates several random folds for each situation, with the number of folds
   * and the percentage of data to keep for testing specified as hard-coded constants.
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Usage: java DataGenerator outdir source1 source2 [source3 ...]");
      System.exit(-1);
    }
    new DataGenerator(args);
  }

  /**
   * Stores a category number and an example text.
   */
  public static class Example {
    /**
     * category (label) for this example.
     */
    public int category;
    /**
     * text for this example.
     */
    public String text;

    /**
     * Constructs a new Example with the given category and text.
     */
    public Example(int category, String text) {
      this.category = category;
      this.text = text;
    }
  }
}
