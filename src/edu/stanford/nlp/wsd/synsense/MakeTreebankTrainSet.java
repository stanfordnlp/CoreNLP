package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.StringLabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;

import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * A utility class which can make a data set for a particular verb
 * from Penn Treebank style files.
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class MakeTreebankTrainSet {


  /**
   * Returns true if the tree contains any of the verb forms verbForms.
   */
  private static boolean containsVerb(Tree tree, String[] verbForms) {

    String tempWord, tempTag;

    boolean containsVerb = false;
    boolean containsQuote = false;

    for (Iterator<Tree> nodeI = tree.iterator(); nodeI.hasNext();) {
      Tree node =  nodeI.next();
      if (node.isPreTerminal()) {
        Tree kid = node.children()[0];
        tempTag = node.label().toString();
        tempWord = kid.label().toString();
        if (tempTag.indexOf("VB") >= 0) {
          for (int i = 0; i < verbForms.length; i++) {
            //	    System.out.println(tempWord + " " + verbForms[i]);
            if (tempWord.equals(verbForms[i])) {
              if (!containsVerb) {
                kid.label().setValue(tempWord + "^");
                containsVerb = true;
              }
            }
          }
        }
        if (tempWord.indexOf("\'\'") >= 0 || tempWord.indexOf("``") >= 0) {
          containsQuote = true;
        }
      }
    }


    /*
    List tagList = tree.taggedYield();

    while (wordIter.hasNext()) {
      tw = (TaggedWord) wordIter.next();
      tempWord = tw.word();
      tempTag = tw.tag();
      if (tempTag.indexOf("VB") >= 0) {
	for (int i=0; i<verbForms.length; i++) {
	  if (tempWord.equals(verbForms[i])) {
	    containsVerb = true;
	  }
	}
      }
      if (tempWord.indexOf("\'\'") >= 0 || tempWord.indexOf("``") >= 0)
	containsQuote = true;
    }
    */

    return (containsVerb && !containsQuote);
  }


  /**
   * The main function.
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage:\n" + "\n" + "MakeTreebankTrainSet [treebankPath] [low] [high] [form0|form1|form2|form3|form4] [maxSentenceSize] [maxNumSentences]");
      System.exit(1);
    }

    String treebankPath = args[0];
    int testlow = Integer.parseInt(args[1]);
    int testhigh = Integer.parseInt(args[2]);
    String verbFormString = args[3];
    int maxSentenceSize = Integer.parseInt(args[4]);
    int maxNumSentences = Integer.parseInt(args[5]);


    DiskTreebank treebank = new DiskTreebank(new StringLabeledScoredTreeReaderFactory());
    treebank.loadPath(treebankPath, new NumberRangeFileFilter(testlow, testhigh, true));


    String[] verbForms = new String[5];

    StringTokenizer st = new StringTokenizer(verbFormString, "|");
    int i = 0;
    while (st.hasMoreTokens() && i < 5) {
      verbForms[i++] = st.nextToken();
    }

    int numPrinted = 0;

    Iterator<Tree> iter = treebank.iterator();
    while (iter.hasNext()) {
      Tree tree = iter.next();
      if (tree.children() == null || tree.children().length == 0 || tree.children()[0] == null) {
        continue;
      }
      if (containsVerb(tree, verbForms) && tree.yield().size() < maxSentenceSize) {
        tree.pennPrint();
        System.out.println();
        numPrinted++;
        if (numPrinted >= maxNumSentences) {
          System.err.println("Got " + numPrinted + " trees");
          return;
        }
      }
    }
    System.err.println("Got " + numPrinted + " trees");
  }
}
