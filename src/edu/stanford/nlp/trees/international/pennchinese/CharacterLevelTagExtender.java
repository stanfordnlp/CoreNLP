package edu.stanford.nlp.trees.international.pennchinese; 

import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.stats.EquivalenceClassEval;
import edu.stanford.nlp.trees.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A transformer to extend tags down to the level of individual characters.
 * Each word preterminal is split into new preterminals for each character
 * with tags corresponding to the original preterminal tag plus a suffix
 * depending on the position of the character in the word: _S for single-char
 * words, _B for first char of multi-char words, _M for middle chars and _E
 * for final chars.
 * <br>
 * This is used in combining Chinese parsing and word segmentation using the
 * method of Luo '03.
 * <br>
 * Note: it implements TreeTransformer because we might want to do away
 * with TreeNormalizers in favor of TreeTransformers
 *
 * @author Galen Andrew (galand@cs.stanford.edu) Date: May 13, 2004
 */
public class CharacterLevelTagExtender extends BobChrisTreeNormalizer implements TreeTransformer  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CharacterLevelTagExtender.class);

  private static final long serialVersionUID = 7893996593626523700L;

  private static final boolean useTwoCharTags = false;

  public CharacterLevelTagExtender() {
    super(new ChineseTreebankLanguagePack());
  }

  public CharacterLevelTagExtender(TreebankLanguagePack tlp) {
    super(tlp);
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    return transformTree(super.normalizeWholeTree(tree,tf));
  }

  //  static Set preterminals = new HashSet();

  public Tree transformTree(Tree tree) {
    TreeFactory tf = tree.treeFactory();
    String tag = tree.label().value();
    if (tree.isPreTerminal()) {
      String word = tree.firstChild().label().value();

      List<Tree> newPreterms = new ArrayList<>();
      for (int i = 0, size = word.length(); i < size; i++) {
        String singleCharLabel = String.valueOf(word.charAt(i));
        Tree newLeaf = tf.newLeaf(singleCharLabel);
        String suffix;
        if (useTwoCharTags) {
          if (word.length() == 1 || i == 0) {
            suffix = "_S";
          } else {
            suffix = "_M";
          }
        } else {
          if (word.length() == 1) {
            suffix = "_S";
          } else if (i == 0) {
            suffix = "_B";
          } else if (i == word.length() - 1) {
            suffix = "_E";
          } else {
            suffix = "_M";
          }
        }
        newPreterms.add(tf.newTreeNode(tag + suffix, Collections.<Tree>singletonList(newLeaf)));
      }
      return tf.newTreeNode(tag, newPreterms);
    } else {
      List<Tree> newChildren = new ArrayList<>();
      for (int i = 0; i < tree.children().length; i++) {
        Tree child = tree.children()[i];
        newChildren.add(transformTree(child));
      }
      return tf.newTreeNode(tag, newChildren);
    }
  }

  public Tree untransformTree(Tree tree) {
    TreeFactory tf = tree.treeFactory();
    if (tree.isPrePreTerminal()) {
      if (tree.firstChild().label().value().matches(".*_.")) {
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < tree.children().length; i++) {
          Tree child = tree.children()[i];
          word.append(child.firstChild().label().value());
        }
        Tree newChild = tf.newLeaf(word.toString());
        tree.setChildren(Collections.singletonList(newChild));
      }
    } else {
      for (int i = 0; i < tree.children().length; i++) {
        Tree child = tree.children()[i];
        untransformTree(child);
      }
    }
    return tree;
  }

  private static void testTransAndUntrans(CharacterLevelTagExtender e, Treebank tb, PrintWriter pw) {
    for (Tree tree : tb) {
      Tree oldTree = tree.treeSkeletonCopy();
      e.transformTree(tree);
      e.untransformTree(tree);
      if (!tree.equals(oldTree)) {
        pw.println("NOT EQUAL AFTER UNTRANSFORMATION!!!");
        pw.println();
        oldTree.pennPrint(pw);
        pw.println();
        tree.pennPrint(pw);
        pw.println("------------------");
      }
    }
  }

  /**
   * for testing -- CURRENTLY BROKEN!!!
   *
   * @param args input dir and output filename
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new RuntimeException("args: treebankPath trainNums testNums");
    }

    ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
    ctpp.charTags = true;
    // TODO: these options are getting clobbered by reading in the
    // parser object (unless it's a text file parser?)
    Options op = new Options(ctpp);
    op.doDep = false;
    op.testOptions.maxLength = 90;

    LexicalizedParser lp;
    try {
      FileFilter trainFilt = new NumberRangesFileFilter(args[1], false);

      lp = LexicalizedParser.trainFromTreebank(args[0], trainFilt, op);
      try {
        String filename = "chineseCharTagPCFG.ser.gz";
        log.info("Writing parser in serialized format to file " + filename + " ");
        System.err.flush();
        ObjectOutputStream out = IOUtils.writeStreamFromString(filename);

        out.writeObject(lp);
        out.close();
        log.info("done.");
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    } catch (IllegalArgumentException e) {
      lp = LexicalizedParser.loadModel(args[1], op);
    }

    FileFilter testFilt = new NumberRangesFileFilter(args[2], false);
    MemoryTreebank testTreebank = ctpp.memoryTreebank();
    testTreebank.loadPath(new File(args[0]), testFilt);
    PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream("out.chi"), "GB18030"), true);
    WordCatEquivalenceClasser eqclass = new WordCatEquivalenceClasser();
    WordCatEqualityChecker eqcheck = new WordCatEqualityChecker();
    EquivalenceClassEval eval = new EquivalenceClassEval(eqclass, eqcheck);
    //    System.out.println("Preterminals:" + preterminals);
    System.out.println("Testing...");
    for (Tree gold : testTreebank) {
      Tree tree;
      try {
        tree = lp.parseTree(gold.yieldHasWord());
        if (tree == null) {
          System.out.println("Failed to parse " + gold.yieldHasWord());
          continue;
        }
      } catch (Exception e) {
        e.printStackTrace();
        continue;
      }
      gold = gold.firstChild();
      pw.println(SentenceUtils.listToString(gold.preTerminalYield()));
      pw.println(SentenceUtils.listToString(gold.yield()));
      gold.pennPrint(pw);

      pw.println(tree.preTerminalYield());
      pw.println(tree.yield());
      tree.pennPrint(pw);
      //      Collection allBrackets = WordCatConstituent.allBrackets(tree);
      //      Collection goldBrackets = WordCatConstituent.allBrackets(gold);
      //      eval.eval(allBrackets, goldBrackets);
      eval.displayLast();
    }
    System.out.println();
    System.out.println();
    eval.display();
  }

}
