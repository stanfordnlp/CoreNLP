package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.util.Filter;

import java.io.Reader;
import java.util.regex.Pattern;
import java.util.*;


/**
 * Same TreeNormalizer as BobChrisTreeNormalizer, but optionally provides
 * four extras.  I.e., the class name is now a misnomer.<br>
 * 1) retains -TMP labels on NP with the new identification NP-TMP,
 * and provides various options to percolate that option downwards
 * to the head noun, and perhaps also to inherit this from a PP-TMP.<br>
 * 2) Annotates S nodes which contain a gapped subject: i.e.,
 * <code>S &lt; (/^NP-SBJ/ &lt; -NONE-) --> S-G</code>  <br>
 * 3) Leave all functional tags on nodes. <br>
 * 4) Keeps -ADV labels on NP and marks head tag with &`^ADV
 * <p/>
 * <i>Performance note:</i> At one point in time, PCFG labeled F1 results
 * for the various TEMPORAL options in lexparser were:
 * 0=86.7, 1=87.49, 2=86.87, 3=87.49, 4=87.48, 5=87.5, 6=87.07.
 * So, mainly avoid values of 0, 2, and 6.
 * <p/>
 * At another point they were:
 * 0=86.53, 1=87.1, 2=87.14, 3=87.22, 4=87.1, 5=87.13, 6=86.95, 7=87.16
 *
 * @author Christopher Manning
 * @author Dan Klein
 */
public class NPTmpRetainingTreeNormalizer extends BobChrisTreeNormalizer {

  private static final long serialVersionUID = 7548777133196579107L;

  public static final int TEMPORAL_NONE = 0;
  public static final int TEMPORAL_ACL03PCFG = 1;
  public static final int TEMPORAL_ANY_TMP_PERCOLATED = 2;
  public static final int TEMPORAL_ALL_TERMINALS = 3;
  public static final int TEMPORAL_ALL_NP = 4;
  public static final int TEMPORAL_ALL_NP_AND_PP = 5;
  public static final int TEMPORAL_NP_AND_PP_WITH_NP_HEAD = 6;
  public static final int TEMPORAL_ALL_NP_EVEN_UNDER_PP = 7;
  public static final int TEMPORAL_ALL_NP_PP_ADVP = 8;
  public static final int TEMPORAL_9 = 9;

  private static final boolean onlyTagAnnotateNstar = true;

  private static final Pattern NPTmpPattern = Pattern.compile("NP.*-TMP.*");
  private static final Pattern PPTmpPattern = Pattern.compile("PP.*-TMP.*");
  private static final Pattern ADVPTmpPattern = Pattern.compile("ADVP.*-TMP.*");
  private static final Pattern TmpPattern = Pattern.compile(".*-TMP.*");
  private static final Pattern NPSbjPattern = Pattern.compile("NP.*-SBJ.*");
  private static final Pattern NPAdvPattern = Pattern.compile("NP.*-ADV.*");

  private final int temporalAnnotation;
  private final boolean doSGappedStuff;
  private final int leaveItAll;
  private final boolean doAdverbialNP;
  private final HeadFinder headFinder;


  public NPTmpRetainingTreeNormalizer() {
    this(TEMPORAL_ACL03PCFG, false);
  }

  public NPTmpRetainingTreeNormalizer(int temporalAnnotation, boolean doSGappedStuff) {
    this(temporalAnnotation, doSGappedStuff, 0, false);
  }

  public NPTmpRetainingTreeNormalizer(int temporalAnnotation, boolean doSGappedStuff, int leaveItAll, boolean doAdverbialNP) {
    this(temporalAnnotation, doSGappedStuff, leaveItAll, doAdverbialNP, new ModCollinsHeadFinder());
  }

