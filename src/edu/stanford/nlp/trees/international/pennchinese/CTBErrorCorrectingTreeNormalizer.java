package edu.stanford.nlp.trees.international.pennchinese;

import java.io.Serializable;
import java.util.regex.Pattern;
import java.util.*;

import edu.stanford.nlp.trees.BobChrisTreeNormalizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import java.util.function.Predicate;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.io.EncodingPrintWriter;


/**
 * This was originally written to correct a few errors Galen found in CTB3.
 * The thinking was that perhaps when we get CTB4 they would be gone and we
 * could revert to BobChris.  Alas, CTB4 contained only more errors....
 * It has since been extended to allow some functional tags from CTB to be
 * maintained.  This is so far much easier than in NPTmpRetainingTN, since
 * we don't do any tag percolation (helped by CTB marking temporal nouns).
 * <p>
 * <i>Implementation note:</i> This now loads CharacterLevelTagExtender by
 * reflection if that option is invoked.
 *
 * @author Galen Andrew
 * @author Christopher Manning
 */
public class CTBErrorCorrectingTreeNormalizer extends BobChrisTreeNormalizer {

  private static final long serialVersionUID = -8203853817025401845L;

  private static final Pattern NPTmpPattern = Pattern.compile("NP.*-TMP.*");
  private static final Pattern PPTmpPattern = Pattern.compile("PP.*-TMP.*");
  private static final Pattern TmpPattern = Pattern.compile(".*-TMP.*");

  private static final boolean DEBUG = System.getProperty("CTBErrorCorrectingTreeNormalizer") != null;

  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private final TreeTransformer tagExtender;

  private final boolean splitNPTMP;
  private final boolean splitPPTMP;
  private final boolean splitXPTMP;

  /** Constructor with all of the options of the other constructor false */
  public CTBErrorCorrectingTreeNormalizer() {
    this(false, false, false, false);
  }

