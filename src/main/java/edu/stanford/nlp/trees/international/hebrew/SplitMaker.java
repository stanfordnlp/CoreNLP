package edu.stanford.nlp.trees.international.hebrew; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;

/**
 * Makes Tsarfaty's canonical split of HTBv2 (see her PhD thesis). This is also
 * the split that appears in Yoav Goldberg's work.
 * 
 * @author Spence Green
 *
 */
public class SplitMaker  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SplitMaker.class);

  /**
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 1) {
      System.err.printf("Usage: java %s tree_file%n", SplitMaker.class.getName());
      System.exit(-1);
    }

    TreebankLanguagePack tlp = new HebrewTreebankLanguagePack();
    String inputFile = args[0];
    File treeFile = new File(inputFile);
    try {
      TreeReaderFactory trf = new HebrewTreeReaderFactory();
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), tlp.getEncoding()));
      TreeReader tr = trf.newTreeReader(br);

      PrintWriter pwDev = new PrintWriter(new PrintStream(new FileOutputStream(inputFile + ".clean.dev"),false,tlp.getEncoding()));
      PrintWriter pwTrain = new PrintWriter(new PrintStream(new FileOutputStream(inputFile + ".clean.train"),false,tlp.getEncoding()));
      PrintWriter pwTest = new PrintWriter(new PrintStream(new FileOutputStream(inputFile + ".clean.test"),false,tlp.getEncoding()));

      int numTrees = 0;
      for(Tree t; ((t = tr.readTree()) != null); numTrees++) {
        if(numTrees < 483)
          pwDev.println(t.toString());
        else if(numTrees >= 483 && numTrees < 5724)
          pwTrain.println(t.toString());
        else
          pwTest.println(t.toString());
      }

      tr.close();
      pwDev.close();
      pwTrain.close();
      pwTest.close();

      System.err.printf("Processed %d trees.%n",numTrees);

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