  /**
   * Create a TreeNormalizer that maintains some functional annotations,
   * particularly those involving temporal annotation.
   *
   * @param temporalAnnotation One of the constants:
   *                           TEMPORAL_NONE (no temporal annotation kept on trees),
   *                           TEMPORAL_ACL03PCFG (temporal annotation on NPs, and percolated down
   *                           to head of constituent until and including POS tag),
   *                           TEMPORAL_ANY_TMP_PERCOLATED (temporal annotation on any phrase is
   *                           kept and percolated via head chain to and including POS tag),
   *                           TEMPORAL_ALL_TERMINALS (temporal annotation is kept on NPs, and
   *                           is placed on all POS tag daughters of that NP (but is not
   *                           percolated down a head chain through phrasal categories),
   *                           TEMPORAL_ALL_NP (temporal annotation on NPs, and it is percolated
   *                           down via the head chain, but only through NPs: annotation stops
   *                           at either a POS tag (which is annotated) or a non-NP head
   *                           (which isn't annotated)),
   *                           TEMPORAL_ALL_NP_AND_PP (keeps temporal annotation on NPs and PPs,
   *                           and it is percolated down via the head chain, but only through
   *                           NPs: annotation stops at either a POS tag (which is annotated)
   *                           or a non-NP head (which isn't annotated)).
   *                           TEMPORAL_NP_AND_PP_WITH_NP_HEAD (like TEMPORAL_ALL_NP_AND_PP
   *                           except an NP is regarded as the head of a PP)
   *                           TEMPORAL_ALL_NP_EVEN_UNDER_PP (like TEMPORAL_ALL_NP, but a PP-TMP
   *                           annotation above an NP is 'passed down' to annotate that NP
   *                           as temporal (but the PP itself isn't marked))
   *                           TEMPORAL_ALL_NP_PP_ADVP (keeps temporal annotation on NPs, PPs, and
   *                           ADVPs
   *                           and it is percolated down via the head chain, but only through
   *                           those categories: annotation stops at either a POS tag
   *                           (which is annotated)
   *                           or a non-NP/PP/ADVP head (which isn't annotated)),
   *                           TEMPORAL_9 (annotates like the previous one but
   *                           does all NP inside node, and their children if
   *                           pre-pre-terminal rather than only if head).
   * @param doSGappedStuff     Leave -SBJ marking on subject NP and then mark
   *                           S-G sentences with a gapped subject.
   * @param leaveItAll         0 means the usual stripping of functional tags and indices;
   *                           1 leaves all functional tags but still strips indices;
   *                           2 leaves everything
   * @param doAdverbialNP      Leave -ADV functional tag on adverbial NPs and
   *                           maybe add it to their head
   * @param headFinder         A head finder that is used with some of the
   *                           options for temporalAnnotation
   */
  public NPTmpRetainingTreeNormalizer(int temporalAnnotation, boolean doSGappedStuff, int leaveItAll, boolean doAdverbialNP, HeadFinder headFinder) {
    this.temporalAnnotation = temporalAnnotation;
    this.doSGappedStuff = doSGappedStuff;
    this.leaveItAll = leaveItAll;
    this.doAdverbialNP = doAdverbialNP;
    this.headFinder = headFinder;
  }


  /**
   * Remove things like hyphened functional tags and equals from the
   * end of a node label.
   */
  @Override
  protected String cleanUpLabel(String label) {
    if (label == null) {
      return "ROOT";
      // String constants are always interned
    } else if (leaveItAll == 1) {
      return tlp.categoryAndFunction(label);
    } else if (leaveItAll == 2) {
      return label;
    } else {
      boolean nptemp = NPTmpPattern.matcher(label).matches();
      boolean pptemp = PPTmpPattern.matcher(label).matches();
      boolean advptemp = ADVPTmpPattern.matcher(label).matches();
      boolean anytemp = TmpPattern.matcher(label).matches();
      boolean subj = NPSbjPattern.matcher(label).matches();
      boolean npadv = NPAdvPattern.matcher(label).matches();
      label = tlp.basicCategory(label);
      if (anytemp && temporalAnnotation == TEMPORAL_ANY_TMP_PERCOLATED) {
        label += "-TMP";
      } else if (pptemp && (temporalAnnotation == TEMPORAL_ALL_NP_AND_PP || temporalAnnotation == TEMPORAL_NP_AND_PP_WITH_NP_HEAD || temporalAnnotation == TEMPORAL_ALL_NP_EVEN_UNDER_PP || temporalAnnotation == TEMPORAL_ALL_NP_PP_ADVP || temporalAnnotation == TEMPORAL_9)) {
        label = label + "-TMP";
      } else if (advptemp && (temporalAnnotation == TEMPORAL_ALL_NP_PP_ADVP || temporalAnnotation == TEMPORAL_9)) {
        label = label + "-TMP";
      } else if (temporalAnnotation > 0 && nptemp) {
        label = label + "-TMP";
      }
      if (doAdverbialNP && npadv) {
        label = label + "-ADV";
      }
      if (doSGappedStuff && subj) {
        label = label + "-SBJ";
      }
      return label;
    }
  }


