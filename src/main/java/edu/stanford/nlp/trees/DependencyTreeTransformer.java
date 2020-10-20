package edu.stanford.nlp.trees;

import java.util.regex.Pattern;

import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;

/**
 * Transforms an English structure parse tree in order to get the dependencies right:  <br>
 *  -- put a ROOT node  <br>
 *  -- remove NONE nodes  <br>
 *  -- retain only NP-TMP, NP-ADV, UCP-TMP tags  <br>
 * The UCP- tags will later be turned into NP- anyway <br>
 *
 * (Note [cdm]: A lot of this overlaps other existing functionality in trees.
 * Could aim to unify it.)
 *
 * @author mcdm
 */
public class DependencyTreeTransformer implements TreeTransformer {


  private static final Pattern TmpPattern = Pattern.compile("(NP|UCP).*-TMP.*");
  private static final Pattern AdvPattern = Pattern.compile("(NP|UCP).*-ADV.*");
  protected final TreebankLanguagePack tlp;

  public DependencyTreeTransformer() {
    tlp = new PennTreebankLanguagePack();
  }

  @Override
  public Tree transformTree(Tree t) {
    //deal with empty root
    t.setValue(cleanUpRoot(t.value()));
    //strips tags
    stripTag(t);

    // strip empty nodes
    return stripEmptyNode(t);
  }

  protected static String cleanUpRoot(String label) {
    if (label == null || label.equals("TOP")) {
      return "ROOT";
      // String constants are always interned
    } else {
      return label;
    }
  }

  // only leaves NP-TMP and NP-ADV
  protected String cleanUpLabel(String label) {
    if (label == null) {
      return "";  // This shouldn't really happen, but can happen if there are unlabeled nodes further down a tree, as apparently happens in at least the 20100730 era American National Corpus
    }
    boolean nptemp = TmpPattern.matcher(label).matches();
    boolean npadv = AdvPattern.matcher(label).matches();
    label = tlp.basicCategory(label);
    if (nptemp) {
      label = label + "-TMP";
    } else if (npadv) {
      label = label + "-ADV";
    }
    return label;
  }

  protected void stripTag(Tree t) {
    if ( ! t.isLeaf()) {
      String label = cleanUpLabel(t.value());
      t.setValue(label);
      for (Tree child : t.getChildrenAsList()) {
        stripTag(child);
      }
    }
  }

  private static final TregexPattern matchPattern =
    TregexPattern.safeCompile("-NONE-=none", true);

  private static final TsurgeonPattern operation =
    Tsurgeon.parseOperation("prune none");

  protected static Tree stripEmptyNode(Tree t) {
    return Tsurgeon.processPattern(matchPattern, operation, t);
  }

}
