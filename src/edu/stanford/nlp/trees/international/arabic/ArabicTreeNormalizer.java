package edu.stanford.nlp.trees.international.arabic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.arabic.pipeline.DefaultLexicalMapper;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.trees.treebank.Mapper;
import edu.stanford.nlp.trees.BobChrisTreeNormalizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Pair;

/**
 * Normalizes both terminals and non-terminals in Penn Arabic Treebank (ATB)
 * trees. Among the normalizations that can be performed:
 *
 * <ul>
 * <li> Adds a ROOT node to the top of every tree
 * <li> Strips all the interesting stuff off of the POS tags.
 * <li> Can keep NP-TMP annotations (retainNPTmp parameter)
 * <li> Can keep whatever annotations there are on verbs that are sisters
 *           to predicatively marked (-PRD) elements (markPRDverb parameter)
 *           [Chris Nov 2006: I'm a bit unsure on that one!]
 * <li> Can keep categories unchanged, i.e., not mapped to basic categories
 *           (changeNoLabels parameter)
 * <li> Counts pronoun deletions ("nullp" and "_") as empty; filters
 * </ul>
 *
 * @author Roger Levy
 * @author Anna Rafferty
 * @author Spence Green
 */
public class ArabicTreeNormalizer extends BobChrisTreeNormalizer {

  private final boolean retainNPTmp;
  private final boolean retainNPSbj;
  private final boolean markPRDverb;
  private final boolean changeNoLabels;
  private final boolean retainPPClr;

  private final Pattern prdPattern;
  private final TregexPattern prdVerbPattern;
  private final TregexPattern npSbjPattern;
  private final String rootLabel;

  private final Mapper lexMapper = new DefaultLexicalMapper();

  public ArabicTreeNormalizer(boolean retainNPTmp, boolean markPRDverb, boolean changeNoLabels,
      boolean retainNPSbj, boolean retainPPClr) {
    super(new ArabicTreebankLanguagePack());
    this.retainNPTmp = retainNPTmp;
    this.retainNPSbj = retainNPSbj;
    this.markPRDverb = markPRDverb;
    this.changeNoLabels = changeNoLabels;
    this.retainPPClr = retainPPClr;

    rootLabel = tlp.startSymbol();

    prdVerbPattern  = TregexPattern.compile("/^V[^P]/ > VP $ /-PRD$/=prd");

    prdPattern = Pattern.compile("^[A-Z]+-PRD");

    //Marks NP subjects that *do not* occur in verb-initial clauses
    npSbjPattern = TregexPattern.compile("/^NP-SBJ/ !> @VP");

    emptyFilter = new ArabicEmptyFilter();
  }

  public ArabicTreeNormalizer(boolean retainNPTmp, boolean markPRDverb,
      boolean changeNoLabels) {
    this(retainNPTmp, markPRDverb, changeNoLabels, false, false);
  }

  public ArabicTreeNormalizer(boolean retainNPTmp, boolean markPRDverb) {
    this(retainNPTmp,markPRDverb,false);
  }

  public ArabicTreeNormalizer(boolean retainNPTmp) {
    this(retainNPTmp,false);
  }

  public ArabicTreeNormalizer() {
    this(false);
  }