  private static boolean includesEmptyNPSubj(Tree t) {
    if (t == null) {
      return false;
    }
    Tree[] kids = t.children();
    if (kids == null) {
      return false;
    }
    boolean foundNullSubj = false;
    for (Tree kid : kids) {
      Tree[] kidkids = kid.children();
      if (NPSbjPattern.matcher(kid.value()).matches()) {
        kid.setValue("NP");
        if (kidkids != null && kidkids.length == 1 && kidkids[0].value().equals("-NONE-")) {
          // only set flag, since there are 2 a couple of times (errors)
          foundNullSubj = true;
        }
      }
    }
    return foundNullSubj;
  }


  /**
   * Normalize a whole tree -- one can assume that this is the root.
   * This implementation deletes empty elements (ones with nonterminal
   * tag label '-NONE-') from the tree.
   */
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    TreeTransformer transformer1 = new TreeTransformer() {
      @Override
      public Tree transformTree(Tree t) {
        if (doSGappedStuff) {
          String lab = t.label().value();
          if (lab.equals("S") && includesEmptyNPSubj(t)) {
            LabelFactory lf = t.label().labelFactory();
            // Note: this changes the tree label, rather than
            // creating a new tree node.  Beware!
            t.setLabel(lf.newLabel(t.label().value() + "-G"));
          }
        }
        return t;
      }
    };
    Filter<Tree> subtreeFilter = new Filter<Tree>() {

      private static final long serialVersionUID = -7250433816896327901L;

      @Override
      public boolean accept(Tree t) {
        Tree[] kids = t.children();
        Label l = t.label();
        // The special Switchboard non-terminals clause.
        // Note that it deletes IP which other Treebanks might use!
        if ("RS".equals(t.label().value()) || "RM".equals(t.label().value()) || "IP".equals(t.label().value()) || "CODE".equals(t.label().value())) {
          return false;
        }
        if ((l != null) && l.value() != null && (l.value().equals("-NONE-")) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf()) {
          // Delete empty/trace nodes (ones marked '-NONE-')
          return false;
        }
        return true;
      }
    };
    Filter<Tree> nodeFilter = new Filter<Tree>() {

      private static final long serialVersionUID = 9000955019205336311L;

      @Override
      public boolean accept(Tree t) {
        if (t.isLeaf() || t.isPreTerminal()) {
          return true;
        }
        // The special switchboard non-terminals clause. Try keeping EDITED for now....
        // if ("EDITED".equals(t.label().value())) {
        //   return false;
        // }
        if (t.numChildren() != 1) {
          return true;
        }
        if (t.label() != null && t.label().value() != null && t.label().value().equals(t.children()[0].label().value())) {
          return false;
        }
        return true;
      }
    };
    TreeTransformer transformer2 = new TreeTransformer() {
      @Override
      public Tree transformTree(Tree t) {
        if (temporalAnnotation == TEMPORAL_ANY_TMP_PERCOLATED) {
          String lab = t.label().value();
          if (TmpPattern.matcher(lab).matches()) {
            Tree oldT = t;
            Tree ht;
            do {
              ht = headFinder.determineHead(oldT);
              // special fix for possessives! -- make noun before head
              if (ht.label().value().equals("POS")) {
                int j = oldT.objectIndexOf(ht);
                if (j > 0) {
                  ht = oldT.getChild(j - 1);
                }
              }
              LabelFactory lf = ht.label().labelFactory();
              // Note: this changes the tree label, rather than
              // creating a new tree node.  Beware!
              ht.setLabel(lf.newLabel(ht.label().value() + "-TMP"));
              oldT = ht;
            } while (!ht.isPreTerminal());
            if (lab.startsWith("PP")) {
              ht = headFinder.determineHead(t);
              // look to right
              int j = t.objectIndexOf(ht);
              int sz = t.children().length;
              if (j + 1 < sz) {
                ht = t.getChild(j + 1);
              }
              if (ht.label().value().startsWith("NP")) {
                while (!ht.isLeaf()) {
                  LabelFactory lf = ht.label().labelFactory();
                  // Note: this changes the tree label, rather than
                  // creating a new tree node.  Beware!
                  ht.setLabel(lf.newLabel(ht.label().value() + "-TMP"));
                  ht = headFinder.determineHead(ht);
                }
              }
            }
          }
        } else if (temporalAnnotation == TEMPORAL_ALL_TERMINALS) {
          String lab = t.label().value();
          if (NPTmpPattern.matcher(lab).matches()) {
            Tree ht;
            ht = headFinder.determineHead(t);
            if (ht.isPreTerminal()) {
              // change all tags to -TMP
              LabelFactory lf = ht.label().labelFactory();
              Tree[] kids = t.children();
              for (Tree kid : kids) {
                if (kid.isPreTerminal()) {
                  // Note: this changes the tree label, rather
                  // than creating a new tree node.  Beware!
                  kid.setLabel(lf.newLabel(kid.value() + "-TMP"));
                }
              }
            } else {
              Tree oldT = t;
              do {
                ht = headFinder.determineHead(oldT);
                oldT = ht;
              } while (!ht.isPreTerminal());
              LabelFactory lf = ht.label().labelFactory();
              // Note: this changes the tree label, rather than
              // creating a new tree node.  Beware!
              ht.setLabel(lf.newLabel(ht.label().value() + "-TMP"));
            }
          }
        } else if (temporalAnnotation == TEMPORAL_ALL_NP) {
          String lab = t.label().value();
          if (NPTmpPattern.matcher(lab).matches()) {
            Tree oldT = t;
            Tree ht;
            do {
              ht = headFinder.determineHead(oldT);
              // special fix for possessives! -- make noun before head
              if (ht.label().value().equals("POS")) {
                int j = oldT.objectIndexOf(ht);
                if (j > 0) {
                  ht = oldT.getChild(j - 1);
                }
              }
              if (ht.isPreTerminal() || ht.value().startsWith("NP")) {
                LabelFactory lf = ht.labelFactory();
                // Note: this changes the tree label, rather than
                // creating a new tree node.  Beware!
                ht.setLabel(lf.newLabel(ht.label().value() + "-TMP"));
                oldT = ht;
              }
            } while (ht.value().startsWith("NP"));
          }
        } else if (temporalAnnotation == TEMPORAL_ALL_NP_AND_PP || temporalAnnotation == TEMPORAL_NP_AND_PP_WITH_NP_HEAD || temporalAnnotation == TEMPORAL_ALL_NP_EVEN_UNDER_PP) {
          // also allow chain to start with PP
          String lab = t.value();
          if (NPTmpPattern.matcher(lab).matches() || PPTmpPattern.matcher(lab).matches()) {
            Tree oldT = t;
            do {
              Tree ht = headFinder.determineHead(oldT);
              // special fix for possessives! -- make noun before head
              if (ht.value().equals("POS")) {
                int j = oldT.objectIndexOf(ht);
                if (j > 0) {
                  ht = oldT.getChild(j - 1);
                }
              } else if ((temporalAnnotation == TEMPORAL_NP_AND_PP_WITH_NP_HEAD || temporalAnnotation == TEMPORAL_ALL_NP_EVEN_UNDER_PP) && (ht.value().equals("IN") || ht.value().equals("TO"))) {
                // change the head to be NP if possible
                Tree[] kidlets = oldT.children();
                for (int k = kidlets.length - 1; k > 0; k--) {
                  if (kidlets[k].value().startsWith("NP")) {
                    ht = kidlets[k];
                  }
                }
              }
              LabelFactory lf = ht.labelFactory();
              // Note: this next bit changes the tree label, rather
              // than creating a new tree node.  Beware!
              if (ht.isPreTerminal() || ht.value().startsWith("NP")) {
                ht.setLabel(lf.newLabel(ht.value() + "-TMP"));
              }
              if (temporalAnnotation == TEMPORAL_ALL_NP_EVEN_UNDER_PP && oldT.value().startsWith("PP")) {
                oldT.setLabel(lf.newLabel(tlp.basicCategory(oldT.value())));
              }
              oldT = ht;
            } while (oldT.value().startsWith("NP") || oldT.value().startsWith("PP"));
          }
        } else if (temporalAnnotation == TEMPORAL_ALL_NP_PP_ADVP) {
          // also allow chain to start with PP or ADVP
          String lab = t.value();
          if (NPTmpPattern.matcher(lab).matches() || PPTmpPattern.matcher(lab).matches() || ADVPTmpPattern.matcher(lab).matches()) {
            Tree oldT = t;
            do {
              Tree ht = headFinder.determineHead(oldT);
              // special fix for possessives! -- make noun before head
              if (ht.value().equals("POS")) {
                int j = oldT.objectIndexOf(ht);
                if (j > 0) {
                  ht = oldT.getChild(j - 1);
                }
              }
              // Note: this next bit changes the tree label, rather
              // than creating a new tree node.  Beware!
              if (ht.isPreTerminal() || ht.value().startsWith("NP")) {
                LabelFactory lf = ht.labelFactory();
                ht.setLabel(lf.newLabel(ht.value() + "-TMP"));
              }
              oldT = ht;
            } while (oldT.value().startsWith("NP"));
          }
        } else if (temporalAnnotation == TEMPORAL_9) {
          // also allow chain to start with PP or ADVP
          String lab = t.value();
          if (NPTmpPattern.matcher(lab).matches() || PPTmpPattern.matcher(lab).matches() || ADVPTmpPattern.matcher(lab).matches()) {
            // System.err.println("TMP: Annotating " + t);
            addTMP9(t);
          }
        } else if (temporalAnnotation == TEMPORAL_ACL03PCFG) {
          String lab = t.label().value();
          if (lab != null && NPTmpPattern.matcher(lab).matches()) {
            Tree oldT = t;
            Tree ht;
            do {
              ht = headFinder.determineHead(oldT);
              // special fix for possessives! -- make noun before head
              if (ht.label().value().equals("POS")) {
                int j = oldT.objectIndexOf(ht);
                if (j > 0) {
                  ht = oldT.getChild(j - 1);
                }
              }
              oldT = ht;
            } while (!ht.isPreTerminal());
            if ( ! onlyTagAnnotateNstar || ht.label().value().startsWith("N")) {
              LabelFactory lf = ht.label().labelFactory();
              // Note: this changes the tree label, rather than
              // creating a new tree node.  Beware!
              ht.setLabel(lf.newLabel(ht.label().value() + "-TMP"));
            }
          }
        }
        if (doAdverbialNP) {
          String lab = t.value();
          if (NPAdvPattern.matcher(lab).matches()) {
            Tree oldT = t;
            Tree ht;
            do {
              ht = headFinder.determineHead(oldT);
              // special fix for possessives! -- make noun before head
              if (ht.label().value().equals("POS")) {
                int j = oldT.objectIndexOf(ht);
                if (j > 0) {
                  ht = oldT.getChild(j - 1);
                }
              }
              if (ht.isPreTerminal() || ht.value().startsWith("NP")) {
                LabelFactory lf = ht.labelFactory();
                // Note: this changes the tree label, rather than
                // creating a new tree node.  Beware!
                ht.setLabel(lf.newLabel(ht.label().value() + "-ADV"));
                oldT = ht;
              }
            } while (ht.value().startsWith("NP"));
          }
        }
        return t;
      }
    };
    // if there wasn't an empty nonterminal at the top, but an S, wrap it.
    if (tree.label().value().equals("S")) {
      tree = tf.newTreeNode("ROOT", Collections.singletonList(tree));
    }
    // repair for the phrasal VB in Switchboard (PTB version 3) that should be a VP
    for (Tree subtree : tree) {
      if (subtree.isPhrasal() && "VB".equals(subtree.label().value())) {
        subtree.setValue("VP");
      }
    }
    tree = tree.transform(transformer1);
    if (tree == null) { return null; }
    tree = tree.prune(subtreeFilter, tf);
    if (tree == null) { return null; }
    tree = tree.spliceOut(nodeFilter, tf);
    if (tree == null) { return null; }
    return tree.transform(transformer2, tf);
  }

  /**
   * Add -TMP when not present within an NP
   * @param tree The tree to add temporal info to.
   */
  private void addTMP9(final Tree tree) {
    // do the head chain under it
    Tree ht = headFinder.determineHead(tree);
    // special fix for possessives! -- make noun before head
    if (ht.value().equals("POS")) {
      int j = tree.objectIndexOf(ht);
      if (j > 0) {
        ht = tree.getChild(j - 1);
      }
    }
    // Note: this next bit changes the tree label, rather
    // than creating a new tree node.  Beware!
    if (ht.isPreTerminal() || ht.value().startsWith("NP") ||
        ht.value().startsWith("PP") || ht.value().startsWith("ADVP")) {
      if (!TmpPattern.matcher(ht.value()).matches()) {
        LabelFactory lf = ht.labelFactory();
        // System.err.println("TMP: Changing " + ht.value() + " to " +
        //                   ht.value() + "-TMP");
        ht.setLabel(lf.newLabel(ht.value() + "-TMP"));
      }
      if (ht.value().startsWith("NP") || ht.value().startsWith("PP") ||
          ht.value().startsWith("ADVP")) {
        addTMP9(ht);
      }
    }
    // do the NPs under it (which may or may not be the head chain
    Tree[] kidlets = tree.children();
    for (int k = 0; k < kidlets.length; k++) {
      ht = kidlets[k];
      LabelFactory lf;
      if (tree.isPrePreTerminal() && !TmpPattern.matcher(ht.value()).matches()) {
        // System.err.println("TMP: Changing " + ht.value() + " to " +
        //                   ht.value() + "-TMP");
        lf = ht.labelFactory();
        // Note: this next bit changes the tree label, rather
        // than creating a new tree node.  Beware!
        ht.setLabel(lf.newLabel(ht.value() + "-TMP"));
      } else if (ht.value().startsWith("NP")) {
        // don't add -TMP twice!
        if (!TmpPattern.matcher(ht.value()).matches()) {
          lf = ht.labelFactory();
          // System.err.println("TMP: Changing " + ht.value() + " to " +
          //                   ht.value() + "-TMP");
          // Note: this next bit changes the tree label, rather
          // than creating a new tree node.  Beware!
          ht.setLabel(lf.newLabel(ht.value() + "-TMP"));
        }
        addTMP9(ht);
      }
    }
  }

  /** Implementation of TreeReaderFactory, mainly for convenience of
   *  constructing by reflection.
   */
  public static class NPTmpRetainingTreeReaderFactory implements TreeReaderFactory {

    @Override
    public TreeReader newTreeReader(Reader in) {
      return new PennTreeReader(in, new LabeledScoredTreeFactory(), new NPTmpRetainingTreeNormalizer());
    }

  }

  /** Implementation of TreeReaderFactory, mainly for convenience of
   *  constructing by reflection. This one corresponds to what's currently
   *  used in englishPCFG accurate unlexicalized parser.
   */
  public static class NPTmpAdvRetainingTreeReaderFactory implements TreeReaderFactory {

    @Override
    public TreeReader newTreeReader(Reader in) {
      return new PennTreeReader(in, new LabeledScoredTreeFactory(),
              new NPTmpRetainingTreeNormalizer(NPTmpRetainingTreeNormalizer.TEMPORAL_ACL03PCFG,
                      false, 0, true));
    }

  }

}
