package old.edu.stanford.nlp.trees;

import java.util.List;
import java.util.regex.Pattern;

import old.edu.stanford.nlp.trees.tregex.TregexPattern;
import old.edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import old.edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import old.edu.stanford.nlp.util.Generics;
import old.edu.stanford.nlp.util.Pair;

/**
 * Transforms an English structure parse tree in order to get the dependencies right:
 *  -- put a ROOT node
 *  -- remove NONE nodes
 *  -- retain only NP-TMP and NP-ADV tags
 *
 * (Note [cdm]: A lot of this overlaps other existing functionality in trees.
 * Could aim to unify it.)
 *
 * @author mcdm
 */
public class DependencyTreeTransformer implements TreeTransformer {


  private static final Pattern NPTmpPattern = Pattern.compile("NP.*-TMP.*");
  private static final Pattern NPAdvPattern = Pattern.compile("NP.*-ADV.*");
  protected final TreebankLanguagePack tlp;

  public DependencyTreeTransformer() {
    tlp = new PennTreebankLanguagePack();
  }

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
    boolean nptemp = NPTmpPattern.matcher(label).matches();
    boolean npadv = NPAdvPattern.matcher(label).matches();
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
    List<Pair<TregexPattern, TsurgeonPattern>> ops = Generics.newArrayList();
    ops.add(Generics.newPair(matchPattern, operation));
    return Tsurgeon.processPatternsOnTree(ops, t);
  }

}