  @Override
  public String normalizeNonterminal(String category) {
    String normalizedString;
    if (changeNoLabels) {
      normalizedString = category;
    } else if (retainNPTmp && category != null && category.startsWith("NP-TMP")) {
      normalizedString = "NP-TMP";
    } else if (retainNPSbj && category != null && category.startsWith("NP-SBJ")) {
      normalizedString = "NP-SBJ";
    } else if (retainPPClr && category != null && category.startsWith("PP-CLR")) {
      normalizedString = "PP-CLR";
    } else if (markPRDverb && category != null && prdPattern.matcher(category).matches()) {
      normalizedString = category;
    } else {
      // otherwise, return the basicCategory (and turn null to ROOT)
      normalizedString = super.normalizeNonterminal(category);
    }

    return normalizedString.intern();
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    tree = tree.prune(emptyFilter, tf).spliceOut(aOverAFilter, tf);

    for (Tree t : tree) {

      if(t.isLeaf()) {
        //Strip off morphological analyses and place them in the OriginalTextAnnotation, which is
        //specified by HasContext.
        if(t.value().contains(MorphoFeatureSpecification.MORPHO_MARK)) {
          String[] toks = t.value().split(MorphoFeatureSpecification.MORPHO_MARK);
          if(toks.length != 2)
            System.err.printf("%s: Word contains malformed morph annotation: %s%n",this.getClass().getName(),t.value());

          else if(t.label() instanceof CoreLabel) {
            ((CoreLabel) t.label()).setValue(toks[0].trim().intern());
            ((CoreLabel) t.label()).setWord(toks[0].trim().intern());

            Pair<String,String> lemmaMorph = MorphoFeatureSpecification.splitMorphString(toks[0], toks[1]);
            String lemma = lemmaMorph.first();
            String morphAnalysis = lemmaMorph.second();
            if (lemma.equals(toks[0])) {
              ((CoreLabel) t.label()).setOriginalText(toks[1].trim().intern());
            } else {
              // TODO(speneg): Does this help?
              String newLemma = lexMapper.map(null, lemma);
              if (newLemma == null || newLemma.trim().length() == 0) {
                newLemma = lemma;
              }
              String newMorphAnalysis = newLemma + MorphoFeatureSpecification.LEMMA_MARK + morphAnalysis;
              ((CoreLabel) t.label()).setOriginalText(newMorphAnalysis.intern());
            }

          } else {
            System.err.printf("%s: Cannot store morph analysis in non-CoreLabel: %s%n",this.getClass().getName(),t.label().getClass().getName());
          }
        }

      } else if (t.isPreTerminal()) {

        if (t.value() == null || t.value().equals("")) {
          System.err.printf("%s: missing tag for\n%s\n",this.getClass().getName(),t.pennString());
        } else if(t.label() instanceof HasTag) {
          ((HasTag) t.label()).setTag(t.value());
        }

      } else { //Phrasal nodes

        // there are some nodes "/" missing preterminals.  We'll splice in a tag for these.
        int nk = t.numChildren();
        List<Tree> newKids = new ArrayList<Tree>(nk);
        for (int j = 0; j < nk; j++) {
          Tree child = t.getChild(j);
          if (child.isLeaf()) {
            System.err.printf("%s: Splicing in DUMMYTAG for%n%s%n",this.getClass().getName(),t.toString());
            newKids.add(tf.newTreeNode("DUMMYTAG", Collections.singletonList(child)));

          } else {
            newKids.add(child);
          }
        }
        t.setChildren(newKids);
      }
    }//Every node in the tree has now been processed

    //
    // Additional processing for specific phrasal annotations
    //

    // special global coding for moving PRD annotation from constituent to verb tag.
    if (markPRDverb) {
      TregexMatcher m = prdVerbPattern.matcher(tree);
      Tree match = null;
      while (m.find()) {
        if (m.getMatch() != match) {
          match = m.getMatch();
          match.label().setValue(match.label().value() + "-PRDverb");
          Tree prd = m.getNode("prd");
          prd.label().setValue(super.normalizeNonterminal(prd.label().value()));
        }
      }
    }

    //Mark *only* subjects in verb-initial clauses
    if(retainNPSbj) {
      TregexMatcher m = npSbjPattern.matcher(tree);
      while (m.find()) {
        Tree match = m.getMatch();
        match.label().setValue("NP");
      }
    }

    if (tree.isPreTerminal()) {
      // The whole tree is a bare tag: bad!
      String val = tree.label().value();
      if (val.equals("CC") || val.startsWith("PUNC") || val.equals("CONJ")) {
        System.err.printf("%s: Bare tagged word being wrapped in FRAG\n%s\n",this.getClass().getName(),tree.pennString());
        tree = tf.newTreeNode("FRAG", Collections.singletonList(tree));
      } else {
        System.err.printf("%s: Bare tagged word\n%s\n",this.getClass().getName(),tree.pennString());
      }
    }

    //Add start symbol so that the root has only one sub-state. Escape any enclosing brackets.
    //If the "tree" consists entirely of enclosing brackets e.g. ((())) then this method
    //will return null. In this case, readers e.g. PennTreeReader will try to read the next tree.
    while(tree != null && (tree.value() == null || tree.value().equals("")) && tree.numChildren() <= 1)
      tree = tree.firstChild();

    if(tree != null && !tree.value().equals(rootLabel))
      tree = tf.newTreeNode(rootLabel, Collections.singletonList(tree));

    return tree;
  }


  /**
   * Remove traces and pronoun deletion markers.
   */
  public static class ArabicEmptyFilter implements Filter<Tree> {

    private static final long serialVersionUID = 7417844982953945964L;

    public boolean accept(Tree t) {
      // Pronoun deletions
      if(t.isPreTerminal() && (t.value().equals("PRON_1S") || t.value().equals("PRP")) &&
          (t.firstChild().value().equals("nullp") || t.firstChild().value().equals("نللة") || t.firstChild().value().equals("-~a")))
        return false;

      // Traces
      else if(t.isPreTerminal() && t.value() != null && t.value().equals("-NONE-"))
        return false;

      return true;
    }
  }

  private static final long serialVersionUID = -1592231121068698494L;
}