  /**
   * Build a CTBErrorCorrectingTreeNormalizer.
   *
   * @param splitNPTMP Temporal annotation on NPs
   * @param splitPPTMP Temporal annotation on PPs
   * @param splitXPTMP Temporal annotation on any phrase marked in CTB
   * @param charTags Whether you wish to push POS tags down on to the
   *           characters of a word (for unsegmented text)
   */
  public CTBErrorCorrectingTreeNormalizer(boolean splitNPTMP, boolean splitPPTMP, boolean splitXPTMP, boolean charTags) {
    this.splitNPTMP = splitNPTMP;
    this.splitPPTMP = splitPPTMP;
    this.splitXPTMP = splitXPTMP;
    if (charTags) {
      try {
        tagExtender = (TreeTransformer) Class.forName("edu.stanford.nlp.trees.international.pennchinese.CharacterLevelTagExtender").newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      tagExtender = null;
    }
  }


  /**
   * Remove things like hyphened functional tags and equals from the
   * end of a node label.  But keep occasional functional tags as
   * determined by class parameters, particularly TMP
   *
   * @param label The label to be cleaned up
   */
  @Override
  protected String cleanUpLabel(String label) {
    if (label == null) {
      return "ROOT";
    } else {
      boolean nptemp = NPTmpPattern.matcher(label).matches();
      boolean pptemp = PPTmpPattern.matcher(label).matches();
      boolean anytemp = TmpPattern.matcher(label).matches();
      label = tlp.basicCategory(label);
      if (anytemp && splitXPTMP) {
        label += "-TMP";
      } else if (pptemp && splitPPTMP) {
        label = label + "-TMP";
      } else if (nptemp && splitNPTMP) {
        label = label + "-TMP";
      }
      return label;
    }
  }


  private static class ChineseEmptyFilter implements Predicate<Tree>, Serializable {

    private static final long serialVersionUID = 8914098359495987617L;

    /** Doesn't accept nodes that only cover an empty. */
    @Override
    public boolean test(Tree t) {
      Tree[] kids = t.children();
      Label l = t.label();
      if ((l != null) && l.value() != null && // there appears to be a mistake in CTB3 where the label "-NONE-1" is used once
              // presumably it should be "-NONE-" and be spliced out here.
              (l.value().matches("-NONE-.*")) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf()) {
        // Delete empty/trace nodes (ones marked '-NONE-')
        if ( ! l.value().equals("-NONE-")) {
          EncodingPrintWriter.err.println("Deleting errant node " + l.value() + " as if -NONE-: " + t, ChineseTreebankLanguagePack.ENCODING);
        }
        return false;
      }
      return true;
    }

  }

  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private final Predicate<Tree> chineseEmptyFilter = new ChineseEmptyFilter();

  private static final TregexPattern[] fixupTregex = {
          TregexPattern.compile("PU=punc < 她｛"),
          TregexPattern.compile("@NP <1 (@NP <1 NR <2 (PU=bad < /^＜$/)) <2 (FLR=dest <2 (NT < /Ｅｎｇｌｉｓｈ/))"),
          TregexPattern.compile("@IP < (FLR=dest <: (PU < /^〈$/) $. (__=bad1 $. (PU=bad2 < /^〉$/)))"),
          TregexPattern.compile("@DFL|FLR|IMG|SKIP=junk <<, (PU < /^[〈｛{＜\\[［]$/) <<- (PU < /^[〉｝}＞\\]］]$/)  <3 __"),
          TregexPattern.compile("WHPP=bad"),
  };
  private static final TsurgeonPattern[] fixupTsurgeon = {
          Tsurgeon.parseOperation("replace punc (PN 她) (PU ｛)"),
          Tsurgeon.parseOperation("move bad >1 dest"),
          Tsurgeon.parseOperation("[move bad1 >-1 dest] [move bad2 >-1 dest]"),
          Tsurgeon.parseOperation("delete junk"),
          Tsurgeon.parseOperation("relabel bad PP"),
  };

  static {
    if (fixupTregex.length != fixupTsurgeon.length) {
      throw new AssertionError("fixupTregex and fixupTsurgeon have different lengths in CTBErrorCorrectingTreeNormalizer.");
    }
  }

  // We delete the most egregious non-speech DFL, FLR, IMG, and SKIP constituents, according to the Tregex
  // expression above. Maybe more should be deleted really. I don't understand this very well, and there is no documentation.

  // New phrasal categories in CTB 7 and later:
  // DFL = Disfluency. Generally keep but delete for ones that are things like (FLR (PU <) (VV turn) (PU >)).
  // EMO = Emoticon. For emoticons. Fine to keep.
  // FLR = Filler.  Generally keep but delete for ones that are things like (FLR (PU <) (VV turn) (PU >)).
  // IMG = ?Image?. Appear to all be of form (IMG (PU [) (NN 图片) (PU ])). Delete all those.
  // INC = Incomplete (more incomplete than a FRAG which is only syntactically incomplete). Just keep.
  // INTJ = Interjection. Fine to keep.
  // META = Just one of these in chtb_5200.df. Delete whole tree. Should have been turned into XML metadata
  // OTH = ??. Weird but just leave.
  // SKIP = ??. Always has NOI under it. Omit or keep?
  // TYPO = seems like should mainly go, but sometimes a branching node??
  // WHPP = ??. Just one of these. Over a -NONE- so will go if empties are deleted. But should just be PP.
  //
  // There is a tree in chtb_2856.bn which has IP -> ... PU (FLR (PU <)) (VV turn) (PU >)
  // which just seems an error - should all be under FLR.
  //
  // POS tags are now 38. Original 33 plus these:
  // EM = Emoticon. Often but not always under EMO.
  // IC = Incomplete word rendered in pinyin, usually under DFL.
  // NOI =
  // URL = URL.
  // X = In practice currently used only for "x" in constructions like "30 x 25 cm". Shouldn't exist!


  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    Tree newTree = tree.prune(chineseEmptyFilter, tf).spliceOut(aOverAFilter);

    // Report non-unary initial rewrites & fix 'obvious ones'
    Tree[] kids = newTree.children();
    if (kids.length > 1) {
    /* -------------- don't do this as probably shouldn't for test set (and doesn't help anyway)
      if (kids.length == 2 &&
          "PU".equals(kids[kids.length - 1].value()) &&
          kids[0].isPhrasal()) {
        printlnErr("Correcting error: non-unary initial rewrite fixed by tucking punctuation inside constituent: " + newTree.localTree());
        List kidkids = kids[0].getChildrenAsList();
        kidkids.add(kids[1]);
        Tree bigger = tf.newTreeNode(kids[0].label(), kidkids);
        newTree = tf.newTreeNode(newTree.label(), Collections.singletonList(bigger));
      } else {
    -------------------- */
      EncodingPrintWriter.err.println("Possible error: non-unary initial rewrite: " +
                             newTree.localTree(), ChineseTreebankLanguagePack.ENCODING);
      // }
    } else if (kids.length > 0) { // ROOT has 1 child - the normal case
      Tree child = kids[0];
      if ( ! child.isPhrasal()) {
        if (DEBUG) {
          EncodingPrintWriter.err.println("Correcting error: treebank tree is not phrasal; wrapping in FRAG: " + child, ChineseTreebankLanguagePack.ENCODING);
        }
        Tree added = tf.newTreeNode("FRAG", Arrays.asList(kids));
        newTree.setChild(0, added);
      } else if (child.label().value().equals("META")) {
        // Delete the one bogus META tree in CTB 9
        EncodingPrintWriter.err.println("Deleting META tree that should be XML metadata in chtb_5200.df: " + child, ChineseTreebankLanguagePack.ENCODING);
        return null;
      }

    } else {
      EncodingPrintWriter.err.println("Error: tree with no children: " + tree, ChineseTreebankLanguagePack.ENCODING);
    }

    // note that there's also at least 1 tree that is an IP with no surrounding ROOT node

    // there are also several places where "NP" is used as a preterminal tag
    // and presumably should be "NN"
    // a couple of other random errors are corrected here
    for (Tree subtree : newTree) {
      if (subtree.value().equals("CP") && subtree.numChildren() == 1) {
        Tree subsubtree = subtree.firstChild();
        if (subsubtree.value().equals("ROOT")) {
          if (subsubtree.firstChild().isLeaf() && "CP".equals(subsubtree.firstChild().value())) {
            EncodingPrintWriter.err.println("Correcting error: seriously messed up tree in CTB6 (chtb_3095.bn): " + newTree, ChineseTreebankLanguagePack.ENCODING);
            List<Tree> children = subsubtree.getChildrenAsList();
            children = children.subList(1,children.size());
            subtree.setChildren(children);
            EncodingPrintWriter.err.println("  Corrected as:                                                    " + newTree, ChineseTreebankLanguagePack.ENCODING); // spaced to align with above
          }
        }
      }
      // All the stuff below here seems to have been fixed in CTB 9. Maybe reporting errors sometimes does help.
      if (subtree.isPreTerminal()) {
        if (subtree.value().matches("NP")) {
          if (ChineseTreebankLanguagePack.chineseDouHaoAcceptFilter().test(subtree.firstChild().value())) {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: NP preterminal over douhao; preterminal changed to PU: " + subtree, ChineseTreebankLanguagePack.ENCODING);
            }
            subtree.setValue("PU");
          } else if (subtree.parent(newTree).value().matches("NP")) {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: NP preterminal w/ NP parent; preterminal changed to NN: " + subtree.parent(newTree), ChineseTreebankLanguagePack.ENCODING);
            }
            subtree.setValue("NN");
          } else {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: NP preterminal w/o NP parent, changing preterminal to NN: " + subtree.parent(newTree), ChineseTreebankLanguagePack.ENCODING);
            }
            // Tree newChild = tf.newTreeNode("NN", Collections.singletonList(subtree.firstChild()));
            // subtree.setChildren(Collections.singletonList(newChild));
            subtree.setValue("NN");
          }
        } else if (subtree.value().matches("PU")) {
          if (subtree.firstChild().value().matches("他")) {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: \"他\" under PU tag; tag changed to PN: " + subtree, ChineseTreebankLanguagePack.ENCODING);
            }
            subtree.setValue("PN");
          } else if (subtree.firstChild().value().equals("里")) {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: \"" + subtree.firstChild().value() + "\" under PU tag; tag changed to LC: " + subtree, ChineseTreebankLanguagePack.ENCODING);
            }
            subtree.setValue("LC");
          } else if (subtree.firstChild().value().equals("是")) {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: \"" + subtree.firstChild().value() + "\" under PU tag; tag changed to VC: " + subtree, ChineseTreebankLanguagePack.ENCODING);
            }
            subtree.setValue("VC");
          } else if (subtree.firstChild().value().matches("tw|半穴式")) {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: \"" + subtree.firstChild().value() + "\" under PU tag; tag changed to NN: " + subtree, ChineseTreebankLanguagePack.ENCODING);
            }
            subtree.setValue("NN");
          } else if (subtree.firstChild().value().matches("33")) {
            if (DEBUG) {
              EncodingPrintWriter.err.println("Correcting error: \"33\" under PU tag; tag changed to CD: " + subtree, ChineseTreebankLanguagePack.ENCODING);
            }
            subtree.setValue("CD");
          }
        }
      } else if (subtree.value().matches("NN")) {
        if (DEBUG) {
          EncodingPrintWriter.err.println("Correcting error: NN phrasal tag changed to NP: " + subtree, ChineseTreebankLanguagePack.ENCODING);
        }
        subtree.setValue("NP");
      } else if (subtree.value().matches("MSP")) {
        if (DEBUG) {
          EncodingPrintWriter.err.println("Correcting error: MSP phrasal tag changed to VP: " + subtree, ChineseTreebankLanguagePack.ENCODING);
        }
        subtree.setValue("VP");
      }
    }

    for (int i = 0; i < fixupTregex.length; ++i) {
      if (DEBUG) {
        Tree preProcessed = newTree.deepCopy();
        newTree = Tsurgeon.processPattern(fixupTregex[i], fixupTsurgeon[i], newTree);
        if (!preProcessed.equals(newTree)) {
          EncodingPrintWriter.err.println("Correcting error: Updated tree using tregex " + fixupTregex[i] + " and tsurgeon " + fixupTsurgeon[i], ChineseTreebankLanguagePack.ENCODING);
          EncodingPrintWriter.err.println("  from: " + preProcessed, ChineseTreebankLanguagePack.ENCODING);
          EncodingPrintWriter.err.println("    to: " + newTree, ChineseTreebankLanguagePack.ENCODING);
        }
      } else {
        newTree = Tsurgeon.processPattern(fixupTregex[i], fixupTsurgeon[i], newTree);
      }
    }

    // at least once we just end up deleting everything under ROOT. In which case, we should just get rid of the tree.
    if (newTree.numChildren() == 0) {
      if (DEBUG) {
        EncodingPrintWriter.err.println("Deleting tree that now has no contents: " + newTree, ChineseTreebankLanguagePack.ENCODING);
      }
      return null;
    }

    if (tagExtender != null) {
      newTree = tagExtender.transformTree(newTree);
    }
    return newTree;
  }

  /** So you can create a TreeReaderFactory using this TreeNormalizer easily by reflection. */
  public static class CTBErrorCorrectingTreeReaderFactory extends CTBTreeReaderFactory {

    public CTBErrorCorrectingTreeReaderFactory() {
      super(new CTBErrorCorrectingTreeNormalizer(false, false, false, false));
    }

  } // end class CTBErrorCorrectingTreeReaderFactory

} // end class CTBErrorCorrectingTreeNormalizer
