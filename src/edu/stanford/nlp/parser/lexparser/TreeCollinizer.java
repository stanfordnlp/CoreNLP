package edu.stanford.nlp.parser.lexparser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.trees.*;

/**
 * Does detransformations to a parsed sentence to map it back to the
 * standard treebank form for output or evaluation.
 * This version has Penn-Treebank-English-specific details, but can probably
 * be used without harm on other treebanks.
 * Returns labels to their basic category, removes punctuation (should be with
 * respect to a gold tree, but currently isn't), deletes the boundary symbol,
 * changes PRT labels to ADVP.
 *
 * @author Dan Klein
 * @author Christopher Manning
 */
public class TreeCollinizer implements AbstractCollinizer {

  private final TreebankLanguagePack tlp;
  private final boolean deletePunct;
  private final boolean fixCollinsBaseNP;
  /** whOption: 0 = do nothing, 1 = also collapse WH phrasal categories in gold tree,
      2 = also collapse WH tags in gold tree,
      4 = attempt to restore WH categories in parse trees (not yet implemented) */
  private final int whOption;

  public TreeCollinizer(TreebankLanguagePack tlp) {
    this(tlp, true, false);
  }

  public TreeCollinizer(TreebankLanguagePack tlp, boolean deletePunct,
                        boolean fixCollinsBaseNP) {
    this(tlp, deletePunct, fixCollinsBaseNP, 0);
  }

  public TreeCollinizer(TreebankLanguagePack tlp, boolean deletePunct,
                        boolean fixCollinsBaseNP, int whOption) {
    this.tlp = tlp;
    this.deletePunct = deletePunct;
    this.fixCollinsBaseNP = fixCollinsBaseNP;
    this.whOption = whOption;
  }

  public String toString() {
    return ("TreeCollinizer(tlp: " + tlp.getClass() + ", deletePunct: " + deletePunct +
            ", fixCollinsBaseNP: " + fixCollinsBaseNP + ", whOption: " + whOption + ")");
  }

  public Tree transformTree(Tree guess, Tree gold) {
    if (guess == null || gold == null) return null;
    if (guess.yield().size() != gold.yield().size()) {
      return null;
    }

    return transformTree(guess, Trees.preTerminals(gold).iterator());
  }

  private String simplifyCategory(String s) {
    s = tlp.basicCategory(s);
    if (((whOption & 1) != 0) && s.startsWith("WH")) {
      s = s.substring(2);
    }
    if ((whOption & 2) != 0) {
      s = s.replaceAll("^WP", "PRP"); // does both WP and WP$ !!
      s = s.replaceAll("^WDT", "DT");
      s = s.replaceAll("^WRB", "RB");
    }
    if (((whOption & 4) != 0) && s.startsWith("WH")) {
      s = s.substring(2);
    }
    return s;
  }

  private Tree transformTree(Tree guess, Iterator<Tree> goldPreterminals) {
    if (guess == null) return null;
    TreeFactory tf = guess.treeFactory();

    String s = guess.value();
    if (tlp.isStartSymbol(s))
      return transformTree(guess.firstChild(), goldPreterminals);

    if (guess.isLeaf()) {
      return tf.newLeaf(guess.label());
    }
    s = simplifyCategory(s);

    // Using the gold tag (and gold word, just in case things are
    // really weird) avoids a problem where the tagger might have used
    // a punct tag when the gold tag is not punct, or vice versa.
    // Otherwise, the transformed trees will be of different length,
    // which makes scoring difficult if not impossible
    if (deletePunct && guess.isPreTerminal()) {
      Tree goldPT = goldPreterminals.next();
      String goldCategory = goldPT.value();
      goldCategory = simplifyCategory(goldCategory);
      if (tlp.isEvalBIgnoredPunctuationTag(goldCategory) ||
          tlp.isPunctuationWord(goldPT.firstChild().value())) {
        return null;
      }
    }

    // remove the extra NPs inserted in the collinsBaseNP option
    if (fixCollinsBaseNP && s.equals("NP")) {
      Tree[] kids = guess.children();
      if (kids.length == 1 && tlp.basicCategory(kids[0].value()).equals("NP")) {
        return transformTree(kids[0], goldPreterminals);
      }
    }
    // Magerman erased this distinction, and everyone else has followed like sheep...
    if (s.equals("PRT")) {
      s = "ADVP";
    }
    List<Tree> children = new ArrayList<>();
    for (int cNum = 0, numKids = guess.numChildren(); cNum < numKids; cNum++) {
      Tree child = guess.children()[cNum];
      Tree newChild = transformTree(child, goldPreterminals);
      if (newChild != null) {
        children.add(newChild);
      }
    }
    if (children.isEmpty()) {
      return null;
    }

    Tree node = tf.newTreeNode(guess.label(), children);
    node.setValue(s);

    return node;
  }

} // end class TreeCollinizer
