package edu.stanford.nlp.trees.international.french;

import java.util.Collections;

import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.trees.BobChrisTreeNormalizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.util.Filter;

/**
 * Prepares French Treebank trees for parsing.
 *
 * @author Spence Green
 *
 */
public class FrenchTreeNormalizer extends BobChrisTreeNormalizer {

  private static final long serialVersionUID = 7868735300308066991L;

  private final String rootLabel;

  public FrenchTreeNormalizer() {
    super(new FrenchTreebankLanguagePack());

    rootLabel = tlp.startSymbol();

    aOverAFilter = new FrenchAOverAFilter();

    emptyFilter = new Filter<Tree>() {
      private static final long serialVersionUID = -22673346831392110L;
      public boolean accept(Tree tree) {
        if(tree.isPreTerminal() && (tree.firstChild().value().equals("") || tree.firstChild().value().equals("-NONE-"))) {
          return false;
        }
        return true;
      }
    };
  }

  @Override
  public String normalizeTerminal(String terminal) {
    if(terminal == null) return terminal;

    // PTB escaping
    if(terminal.equals(")"))
      return "-RRB-";
    else if(terminal.equals("("))
      return "-LRB-";

    return super.normalizeTerminal(terminal).intern();
  }

  @Override
  public String normalizeNonterminal(String category) {
    return super.normalizeNonterminal(category).intern();
  }

  /**
   * Sets POS for punctuation to the punctuation token (like the PTB).
   *
   * @param t
   */
  private String normalizePreterminal(Tree t) {
    if(tlp.isPunctuationWord(t.firstChild().value()))
      return tlp.punctuationTags()[0].intern(); //Map to a common tag
//      return t.firstChild().value();//Map to the punctuation item

    return t.value();
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    tree = tree.prune(emptyFilter, tf).spliceOut(aOverAFilter, tf);

    for(Tree t : tree) {
      //Map punctuation tags back like the PTB
      if(t.isPreTerminal()) {
        String posStr = normalizePreterminal(t);
        t.setValue(posStr);
        if(t.label() instanceof HasTag) ((HasTag) t.label()).setTag(posStr);

      } else if(t.isLeaf()) {
        //Strip off morphological analyses and place them in the OriginalTextAnnotation, which is
        //specified by HasContext.
        if(t.value().contains(MorphoFeatureSpecification.MORPHO_MARK)) {
          String[] toks = t.value().split(MorphoFeatureSpecification.MORPHO_MARK);
          if(toks.length != 2)
            System.err.printf("%s: Word contains malformed morph annotation: %s%n",this.getClass().getName(),t.value());

          else if(t.label() instanceof CoreLabel) {
            ((CoreLabel) t.label()).setValue(toks[0].trim().intern());
            ((CoreLabel) t.label()).setWord(toks[0].trim().intern());
            ((CoreLabel) t.label()).setOriginalText(toks[1].trim().intern());
          } else {
            System.err.printf("%s: Cannot store morph analysis in non-CoreLabel: %s%n",this.getClass().getName(),t.label().getClass().getName());
          }
        }
      }
    }

    //Add start symbol so that the root has only one sub-state. Escape any enclosing brackets.
    //If the "tree" consists entirely of enclosing brackets e.g. ((())) then this method
    //will return null. In this case, readers e.g. PennTreeReader will try to read the next tree.
    while(tree != null && (tree.value() == null || tree.value().equals("")) && tree.numChildren() <= 1)
      tree = tree.firstChild();

    //Ensure that the tree has a top-level unary rewrite
    if(tree != null && !tree.value().equals(rootLabel))
      tree = tf.newTreeNode(rootLabel, Collections.singletonList(tree));

    return tree;
  }

  public static class FrenchAOverAFilter implements Filter<Tree> {

    private static final long serialVersionUID = 793800623099852951L;

    /** Doesn't accept nodes that are A over A nodes (perhaps due to
     *  empty removal or are EDITED nodes).
     *
     *  Also removes all w nodes.
     */
    public boolean accept(Tree t) {
      if(t.value() != null && t.value().equals("w"))
        return false;

      if (t.isLeaf() || t.isPreTerminal())
        return true;

      return ! (t.label() != null && t.label().value() != null && t.label().value().equals(t.getChild(0).label().value()));
    }
  }

}
