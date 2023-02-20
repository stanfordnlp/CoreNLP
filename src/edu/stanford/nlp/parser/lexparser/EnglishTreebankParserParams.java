// Stanford Parser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002 - 2014 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/lex-parser.html

package edu.stanford.nlp.parser.lexparser;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Parser parameters for the Penn English Treebank (WSJ, Brown, Switchboard).
 *
 * @author Roger Levy
 * @author Christopher Manning
 * @version 03/05/2003
 */

public class EnglishTreebankParserParams extends AbstractTreebankParserParams  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(EnglishTreebankParserParams.class);

  protected class EnglishSubcategoryStripper implements TreeTransformer {

    protected TreeFactory tf = new LabeledScoredTreeFactory();

    @Override
    public Tree transformTree(Tree tree) {
      Label lab = tree.label();
      String s = lab.value();
      String tag = null;
      if (lab instanceof HasTag) {
        tag = ((HasTag) lab).tag();
      }
      if (tree.isLeaf()) {
        Tree leaf = tf.newLeaf(lab);
        leaf.setScore(tree.score());
        return leaf;
      } else if (tree.isPhrasal()) {
        if (englishTest.retainADVSubcategories && s.contains("-ADV")) {
          s = tlp.basicCategory(s);
          s += "-ADV";
        } else if (englishTest.retainTMPSubcategories && s.contains("-TMP")) {
          s = tlp.basicCategory(s);
          s += "-TMP";
        } else if (englishTest.retainNPTMPSubcategories && s.startsWith("NP-TMP")) {
          s = "NP-TMP";
        } else {
          s = tlp.basicCategory(s);
        }
        // remove the extra NPs inserted in the splitBaseNP == Collins option
        if (englishTrain.splitBaseNP == 2 &&
            s.equals("NP")) {
          Tree[] kids = tree.children();
          if (kids.length == 1 &&
              tlp.basicCategory(kids[0].value()).equals("NP")) {
            // go through kidkids here so as to keep any annotation on me.
            List<Tree> kidkids = new ArrayList<>();
            for (int cNum = 0; cNum < kids[0].children().length; cNum++) {
              Tree child = kids[0].children()[cNum];
              Tree newChild = transformTree(child);
              if (newChild != null) {
                kidkids.add(newChild);
              }
            }
            CategoryWordTag myLabel = new CategoryWordTag(lab);
            myLabel.setCategory(s);
            return tf.newTreeNode(myLabel, kidkids);
          }
        }
        // remove the extra POSSPs inserted by restructurePossP
        if (englishTrain.splitPoss == 2 &&
            s.equals("POSSP")) {
          Tree[] kids = tree.children();
          List<Tree> newkids = new ArrayList<>();
          for (int j = 0; j < kids.length - 1; j++) {
            for (int cNum = 0; cNum < kids[j].children().length; cNum++) {
              Tree child = kids[0].children()[cNum];
              Tree newChild = transformTree(child);
              if (newChild != null) {
                newkids.add(newChild);
              }
            }
          }
          Tree finalChild = transformTree(kids[kids.length - 1]);
          newkids.add(finalChild);
          CategoryWordTag myLabel = new CategoryWordTag(lab);
          myLabel.setCategory("NP");
          return tf.newTreeNode(myLabel, newkids);
        }
      } else { // preterminal
        s = tlp.basicCategory(s);
        if (tag != null) {
          tag = tlp.basicCategory(tag);
        }
      }
      List<Tree> children = new ArrayList<>();
      for (int cNum = 0; cNum < tree.numChildren(); cNum++) {
        Tree child = tree.getChild(cNum);
        Tree newChild = transformTree(child);
        if (newChild != null) {
          children.add(newChild);
        }
      }
      if (children.isEmpty()) {
        return null;
      }
      CategoryWordTag newLabel = new CategoryWordTag(lab);
      newLabel.setCategory(s);
      if (tag != null) {
        newLabel.setTag(tag);
      }
      Tree node = tf.newTreeNode(newLabel, children);
      node.setScore(tree.score());
      return node;
    }

  } // end class EnglishSubcategoryStripper


  public EnglishTreebankParserParams() {
    super(new PennTreebankLanguagePack());
    headFinder = new ModCollinsHeadFinder(tlp);
  }

  private HeadFinder headFinder;

  private final EnglishTrain englishTrain = new EnglishTrain();

  private final EnglishTest englishTest = new EnglishTest();

  @Override
  public HeadFinder headFinder() {
    return headFinder;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    if (generateOriginalDependencies) {
      return new SemanticHeadFinder(treebankLanguagePack(), !englishTest.makeCopulaHead);
    } else {
      return new UniversalSemanticHeadFinder(treebankLanguagePack(), !englishTest.makeCopulaHead);
    }
  }


  /**
   * Allows you to read in trees from the source you want.  It's the
   * responsibility of treeReaderFactory() to deal properly with character-set
   * encoding of the input.  It also is the responsibility of tr to properly
   * normalize trees.
   */
  @Override
  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory());
  }


  /**
   * Allows you to read in trees from the source you want.  It's the
   * responsibility of treeReaderFactory() to deal properly with character-set
   * encoding of the input.  It also is the responsibility of tr to properly
   * normalize trees.
   */
  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory());
  }


  /**
   * Makes appropriate TreeReaderFactory with all options specified
   */
  @Override
  public TreeReaderFactory treeReaderFactory() {
    return in -> new PennTreeReader(in, new LabeledScoredTreeFactory(), new NPTmpRetainingTreeNormalizer(englishTrain.splitTMP, englishTrain.splitSGapped == 5, englishTrain.leaveItAll, englishTrain.splitNPADV >= 1, headFinder()));
  }


  /**
   * returns a MemoryTreebank appropriate to the testing treebank source
   */
  @Override
  public MemoryTreebank testMemoryTreebank() {
    return new MemoryTreebank(in -> new PennTreeReader(in, new LabeledScoredTreeFactory(), new BobChrisTreeNormalizer(tlp)));
  }

  /**
   * The tree transformer used to produce trees for evaluation.  It will
   * be applied both to the parser output and the gold tree.
   */
  @Override
  public AbstractCollinizer collinizer() {
    return new TreeCollinizer(tlp, true, englishTrain.splitBaseNP == 2, englishTrain.collapseWhCategories);
  }

  @Override
  public AbstractCollinizer collinizerEvalb() {
    return new TreeCollinizer(tlp, true, englishTrain.splitBaseNP == 2, englishTrain.collapseWhCategories);
  }

  /**
   * contains Treebank-specific (but not parser-specific) info such
   * as what is punctuation, and also information about the structure
   * of labels
   */
  @Override
  public TreebankLanguagePack treebankLanguagePack() {
    return tlp;
  }

  @Override
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    if(op.lexOptions.uwModelTrainer == null) {
      //use default unknown word model for English
      op.lexOptions.uwModelTrainer = "edu.stanford.nlp.parser.lexparser.EnglishUnknownWordModelTrainer";
    }
    return new BaseLexicon(op, wordIndex, tagIndex);
  }


  // Automatically generated by SisterAnnotationStats -- preferably don't edit
  private static final String[] sisterSplit1 = {"ADJP=l=VBD", "ADJP=l=VBP", "NP=r=RBR", "PRN=r=.", "ADVP=l=PP", "PP=l=JJ", "PP=r=NP", "SBAR=l=VB", "PP=l=VBG", "ADJP=r=,", "ADVP=r=.", "ADJP=l=VB", "FRAG=l=FRAG", "FRAG=r=:", "PP=r=,", "ADJP=l=,", "FRAG=r=FRAG", "FRAG=l=:", "PRN=r=VP", "PP=l=RB", "S=l=ADJP", "SBAR=l=VBN", "NP=r=NX", "SBAR=l=VBZ", "SBAR=l=ADVP", "QP=r=JJ", "SBAR=l=PP", "SBAR=l=ADJP", "NP=r=VBG", "VP=r=:", "VP=l=ADJP", "SBAR=l=VBP", "ADVP=r=NP", "PP=l=VB", "VP=r=PP", "ADJP=r=SBAR", "NP=r=JJR", "SBAR=l=NN", "S=l=RB", "S=l=NNS", "S=r=SBAR", "S=l=WHPP", "VP=l=:", "ADVP=l=NP", "ADVP=r=PP", "ADJP=l=JJ", "NP=r=VBN", "NP=l=PRN", "VP=r=S", "NP=r=NNPS", "NX=r=NX", "ADJP=l=PRP$", "SBAR=l=CC", "SBAR=l=S", "S=l=PRT", "ADVP=l=VB", "ADVP=r=JJ", "NP=l=DT"};
  private static final String[] sisterSplit2 = {"S=r=PP", "NP=r=JJS", "ADJP=r=NNP", "NP=l=PRT", "ADJP=r=PP", "ADJP=l=VBZ", "PP=r=VP", "NP=r=CD", "ADVP=l=IN", "ADVP=l=,", "ADJP=r=JJ", "ADVP=l=VBD", "PP=r=.", "S=l=ADVP", "S=l=DT", "PP=l=NP", "VP=l=PRN", "NP=r=IN", "NP=r=``"};
  private static final String[] sisterSplit3 = {"PP=l=VBD", "ADJP=r=NNS", "S=l=:", "NP=l=ADVP", "NP=r=PRN", "NP=r=-RRB-", "NP=l=-LRB-", "NP=l=JJ", "SBAR=r=.", "S=r=:", "ADVP=r=VP", "NP=l=RB", "NP=r=RB", "S=l=VBP", "SBAR=r=,", "VP=r=,", "PP=r=PP", "NP=r=S", "ADJP=l=NP", "VP=l=VBG", "PP=l=PP"};
  private static final String[] sisterSplit4 = {"VP=l=NP", "NP=r=NN", "NP=r=VP", "VP=r=.", "NP=r=PP", "VP=l=TO", "VP=l=MD", "NP=r=,", "NP=r=NP", "NP=r=.", "NP=l=IN", "NP=l=NP", "VP=l=,", "VP=l=S", "NP=l=,", "VP=l=VBZ", "S=r=.", "NP=r=NNS", "S=l=IN", "NP=r=JJ", "NP=r=NNP", "VP=l=VBD", "S=l=WHNP", "VP=r=NP", "VP=l=''", "VP=l=VBP", "NP=l=:", "S=r=,", "VP=l=``", "VP=l=VB", "NP=l=S", "NP=l=VP", "NP=l=VB", "NP=l=VBD", "NP=r=SBAR", "NP=r=:", "VP=l=PP", "NP=l=VBZ", "NP=l=CC", "NP=l=''", "S=r=NP", "S=r=S", "S=l=VBN", "NP=l=``", "ADJP=r=NN", "S=r=VP", "NP=r=CC", "VP=l=RB", "S=l=S", "S=l=NP", "NP=l=TO", "S=l=,", "S=l=VBD", "S=r=''", "S=l=``", "S=r=CC", "PP=l=,", "S=l=CC", "VP=l=CC", "ADJP=l=DT", "NP=l=VBG", "VP=r=''", "SBAR=l=NP", "VP=l=VP", "NP=l=PP", "S=l=VB", "SBAR=l=VBD", "VP=l=ADVP", "VP=l=VBN", "NP=r=''", "VP=l=SBAR", "SBAR=l=,", "S=l=WHADVP", "VP=r=VP", "NP=r=ADVP", "QP=r=NNS", "NP=l=VBP", "S=l=VBZ", "NP=l=VBN", "S=l=PP", "VP=r=CC", "NP=l=SBAR", "SBAR=r=NP", "S=l=VBG", "SBAR=r=VP", "NP=r=ADJP", "S=l=JJ", "S=l=NN", "QP=r=NN"};

  @Override
  public String[] sisterSplitters() {
    switch (englishTrain.sisterSplitLevel) {
      case 1:
        return sisterSplit1;
      case 2:
        return sisterSplit2;
      case 3:
        return sisterSplit3;
      case 4:
        return sisterSplit4;
      default:
        return new String[0];
    }
  }

  /**
   * Returns a TreeTransformer appropriate to the Treebank which
   * can be used to remove functional tags (such as "-TMP") from
   * categories.
   */
  @Override
  public TreeTransformer subcategoryStripper() {
    return new EnglishSubcategoryStripper();
  }


  public static class EnglishTest implements Serializable {
    /* THESE OPTIONS ARE ENGLISH-SPECIFIC AND AFFECT ONLY TEST TIME */
    EnglishTest() {}
    boolean retainNPTMPSubcategories = false;
    boolean retainTMPSubcategories = false;
    boolean retainADVSubcategories = false;

    boolean makeCopulaHead = false;

    private static final long serialVersionUID = 183157656745674521L;

  }


  public static class EnglishTrain implements Serializable {
    /* THESE OPTIONS ARE ENGLISH-SPECIFIC AND AFFECT ONLY TRAIN TIME */
    EnglishTrain() {}

    /**
     * if true, leave all PTB (functional tag) annotations (bad)
     */
    public int leaveItAll = 0;

    /**
     * Annotate prepositions into subcategories.  Values:
     * 0 = no annotation
     * 1 = IN with a ^S.* parent (putative subordinating
     * conjunctions) marked differently from others (real prepositions). OK.
     * 2 = Annotate IN prepositions 3 ways: ^S.* parent, ^N.* parent or rest
     * (generally predicative ADJP, VP). Better than sIN=1.  Good.
     * 3 = Annotate prepositions 6 ways: real feature engineering. Great.
     * 4 = Refinement of 3: allows -SC under SINV, WHADVP for -T and no -SCC
     *     if the parent is an NP.
     * 5 = Like 4 but maps TO to IN in a "nominal" (N*, P*, A*) context.
     * 6 = 4, but mark V/A complement and leave noun ones unmarked instead.
     */
    public int splitIN = 0;

    /** Mark quote marks for single vs. double so don't get mismatched ones.
     */
    public boolean splitQuotes = false;

    /** Separate out sentence final punct. (. ! ?).  Doesn't help.
     */
    public boolean splitSFP = false;

    /**
     * Mark the nouns that are percent signs.  Slightly good.
     */
    public boolean splitPercent = false;

    /**
     * Mark phrases that are headed by %.
     * A value of 0 = do nothing, 1 = only NP, 2 = NP and ADJP,
     * 3 = NP, ADJP and QP, 4 = any phrase.
     */
    public int splitNPpercent = 0;

    /** Grand parent annotate RB to try to distinguish sentential ones and
     *  ones in places like NP post modifier (things like 'very' are already
     *  distinguished as their parent is ADJP).
     */
    public boolean tagRBGPA = false;

    /** Mark NNP words as to position in phrase (single, left, right, inside)
     *  or subcategorizes NNP(S) as initials or initial/final in NP.
     */
    public int splitNNP = 0;

    /**
     * Join pound with dollar.
     */
    public boolean joinPound = false;

    /**
     * Joint comparative and superlative adjective with positive.
     */
    public boolean joinJJ = false;

    /**
     * Join proper nouns with common nouns. This isn't to improve
     * performance, but because Genia doesn't use proper noun tags in
     * general.
     */
    public boolean joinNounTags = false;

    /**
     * A special test for "such" mainly ("such as Fred"). A wash, so omit
     */
    public boolean splitPPJJ = false;

    /**
     * Put a special tag on 'transitive adjectives' with NP complement, like
     * 'due May 15' -- it also catches 'such' in 'such as NP', which may
     * be a good.  Matches 658 times in 2-21 training corpus. Wash.
     */
    public boolean splitTRJJ = false;

    /**
     * Put a special tag on 'adjectives with complements'.  This acts as a
     * general subcat feature for adjectives.
     */
    public boolean splitJJCOMP = false;

    /**
     * Specially mark the comparative/superlative words: less, least,
     * more, most
     */
    public boolean splitMoreLess = false;

    /**
     * Mark "Intransitive" DT.  Good.
     */
    public boolean unaryDT = false;//true;
    /**
     * Mark "Intransitive" RB.  Good.
     */
    public boolean unaryRB = false;//true;
    /**
     * "Intransitive" PRP. Wash -- basically a no-op really.
     */
    public boolean unaryPRP = false;
    /**
     * Mark reflexive PRP words.
     */
    public boolean markReflexivePRP = false;
    /**
     * Mark "Intransitive" IN. Minutely negative.
     */
    public boolean unaryIN = false;

    /** Provide annotation of conjunctions.  Gives modest gains (numbers
     *  shown F1 increase with respect to goodPCFG in June 2005).  A value of
     *  1 annotates both "and" and "or" as "CC-C" (+0.29%),
     *  2 annotates "but" and "&amp;" separately (+0.17%),
     *  3 annotates just "and" (equalsIgnoreCase) (+0.11%),
     *  0 annotates nothing (+0.00%).
     */
    public int splitCC = 0;

    /**
     * Annotates forms of "not" specially as tag "NOT". BAD
     */
    public boolean splitNOT = false;
    /**
     * Split modifier (NP, AdjP) adverbs from others.
     * This does nothing if you're already doing tagPA.
     */
    public boolean splitRB = false;

    /**
     * Make special tags for forms of BE and HAVE (and maybe DO/HELP, etc.).
     * A value of 0 is do nothing.
     * A value of 1 is the basic form.  Positive PCFG effect,
     *   but neutral to negative in Factored, and impossible if you use gPA.
     * A value of 2 adds in "s" = "'s"
     * and delves further to disambiguate "'s" as BE or HAVE.  Theoretically
     * good, but no practical gains.
     * A value of 3 adds DO.
     * A value of 4 adds HELP (which also takes VB form complement) as DO.
     * A value of 5 adds LET (which also takes VB form complement) as DO.
     * A value of 6 adds MAKE (which also takes VB form complement) as DO.
     * A value of 7 adds WATCH, SEE (which also take VB form complement) as DO.
     * A value of 8 adds come, go, but not inflections (which colloquially
     *   can take a VB form complement) as DO.
     * A value of 9 adds GET as BE.
     * Differences are small. You get about 0.3 F1 by doing something; the best
     * appear to be 2 or 3 for sentence exact and 7 or 8 for LP/LR F1.
     */
    public int splitAux = 0;

    /**
     * Pitiful attempt at marking V* preterms with their surface subcat
     * frames.  Bad so far.
     */
    public boolean vpSubCat = false;
    /**
     * Attempt to record ditransitive verbs.  The value 0 means do nothing;
     * 1 records two or more NP or S* arguments, and 2 means to only record
     * two or more NP arguments (that aren't NP-TMP).
     * 1 gave neutral to bad results.
     */
    public int markDitransV = 0;

    /**
     * Add (head) tags to VPs.  An argument of
     * 0 = no head-subcategorization of VPs,
     * 1 = add head tags (anything, as given by HeadFinder),
     * 2 = add head tags, but collapse finite verb tags (VBP, VBD, VBZ, MD)
     *     together,
     * 3 = only annotate verbal tags, and collapse finite verb tags
     *     (annotation is VBF, TO, VBG, VBN, VB, or zero),
     * 4 = only split on categories of VBF, TO, VBG, VBN, VB, and map
     *     cases that are not headed by a verbal category to an appropriate
     *     category based on word suffix (ing, d, t, s, to) or to VB otherwise.
     * We usually use a value of 3; 2 or 3 is much better than 0.
     * See also {@code splitVPNPAgr}. If it is true, its effects override
     * any value set for this parameter.
     */
    public int splitVP = 0;

    /**
     * Put enough marking on VP and NP to permit "agreement".
     */
    public boolean splitVPNPAgr = false;

    /**
     * Mark S/SINV/SQ nodes according to verbal tag.  Meanings are:
     * 0 = no subcategorization.
     * 1 = mark with head tag
     * 2 = mark only -VBF if VBZ/VBD/VBP/MD tag
     * 3 = as 2 and mark -VBNF if TO/VBG/VBN/VB
     * 4 = as 2 but only mark S not SINV/SQ
     * 5 = as 3 but only mark S not SINV/SQ
     * Previously seen as bad.  Option 4 might be promising now.
     */
    public int splitSTag = 0;

    public boolean markContainedVP = false;

    public boolean splitNPPRP = false;

    /**
     * Verbal distance -- mark whether symbol dominates a verb (V*, MD).
     * Very good.
     */
    public int dominatesV = 0;

    /**
     * Verbal distance -- mark whether symbol dominates a preposition (IN)
     */
    public boolean dominatesI = false;

    /**
     * Verbal distance -- mark whether symbol dominates a conjunction (CC)
     */
    public boolean dominatesC = false;

    /**
     * Mark phrases which are conjunctions.
     * 0 = No marking
     * 1 = Any phrase with a CC daughter that isn't first or last.  Possibly marginally positive.
     * 2 = As 0 but also a non-marginal CONJP daughter.  In principle good, but no gains.
     * 3 = More like Charniak.  Not yet implemented.  Need to annotate _before_ annotate children!
     *     np or vp with two or more np/vp children, a comma, cc or conjp, and nothing else.
     */
    public int markCC = 0;

    /**
     * Mark specially S nodes with "gapped" subject (control, raising).
     * 1 is basic version.  2 is better mark S nodes with "gapped" subject.
     * 3 seems best on small training set, but all of these are too similar;
     * 4 can't be differentiated.
     * 5 is done on tree before empty splitting. (Bad!?)
     */
    public int splitSGapped = 0;

    /**
     * Mark "numeric NPs".  Probably bad?
     */
    public boolean splitNumNP = false;

    /**
     * Give a special tag to NPs which are possessive NPs (end in 's).
     * A value of 0 means do nothing, 1 means tagging possessive NPs with
     * "-P", 2 means restructure possessive NPs so that they introduce a
     * POSSP node that
     * takes as children the POS and a regularly structured NP.
     * I.e., recover standard good linguistic practice circa 1985.
     * This seems a good idea, but is almost a no-op (modulo fine points of
     * markovization), since the previous NP-P phrase already uniquely
     * captured what is now a POSSP.
     */
    public int splitPoss = 0;

    /**
     * Mark base NPs.  A value of 0 = no marking, 1 = marking
     * baseNP (ones which rewrite just as preterminals), and 2 = doing
     * Collins-style marking, where an extra NP node is inserted above a
     * baseNP, if it isn't
     * already in an NP over NP construction, as in Collins 1999.
     * <i>This option shouldn't really be in EnglishTrain since it's needed
     * at parsing time.  But we don't currently use it....</i>
     * A value of 1 is good.
     */
    public int splitBaseNP = 0;

    /**
     * Retain NP-TMP (or maybe PP-TMP) annotation.  Good.
     * The values for this parameter are defined in
     * NPTmpRetainingTreeNormalizer.
     */
    public int splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_NONE;

    /** Split SBAR nodes.
     *  1 = mark 'in order to' purpose clauses; this is actually a small and
     *  inconsistent part of what is marked SBAR-PRP in the treebank, which
     *  is mainly 'because' reason clauses.
     *  2 = mark all infinitive SBAR.
     *  3 = do 1 and 2.
     *  A value of 1 seems minutely positive; 2 and 3 seem negative.
     *  Also get 'in case Sfin', 'In order to', and on one occasion
     *  'in order that'
     */
    public int splitSbar = 0;

    /**
     * Retain NP-ADV annotation.  0 means strip "-ADV" annotation.  1 means to
     * retain it, and to percolate it down to a head tag providing it can
     * do it through a path of only NP nodes.
     */
    public int splitNPADV = 0;

    /**
     * Mark NP-NNP.  0 is nothing; 1 is only NNP head, 2 is NNP and NNPS
     * head; 3 is NNP or NNPS anywhere in local NP.  All bad!
     */
    public int splitNPNNP = 0;

    /**
     * 'Correct' tags to produce verbs in VPs, etc. where possible
     */
    public boolean correctTags = false;

    /**
     * Right edge has a phrasal node.  Bad?
     */
    public boolean rightPhrasal = false;

    /**
     * Set the support * KL cutoff level (1-4) for sister splitting
     * -- don't use it, as far as we can tell so far
     */
    public int sisterSplitLevel = 1;

    /**
     * Grand-parent annotate (root mark) VP below ROOT.  Seems negative.
     */
    public boolean gpaRootVP = false;

    /**
     * Change TO inside PP to IN.
     */
    public int makePPTOintoIN = 0;

    /** Collapse WHPP with PP, etc., in training and perhaps in evaluation.
     *  1 = collapse phrasal categories.
     *  2 = collapse POS categories.
     *  4 = restore them in output (not yet implemented)
     */
    public int collapseWhCategories = 0;

    public void display() {
      String englishParams = "Using EnglishTreebankParserParams" + " splitIN=" + splitIN + " sPercent=" + splitPercent + " sNNP=" + splitNNP + " sQuotes=" + splitQuotes + " sSFP=" + splitSFP + " rbGPA=" + tagRBGPA + " j#=" + joinPound + " jJJ=" + joinJJ + " jNounTags=" + joinNounTags + " sPPJJ=" + splitPPJJ + " sTRJJ=" + splitTRJJ + " sJJCOMP=" + splitJJCOMP + " sMoreLess=" + splitMoreLess + " unaryDT=" + unaryDT + " unaryRB=" + unaryRB + " unaryPRP=" + unaryPRP + " reflPRP=" + markReflexivePRP + " unaryIN=" + unaryIN + " sCC=" + splitCC + " sNT=" + splitNOT + " sRB=" + splitRB + " sAux=" + splitAux + " vpSubCat=" + vpSubCat + " mDTV=" + markDitransV + " sVP=" + splitVP + " sVPNPAgr=" + splitVPNPAgr + " sSTag=" + splitSTag + " mVP=" + markContainedVP + " sNP%=" + splitNPpercent + " sNPPRP=" + splitNPPRP + " dominatesV=" + dominatesV + " dominatesI=" + dominatesI + " dominatesC=" + dominatesC + " mCC=" + markCC + " sSGapped=" + splitSGapped + " numNP=" + splitNumNP + " sPoss=" + splitPoss + " baseNP=" + splitBaseNP + " sNPNNP=" + splitNPNNP + " sTMP=" + splitTMP + " sNPADV=" + splitNPADV + " cTags=" + correctTags + " rightPhrasal=" + rightPhrasal + " gpaRootVP=" + gpaRootVP + " splitSbar=" + splitSbar + " mPPTOiIN=" + makePPTOintoIN + " cWh=" + collapseWhCategories;
      log.info(englishParams);
    }

    private static final long serialVersionUID = 1831576434872643L;

  } // end class EnglishTrain

  private static final TreeFactory categoryWordTagTreeFactory =
    new LabeledScoredTreeFactory(new CategoryWordTagFactory());

  /**
   * This method does language-specific tree transformations such
   * as annotating particular nodes with language-relevant features.
   * Such parameterizations should be inside the specific
   * TreebankLangParserParams class.  This method is recursively
   * applied to each node in the tree (depth first, left-to-right),
   * so you shouldn't write this method to apply recursively to tree
   * members.  This method is allowed to (and in some cases does)
   * destructively change the input tree {@code t}. It changes both
   * labels and the tree shape.
   *
   * @param t The input tree (with non-language-specific annotation already
   *           done, so you need to strip back to basic categories)
   * @param root The root of the current tree (can be null for words)
   * @return The fully annotated tree node (with daughters still as you
   *           want them in the final result)
   */
  @Override
  public Tree transformTree(Tree t, Tree root) {
    if (t == null || t.isLeaf()) {
      return t;
    }

    Tree parent;
    String parentStr;
    String grandParentStr;
    if (root == null || t.equals(root)) {
      parent = null;
      parentStr = "";
    } else {
      parent = t.parent(root);
      parentStr = parent.label().value();
    }
    if (parent == null || parent.equals(root)) {
      grandParentStr = "";
    } else {
      Tree grandParent = parent.parent(root);
      grandParentStr = grandParent.label().value();
    }
    String baseParentStr = tlp.basicCategory(parentStr);
    String baseGrandParentStr = tlp.basicCategory(grandParentStr);

    CoreLabel lab = (CoreLabel) t.label();
    String word = lab.word();
    String tag = lab.tag();
    String baseTag = tlp.basicCategory(tag);
    String cat = lab.value();
    String baseCat = tlp.basicCategory(cat);

    if (t.isPreTerminal()) {
      if (englishTrain.correctTags) {
        if (baseParentStr.equals("NP")) {
          switch (baseCat) {
            case "IN":
              if (word.equalsIgnoreCase("a") || word.equalsIgnoreCase("that")) {
                cat = changeBaseCat(cat, "DT");
              } else if (word.equalsIgnoreCase("so") ||
                  word.equalsIgnoreCase("about")) {
                cat = changeBaseCat(cat, "RB");
              } else if (word.equals("fiscal") || word.equalsIgnoreCase("next")) {
                cat = changeBaseCat(cat, "JJ");
              }
              break;
            case "RB":
              if (word.equals("McNally")) {
                cat = changeBaseCat(cat, "NNP");
              } else if (word.equals("multifamily")) {
                cat = changeBaseCat(cat, "NN");
              } else if (word.equals("MORE")) {
                cat = changeBaseCat(cat, "JJR");
              } else if (word.equals("hand")) {
                cat = changeBaseCat(cat, "NN");
              } else if (word.equals("fist")) {
                cat = changeBaseCat(cat, "NN");
              }
              break;
            case "RP":
              if (word.equals("Howard")) {
                cat = changeBaseCat(cat, "NNP");
              } else if (word.equals("whole")) {
                cat = changeBaseCat(cat, "JJ");
              }
              break;
            case "JJ":
              if (word.equals("U.S.")) {
                cat = changeBaseCat(cat, "NNP");
              } else if (word.equals("ours")) {
                cat = changeBaseCat(cat, "PRP");
              } else if (word.equals("mine")) {
                cat = changeBaseCat(cat, "NN");
              } else if (word.equals("Sept.")) {
                cat = changeBaseCat(cat, "NNP");
              }
              break;
            case "NN":
              if (word.equals("Chapman") || word.equals("Jan.") || word.equals("Sept.") || word.equals("Oct.") || word.equals("Nov.") || word.equals("Dec.")) {
                cat = changeBaseCat(cat, "NNP");
              } else if (word.equals("members") || word.equals("bureaus") || word.equals("days") || word.equals("outfits") || word.equals("institutes") || word.equals("innings") || word.equals("write-offs") || word.equals("wines") || word.equals("trade-offs") || word.equals("tie-ins") || word.equals("thrips") || word.equals("1980s") || word.equals("1920s")) {
                cat = changeBaseCat(cat, "NNS");
              } else if (word.equals("this")) {
                cat = changeBaseCat(cat, "DT");
              }
              break;
            case ":":
              if (word.equals("'")) {
                cat = changeBaseCat(cat, "''");
              }
              break;
            case "NNS":
              if (word.equals("start-up") || word.equals("ground-handling") ||
                  word.equals("word-processing") || word.equals("T-shirt") ||
                  word.equals("co-pilot")) {
                cat = changeBaseCat(cat, "NN");
              } else if (word.equals("Sens.") || word.equals("Aichi")) {
                cat = changeBaseCat(cat, "NNP");  //not clear why Sens not NNPS
              }
              break;
            case "VBZ":
              if (word.equals("'s")) {
                cat = changeBaseCat(cat, "POS");
              } else if (!word.equals("kills")) { // a worse PTB error
                cat = changeBaseCat(cat, "NNS");
              }
              break;
            case "VBG":
              if (word.equals("preferred")) {
                cat = changeBaseCat(cat, "VBN");
              }
              break;
            case "VB":
              if (word.equals("The")) {
                cat = changeBaseCat(cat, "DT");
              } else if (word.equals("allowed")) {
                cat = changeBaseCat(cat, "VBD");
              } else if (word.equals("short") || word.equals("key") || word.equals("many") || word.equals("last") || word.equals("further")) {
                cat = changeBaseCat(cat, "JJ");
              } else if (word.equals("lower")) {
                cat = changeBaseCat(cat, "JJR");
              } else if (word.equals("Nov.") || word.equals("Jan.") || word.equals("Dec.") || word.equals("Tandy") || word.equals("Release") || word.equals("Orkem")) {
                cat = changeBaseCat(cat, "NNP");
              } else if (word.equals("watch") || word.equals("review") || word.equals("risk") || word.equals("realestate") || word.equals("love") || word.equals("experience") || word.equals("control") || word.equals("Transport") || word.equals("mind") || word.equals("term") || word.equals("program") || word.equals("gender") || word.equals("audit") || word.equals("blame") || word.equals("stock") || word.equals("run") || word.equals("group") || word.equals("affect") || word.equals("rent") || word.equals("show") || word.equals("accord") || word.equals("change") || word.equals("finish") || word.equals("work") || word.equals("schedule") || word.equals("influence") || word.equals("school") || word.equals("freight") || word.equals("growth") || word.equals("travel") || word.equals("call") || word.equals("autograph") || word.equals("demand") || word.equals("abuse") || word.equals("return") || word.equals("defeat") || word.equals("pressure") || word.equals("bank") || word.equals("notice") || word.equals("tax") || word.equals("ooze") || word.equals("network") || word.equals("concern") || word.equals("pit") || word.equals("contract") || word.equals("cash")) {
                cat = changeBaseCat(cat, "NN");
              }
              break;
            case "NNP":
              if (word.equals("Officials")) {
                cat = changeBaseCat(cat, "NNS");
              } else if (word.equals("Currently")) {
                cat = changeBaseCat(cat, "RB");
                // should change NP-TMP to ADVP-TMP here too!
              }
              break;
            case "PRP":
              if (word.equals("her") && parent.numChildren() > 1) {
                cat = changeBaseCat(cat, "PRP$");
              } else if (word.equals("US")) {
                cat = changeBaseCat(cat, "NNP");
              }
              break;
          }
        } else if (baseParentStr.equals("WHNP")) {
          if (baseCat.equals("VBP") && (word.equalsIgnoreCase("that"))) {
            cat = changeBaseCat(cat, "WDT");
          }
        } else if (baseParentStr.equals("UCP")) {
           if (word.equals("multifamily")) {
             cat = changeBaseCat(cat, "NN");
           }
        } else if (baseParentStr.equals("PRT")) {
          if (baseCat.equals("RBR") && word.equals("in")) {
            cat = changeBaseCat(cat, "RP");
          } else if (baseCat.equals("NNP") && word.equals("up")) {
            cat = changeBaseCat(cat, "RP");
          }
        } else if (baseParentStr.equals("PP")) {
          if (parentStr.equals("PP-TMP")) {
            if (baseCat.equals("RP")) {
              cat = changeBaseCat(cat, "IN");
            }
          }
          if (word.equals("in") && (baseCat.equals("RP") || baseCat.equals("NN"))) {
            cat = changeBaseCat(cat, "IN");
          } else if (baseCat.equals("RB")) {
            if (word.equals("for") || word.equals("After")) {
              cat = changeBaseCat(cat, "IN");
            }
          } else if (word.equals("if") && baseCat.equals("JJ")) {
            cat = changeBaseCat(cat, "IN");
          }
        } else if (baseParentStr.equals("VP")) {
          if (baseCat.equals("NNS")) {
            cat = changeBaseCat(cat, "VBZ");
          } else if (baseCat.equals("IN")) {
            switch (word) {
              case "complicated":
                cat = changeBaseCat(cat, "VBD");
                break;
              case "post":
                cat = changeBaseCat(cat, "VB");
                break;
              case "like":
                cat = changeBaseCat(cat, "VB");  // most are VB; odd VBP

                break;
              case "off":
                cat = changeBaseCat(cat, "RP");
                break;
            }
          } else if (baseCat.equals("NN")) {
            if (word.endsWith("ing")) {
              cat = changeBaseCat(cat, "VBG");
            } else if (word.equals("bid")) {
              cat = changeBaseCat(cat, "VBN");
            } else if (word.equals("are")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("lure")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("cost")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("agreed")) {
              cat = changeBaseCat(cat, "VBN");
            } else if (word.equals("restructure")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("rule")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("fret")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("retort")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("draft")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("will")) {
              cat = changeBaseCat(cat, "MD");
            } else if (word.equals("yield")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("lure")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("feel")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("institutes")) {
              cat = changeBaseCat(cat, "VBZ");
            } else if (word.equals("share")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("trade")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("beat")) {
              cat = changeBaseCat(cat, "VBN");
            } else if (word.equals("effect")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("speed")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("work")) {
              cat = changeBaseCat(cat, "VB");   // though also one VBP
            } else if (word.equals("act")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("drop")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("stand")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("push")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("service")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("set")) {
              cat = changeBaseCat(cat, "VBN");   // or VBD sometimes, sigh
            } else if (word.equals("appeal")) {
              cat = changeBaseCat(cat, "VBP");  // 2 VBP, 1 VB in train
            } else if (word.equals("mold")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("mean")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("reconfirm")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("land")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("point")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("rise")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("pressured")) {
              cat = changeBaseCat(cat, "VBN");
            } else if (word.equals("smell")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("pay")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("hum")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("shape")) {
              cat = changeBaseCat(cat, "VBP");
            } else if (word.equals("benefit")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("abducted")) {
              cat = changeBaseCat(cat, "VBN");
            } else if (word.equals("look")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("fare")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("change")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("farm")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("increase")) {
              cat = changeBaseCat(cat, "VB");
            } else if (word.equals("stem")) {
              cat = changeBaseCat(cat, "VB");
            // only done 200-700
            } else if (word.equals("rebounded")) {
              cat = changeBaseCat(cat, "VBD");
            } else if (word.equals("face")) {
              cat = changeBaseCat(cat, "VB");
            }
          } else if (baseCat.equals("NNP")) {
            switch (word) {
              case "GRAB":
                cat = changeBaseCat(cat, "VBP");
                break;
              case "mature":
                cat = changeBaseCat(cat, "VB");
                break;
              case "Face":
                cat = changeBaseCat(cat, "VBP");
                break;
              case "are":
                cat = changeBaseCat(cat, "VBP");
                break;
              case "Urging":
                cat = changeBaseCat(cat, "VBG");
                break;
              case "Finding":
                cat = changeBaseCat(cat, "VBG");
                break;
              case "say":
                cat = changeBaseCat(cat, "VBP");
                break;
              case "Added":
                cat = changeBaseCat(cat, "VBD");
                break;
              case "Adds":
                cat = changeBaseCat(cat, "VBZ");
                break;
              case "BRACED":
                cat = changeBaseCat(cat, "VBD");
                break;
              case "REQUIRED":
                cat = changeBaseCat(cat, "VBN");
                break;
              case "SIZING":
                cat = changeBaseCat(cat, "VBG");
                break;
              case "REVIEW":
                cat = changeBaseCat(cat, "VB");
                break;
              case "code-named":
                cat = changeBaseCat(cat, "VBN");
                break;
              case "Printed":
                cat = changeBaseCat(cat, "VBN");
                break;
              case "Rated":
                cat = changeBaseCat(cat, "VBN");
                break;
              case "FALTERS":
                cat = changeBaseCat(cat, "VBZ");
                break;
              case "Got":
                cat = changeBaseCat(cat, "VBN");
                break;
              case "JUMPING":
                cat = changeBaseCat(cat, "VBG");
                break;
              case "Branching":
                cat = changeBaseCat(cat, "VBG");
                break;
              case "Excluding":
                cat = changeBaseCat(cat, "VBG");
                break;
              case "OKing":
                cat = changeBaseCat(cat, "VBG");
                break;
            }
          } else if (baseCat.equals("POS")) {
            cat = changeBaseCat(cat, "VBZ");
          } else if (baseCat.equals("VBD")) {
            if (word.equals("heaves")) {
              cat = changeBaseCat(cat, "VBZ");
            }
          } else if (baseCat.equals("VB")) {
            if (word.equals("allowed") || word.equals("increased")) {
              cat = changeBaseCat(cat, "VBD");
            }
          } else if (baseCat.equals("VBN")) {
            if (word.equals("has")) {
              cat = changeBaseCat(cat, "VBZ");
            } else if (word.equals("grew") || word.equals("fell")) {
              cat = changeBaseCat(cat, "VBD");
            }
          } else if (baseCat.equals("JJ")) {
            if (word.equals("own")) {
              cat = changeBaseCat(cat, "VB");
              // a couple should actually be VBP, but at least verb is closer
            }
          } else if (word.equalsIgnoreCase("being")) {
            if (!cat.equals("VBG")) {
              cat = changeBaseCat(cat, "VBG");
            }
          } else if (word.equalsIgnoreCase("all")) {
            cat = changeBaseCat(cat, "RB");
          // The below two lines seem in principle good but don't actually
          // improve parser performance; they degrade it on 2200-2219
          // } else if (baseGrandParentStr.equals("NP") && baseCat.equals("VBD")) {
          //   cat = changeBaseCat(cat, "VBN");
          }
        } else if (baseParentStr.equals("S")) {
          if (word.equalsIgnoreCase("all")) {
            cat = changeBaseCat(cat, "RB");
          }
        } else if (baseParentStr.equals("ADJP")) {
          switch (baseCat) {
            case "UH":
              cat = changeBaseCat(cat, "JJ");
              break;
            case "JJ":
              if (word.equalsIgnoreCase("more")) {
                cat = changeBaseCat(cat, "JJR");
              }
              break;
            case "RB":
              if (word.equalsIgnoreCase("free")) {
                cat = changeBaseCat(cat, "JJ");
              } else if (word.equalsIgnoreCase("clear")) {
                cat = changeBaseCat(cat, "JJ");
              } else if (word.equalsIgnoreCase("tight")) {
                cat = changeBaseCat(cat, "JJ");
              } else if (word.equalsIgnoreCase("sure")) {
                cat = changeBaseCat(cat, "JJ");
              } else if (word.equalsIgnoreCase("particular")) {
                cat = changeBaseCat(cat, "JJ");
              }
              // most uses of hard/RB should be JJ but not hard put/pressed exx.
              break;
            case "VB":
              if (word.equalsIgnoreCase("stock")) {
                cat = changeBaseCat(cat, "NN");
              } else if (word.equalsIgnoreCase("secure")) {
                cat = changeBaseCat(cat, "JJ");
              }
              break;
          }
        } else if (baseParentStr.equals("QP")) {
          if (word.equalsIgnoreCase("about")) {
            cat = changeBaseCat(cat, "RB");
          } else if (baseCat.equals("JJ")) {
            if (word.equalsIgnoreCase("more")) {
              cat = changeBaseCat(cat, "JJR");
            // this isn't right for "as much as X" constructions!
            // } else if (word.equalsIgnoreCase("as")) {
            //   cat = changeBaseCat(cat, "RB");
            }
          }
        } else if (baseParentStr.equals("ADVP")) {
          if (baseCat.equals("EX")) {
            cat = changeBaseCat(cat, "RB");
          } else if (baseCat.equals("NN") && word.equalsIgnoreCase("that")) {
            cat = changeBaseCat(cat, "DT");
          } else if (baseCat.equals("NNP") && (word.endsWith("ly") ||
                                               word.equals("Overall"))) {
            cat = changeBaseCat(cat, "RB");

          // This should be a sensible thing to do, but hurts on 2200-2219
          // } else if (baseCat.equals("RP") && word.equalsIgnoreCase("around")) {
          //   cat = changeBaseCat(cat, "RB");
          }
        } else if (baseParentStr.equals("SBAR")) {
          if ((word.equalsIgnoreCase("that") || word.equalsIgnoreCase("because") || word.equalsIgnoreCase("while")) && !baseCat.equals("IN")) {
            cat = changeBaseCat(cat, "IN");
          } else if ((word.equals("Though") || word.equals("Whether")) && baseCat.equals("NNP")) {
            cat = changeBaseCat(cat, "IN");
          }
        } else if (baseParentStr.equals("SBARQ")) {
          if (baseCat.equals("S")) {
            if (word.equalsIgnoreCase("had")) {
              cat = changeBaseCat(cat, "SQ");
            }
          }
        } else if (baseCat.equals("JJS")) {
          if (word.equalsIgnoreCase("less")) {
            cat = changeBaseCat(cat, "JJR");
          }
        } else if (baseCat.equals("JJ")) {
          if (word.equalsIgnoreCase("%")) {
            // nearly all % are NN, a handful are JJ which we 'correct'
            cat = changeBaseCat(cat, "NN");
          } else if (word.equalsIgnoreCase("to")) {
            cat = changeBaseCat(cat, "TO");
          }
        } else if (baseCat.equals("VB")) {
          if (word.equalsIgnoreCase("even")) {
            cat = changeBaseCat(cat, "RB");
          }
        } else if (baseCat.equals(",")) {
          switch (word) {
            case "2":
              cat = changeBaseCat(cat, "CD");
              break;
            case "an":
              cat = changeBaseCat(cat, "DT");
              break;
            case "Wa":
              cat = changeBaseCat(cat, "NNP");
              break;
            case "section":
              cat = changeBaseCat(cat, "NN");
              break;
            case "underwriters":
              cat = changeBaseCat(cat, "NNS");
              break;
          }
        } else if (baseCat.equals("CD")) {
          if (word.equals("high-risk")) {
            cat = changeBaseCat(cat, "JJ");
          }
        } else if (baseCat.equals("RB")) {
          if (word.equals("for")) {
            cat = changeBaseCat(cat, "IN");
          }
        } else if (baseCat.equals("RP")) {
          if (word.equals("for")) {
            cat = changeBaseCat(cat, "IN");
          }
        } else if (baseCat.equals("NN")) {
          if (word.length() == 2 && word.charAt(1) == '.' && Character.isUpperCase(word.charAt(0))) {
            cat = changeBaseCat(cat, "NNP");
          } else if (word.equals("Lorillard")) {
            cat = changeBaseCat(cat, "NNP");
          }
        } else if (word.equals("for") || word.equals("at")) {
          if ( ! baseCat.equals("IN")) {
            // only non-prepositional taggings are mistaken
            cat = changeBaseCat(cat, "IN");
          }
        } else if (word.equalsIgnoreCase("and") && ! baseCat.equals("CC")) {
          cat = changeBaseCat(cat, "CC");
        } else if (word.equals("ago")) {
          if ( ! baseCat.equals("RB")) {
            cat = changeBaseCat(cat, "RB");
          }
        }
        // put correct value into baseCat for later processing!
        baseCat = tlp.basicCategory(cat);
      }
      if (englishTrain.makePPTOintoIN > 0 && baseCat.equals("TO")) {
        // CONJP is for "not to mention"
        if ( ! (baseParentStr.equals("VP") || baseParentStr.equals("CONJP") ||
                baseParentStr.startsWith("S"))) {
          if (englishTrain.makePPTOintoIN == 1) {
            cat = changeBaseCat(cat, "IN");
          } else {
            cat = cat + "-IN";
          }
        }
      }
      if (englishTrain.splitIN == 5 && baseCat.equals("TO")) {
        if (grandParentStr.charAt(0) == 'N' && (parentStr.charAt(0) == 'P' || parentStr.charAt(0) == 'A')) {
          // noun postmodifier PP (or so-called ADVP like "outside India")
          cat = changeBaseCat(cat, "IN") + "-N";
        }
      }
      if (englishTrain.splitIN == 1 && baseCat.equals("IN") && parentStr.charAt(0) == 'S') {
        cat = cat + "^S";
      } else if (englishTrain.splitIN == 2 && baseCat.equals("IN")) {
        if (parentStr.charAt(0) == 'S') {
          cat = cat + "^S";
        } else if (grandParentStr.charAt(0) == 'N') {
          cat = cat + "^N";
        }
      } else if (englishTrain.splitIN == 3 && baseCat.equals("IN")) {
        // 6 classes seems good!
        // but have played with joining first two, splitting out ADJP/ADVP,
        // and joining two SC cases
        if (grandParentStr.charAt(0) == 'N' && (parentStr.charAt(0) == 'P' || parentStr.charAt(0) == 'A')) {
          // noun postmodifier PP (or so-called ADVP like "outside India")
          cat = cat + "-N";
        } else if (parentStr.charAt(0) == 'Q' && (grandParentStr.charAt(0) == 'N' || grandParentStr.startsWith("ADJP"))) {
          // about, than, between, etc. in a QP preceding head of NP
          cat = cat + "-Q";
        } else if (grandParentStr.equals("S")) {
          // the distinction here shouldn't matter given parent annotation!
          if (baseParentStr.equals("SBAR")) {
            // sentential subordinating conj: although, if, until, as, while
            cat = cat + "-SCC";
          } else {
            // PP adverbial clause: among, in, for, after
            cat = cat + "-SC";
          }
        } else if (baseParentStr.equals("SBAR") || baseParentStr.equals("WHNP")) {
          // that-clause complement of VP or NP (or whether, if complement)
          // but also VP adverbial because, until, as, etc.
          cat = cat + "-T";
        }
        // all the rest under VP, PP, ADJP, ADVP, etc. are basic case
      } else if (englishTrain.splitIN >= 4 && englishTrain.splitIN <= 5 && baseCat.equals("IN")) {
        if (grandParentStr.charAt(0) == 'N' && (parentStr.charAt(0) == 'P' || parentStr.charAt(0) == 'A')) {
          // noun postmodifier PP (or so-called ADVP like "outside India")
          cat = cat + "-N";
        } else if (parentStr.charAt(0) == 'Q' && (grandParentStr.charAt(0) == 'N' || grandParentStr.startsWith("ADJP"))) {
          // about, than, between, etc. in a QP preceding head of NP
          cat = cat + "-Q";
        } else if (baseGrandParentStr.charAt(0) == 'S' &&
                   ! baseGrandParentStr.equals("SBAR")) {
          // the distinction here shouldn't matter given parent annotation!
          if (baseParentStr.equals("SBAR")) {
            // sentential subordinating conj: although, if, until, as, while
            cat = cat + "-SCC";
          } else if (!baseParentStr.equals("NP") && !baseParentStr.equals("ADJP")) {
            // PP adverbial clause: among, in, for, after
            cat = cat + "-SC";
          }
        } else if (baseParentStr.equals("SBAR") || baseParentStr.equals("WHNP") || baseParentStr.equals("WHADVP")) {
          // that-clause complement of VP or NP (or whether, if complement)
          // but also VP adverbial because, until, as, etc.
          cat = cat + "-T";
        }
        // all the rest under VP, PP, ADJP, ADVP, etc. are basic case
      } else if (englishTrain.splitIN == 6 && baseCat.equals("IN")) {
        if (grandParentStr.charAt(0) == 'V' || grandParentStr.charAt(0) == 'A') {
          cat = cat + "-V";
        } else if (grandParentStr.charAt(0) == 'N' && (parentStr.charAt(0) == 'P' || parentStr.charAt(0) == 'A')) {
          // noun postmodifier PP (or so-called ADVP like "outside India")
          // XXX experiment cat = cat + "-N";
        } else if (parentStr.charAt(0) == 'Q' && (grandParentStr.charAt(0) == 'N' || grandParentStr.startsWith("ADJP"))) {
          // about, than, between, etc. in a QP preceding head of NP
          cat = cat + "-Q";
        } else if (baseGrandParentStr.charAt(0) == 'S' &&
                   ! baseGrandParentStr.equals("SBAR")) {
          // the distinction here shouldn't matter given parent annotation!
          if (baseParentStr.equals("SBAR")) {
            // sentential subordinating conj: although, if, until, as, while
            cat = cat + "-SCC";
          } else if (!baseParentStr.equals("NP") && !baseParentStr.equals("ADJP")) {
            // PP adverbial clause: among, in, for, after
            cat = cat + "-SC";
          }
        } else if (baseParentStr.equals("SBAR") || baseParentStr.equals("WHNP") || baseParentStr.equals("WHADVP")) {
          // that-clause complement of VP or NP (or whether, if complement)
          // but also VP adverbial because, until, as, etc.
          cat = cat + "-T";
        }
        // all the rest under VP, PP, ADJP, ADVP, etc. are basic case
      }
      if (englishTrain.splitPercent && word.equals("%")) {
        cat += "-%";
      }
      if (englishTrain.splitNNP > 0 && baseCat.startsWith("NNP")) {
        if (englishTrain.splitNNP == 1) {
          if (baseCat.equals("NNP")) {
            if (parent.numChildren() == 1) {
              cat += "-S";
            } else if (parent.firstChild().equals(t)) {
              cat += "-L";
            } else if (parent.lastChild().equals(t)) {
              cat += "-R";
            } else {
              cat += "-I";
            }
          }
        } else if (englishTrain.splitNNP == 2) {
          if (word.matches("[A-Z]\\.?")) {
            cat = cat + "-I";
          } else if (firstOfSeveralNNP(parent, t)) {
            cat = cat + "-B";
          } else if (lastOfSeveralNNP(parent, t)) {
            cat = cat + "-E";
          }
        }
      }
      if (englishTrain.splitQuotes &&
          (word.equals("'") || word.equals("`"))) {
        cat += "-SG";
      }
      if (englishTrain.splitSFP && baseTag.equals(".")) {
        if (word.equals("?")) {
          cat += "-QUES";
        } else if (word.equals("!")) {
          cat += "-EXCL";
        }
      }
      if (englishTrain.tagRBGPA) {
          if (baseCat.equals("RB")) {
              cat = cat + "^" + baseGrandParentStr;
          }
      }
      if (englishTrain.joinPound && baseCat.equals("#")) {
        cat = changeBaseCat(cat, "$");
      }
      if (englishTrain.joinNounTags) {
        if (baseCat.equals("NNP")) {
          cat = changeBaseCat(cat, "NN");
        } else if (baseCat.equals("NNPS")) {
          cat = changeBaseCat(cat, "NNS");
        }
      }
      if (englishTrain.joinJJ && cat.startsWith("JJ")) {
        cat = changeBaseCat(cat, "JJ");
      }
      if (englishTrain.splitPPJJ && cat.startsWith("JJ") && parentStr.startsWith("PP")) {
        cat = cat + "^S";
      }
      if (englishTrain.splitTRJJ && cat.startsWith("JJ") && (parentStr.startsWith("PP") || parentStr.startsWith("ADJP")) && headFinder().determineHead(parent) == t) {
        // look for NP right sister of head JJ -- if so transitive adjective
        Tree[] kids = parent.children();
        boolean foundJJ = false;
        int i = 0;
        for (; i < kids.length && !foundJJ; i++) {
          if (kids[i].label().value().startsWith("JJ")) {
            foundJJ = true;
          }
        }
        if (foundJJ) {
          for (int j = i; j < kids.length; j++) {
            if (kids[j].label().value().startsWith("NP")) {
              cat = cat + "^T";
              break;
            }
          }
        }
      }
      if (englishTrain.splitJJCOMP && cat.startsWith("JJ") && (parentStr.startsWith("PP") || parentStr.startsWith("ADJP")) && headFinder().determineHead(parent) == t) {
        Tree[] kids = parent.children();
        int i = 0;
        for (boolean foundJJ = false; i < kids.length && !foundJJ; i++) {
          if (kids[i].label().value().startsWith("JJ")) {
            foundJJ = true;
          }
        }
        for (int j = i; j < kids.length; j++) {
          String kid = tlp.basicCategory(kids[j].label().value());
          if ("S".equals(kid) || "SBAR".equals(kid) || "PP".equals(kid) || "NP".equals(kid)) {
            // there's a complement.
            cat = cat + "^CMPL";
            break;
          }
        }
      }
      if (englishTrain.splitMoreLess) {
        char ch = cat.charAt(0);
        if (ch == 'R' || ch == 'J' || ch == 'C') {
          // adverbs, adjectives and coordination -- what you'd expect
          if (word.equalsIgnoreCase("more") || word.equalsIgnoreCase("most") || word.equalsIgnoreCase("less") || word.equalsIgnoreCase("least")) {
            cat = cat + "-ML";
          }
        }
      }
      if (englishTrain.unaryDT && cat.startsWith("DT")) {
        if (parent.children().length == 1) {
          cat = cat + "^U";
        }
      }
      if (englishTrain.unaryRB && cat.startsWith("RB")) {
        if (parent.children().length == 1) {
          cat = cat + "^U";
        }
      }
      if (englishTrain.markReflexivePRP && cat.startsWith("PRP")) {
        if (word.equalsIgnoreCase("itself") || word.equalsIgnoreCase("themselves") || word.equalsIgnoreCase("himself") || word.equalsIgnoreCase("herself") || word.equalsIgnoreCase("ourselves") || word.equalsIgnoreCase("yourself") || word.equalsIgnoreCase("yourselves") || word.equalsIgnoreCase("myself") || word.equalsIgnoreCase("thyself")) {
          cat += "-SE";
        }
      }
      if (englishTrain.unaryPRP && cat.startsWith("PRP")) {
        if (parent.children().length == 1) {
          cat = cat + "^U";
        }
      }
      if (englishTrain.unaryIN && cat.startsWith("IN")) {
        if (parent.children().length == 1) {
          cat = cat + "^U";
        }
      }
      if (englishTrain.splitCC > 0 && baseCat.equals("CC")) {
        if (englishTrain.splitCC == 1 && (word.equals("and") || word.equals("or"))) {
          cat = cat + "-C";
        } else if (englishTrain.splitCC == 2) {
          if (word.equalsIgnoreCase("but")) {
            cat = cat + "-B";
          } else if (word.equals("&")) {
            cat = cat + "-A";
          }
        } else if (englishTrain.splitCC == 3 && word.equalsIgnoreCase("and")) {
          cat = cat + "-A";
        }
      }
      if (englishTrain.splitNOT && baseCat.equals("RB") && (word.equalsIgnoreCase("n't") || word.equalsIgnoreCase("not") || word.equalsIgnoreCase("nt"))) {
        cat = cat + "-N";
      } else if (englishTrain.splitRB && baseCat.equals("RB") && (baseParentStr.equals("NP") || baseParentStr.equals("QP") || baseParentStr.equals("ADJP"))) {
        cat = cat + "^M";
      }
      if (englishTrain.splitAux > 1 && (baseCat.equals("VBZ") || baseCat.equals("VBP") || baseCat.equals("VBD") || baseCat.equals("VBN") || baseCat.equals("VBG") || baseCat.equals("VB"))) {
        if (word.equalsIgnoreCase("'s") || word.equalsIgnoreCase("s")) {  // a few times the apostrophe is missing!
          Tree[] sisters = parent.children();
          int i = 0;
          for (boolean foundMe = false; i < sisters.length && !foundMe; i++) {
            if (sisters[i].label().value().startsWith("VBZ")) {
              foundMe = true;
            }
          }
          boolean annotateHave = false;  // VBD counts as an erroneous VBN!
          for (int j = i; j < sisters.length; j++) {
            if (sisters[j].label().value().startsWith("VP")) {
              for (Tree kid : sisters[j].children()) {
                if (kid.label().value().startsWith("VBN") || kid.label().value().startsWith("VBD")) {
                  annotateHave = true;
                }
              }
            }
          }
          if (annotateHave) {
            cat = cat + "-HV";
            // System.out.println("Went with HAVE for " + parent);
          } else {
            cat = cat + "-BE";
          }
        } else {
          if (word.equalsIgnoreCase("am") || word.equalsIgnoreCase("is") || word.equalsIgnoreCase("are") || word.equalsIgnoreCase("was") || word.equalsIgnoreCase("were") || word.equalsIgnoreCase("'m") || word.equalsIgnoreCase("'re") || word.equalsIgnoreCase("be") || word.equalsIgnoreCase("being") || word.equalsIgnoreCase("been") || word.equalsIgnoreCase("ai")) { // allow "ai n't"
            cat = cat + "-BE";
          } else if (word.equalsIgnoreCase("have") || word.equalsIgnoreCase("'ve") || word.equalsIgnoreCase("having") || word.equalsIgnoreCase("has") || word.equalsIgnoreCase("had") || word.equalsIgnoreCase("'d")) {
            cat = cat + "-HV";
          } else if (englishTrain.splitAux >= 3 &&
                     (word.equalsIgnoreCase("do") || word.equalsIgnoreCase("did") || word.equalsIgnoreCase("does") || word.equalsIgnoreCase("done") || word.equalsIgnoreCase("doing"))) {
            // both DO and HELP take VB form complement VP
            cat = cat + "-DO";
          } else if (englishTrain.splitAux >= 4 &&
                     (word.equalsIgnoreCase("help") || word.equalsIgnoreCase("helps") || word.equalsIgnoreCase("helped") || word.equalsIgnoreCase("helping"))) {
            // both DO and HELP take VB form complement VP
            cat = cat + "-DO";
          } else if (englishTrain.splitAux >= 5 &&
                     (word.equalsIgnoreCase("let") || word.equalsIgnoreCase("lets") || word.equalsIgnoreCase("letting"))) {
            // LET also takes VB form complement VP
            cat = cat + "-DO";
          } else if (englishTrain.splitAux >= 6 &&
                     (word.equalsIgnoreCase("make") || word.equalsIgnoreCase("makes") || word.equalsIgnoreCase("making") || word.equalsIgnoreCase("made"))) {
            // MAKE can also take VB form complement VP
            cat = cat + "-DO";
          } else if (englishTrain.splitAux >= 7 &&
                     (word.equalsIgnoreCase("watch") || word.equalsIgnoreCase("watches") || word.equalsIgnoreCase("watching") || word.equalsIgnoreCase("watched") || word.equalsIgnoreCase("see") || word.equalsIgnoreCase("sees") || word.equalsIgnoreCase("seeing") || word.equalsIgnoreCase("saw") || word.equalsIgnoreCase("seen"))) {
            // WATCH, SEE can also take VB form complement VP
            cat = cat + "-DO";
          } else if (englishTrain.splitAux >= 8 &&
                     (word.equalsIgnoreCase("go") || word.equalsIgnoreCase("come"))) {
            // go, come, but not inflections can also take VB form complement VP
            cat = cat + "-DO";
          } else if (englishTrain.splitAux >= 9 &&
                     (word.equalsIgnoreCase("get") || word.equalsIgnoreCase("gets") || word.equalsIgnoreCase("getting") || word.equalsIgnoreCase("got") || word.equalsIgnoreCase("gotten"))) {
            // GET also takes a VBN form complement VP
            cat = cat + "-BE";
          }
        }
      } else if (englishTrain.splitAux > 0 && (baseCat.equals("VBZ") || baseCat.equals("VBP") || baseCat.equals("VBD") || baseCat.equals("VBN") || baseCat.equals("VBG") || baseCat.equals("VB"))) {
        if (word.equalsIgnoreCase("is") || word.equalsIgnoreCase("am") || word.equalsIgnoreCase("are") || word.equalsIgnoreCase("was") || word.equalsIgnoreCase("were") || word.equalsIgnoreCase("'m") || word.equalsIgnoreCase("'re") || word.equalsIgnoreCase("'s") || // imperfect -- could be (ha)s
                word.equalsIgnoreCase("being") || word.equalsIgnoreCase("be") || word.equalsIgnoreCase("been")) {
          cat = cat + "-BE";
        }
        if (word.equalsIgnoreCase("have") || word.equalsIgnoreCase("'ve") || word.equalsIgnoreCase("having") || word.equalsIgnoreCase("has") || word.equalsIgnoreCase("had") || word.equalsIgnoreCase("'d")) {
          cat = cat + "-HV";
        }
      }
      if (englishTrain.collapseWhCategories != 0) {
        if ((englishTrain.collapseWhCategories & 1) !=0) {
          cat = cat.replaceAll("WH(NP|PP|ADVP|ADJP)", "$1");
        }
        if ((englishTrain.collapseWhCategories & 2) != 0) {
          cat = cat.replaceAll("WP", "PRP"); // does both WP and WP$ !!
          cat = cat.replaceAll("WDT", "DT");
          cat = cat.replaceAll("WRB", "RB");
        }
        if ((englishTrain.collapseWhCategories & 4) !=0) {
          cat = cat.replaceAll("WH(PP|ADVP|ADJP)", "$1"); // don't do NP, so it is preserved! Crucial.
        }
      }
      if (englishTrain.markDitransV > 0 && cat.startsWith("VB")) {
        cat += ditrans(parent);
      } else if (englishTrain.vpSubCat && cat.startsWith("VB")) {
        cat = cat + subCatify(parent);
      }
      // VITAL: update tag to be same as cat for when new node is created below
      tag = cat;
    } else {                       // that is, if (t.isPhrasal())
      Tree[] kids = t.children();

      if (baseCat.equals("VP")) {
        if (englishTrain.gpaRootVP) {
          if (tlp.isStartSymbol(baseGrandParentStr)) {
            cat = cat + "~ROOT";
          }
        }
        if (englishTrain.splitVPNPAgr) {
          // don't split on weirdo categories!
          // but do preserve agreement distinctions
          // note MD is like VBD -- any subject person/number okay
          switch (baseTag) {
            case "VBD":
            case "MD":
              cat = cat + "-VBF";
              break;
            case "VBZ":
            case "TO":
            case "VBG":
            case "VBP":
            case "VBN":
            case "VB":
              cat = cat + "-" + baseTag;
              break;
            default:
              log.info("XXXX Head of " + t + " is " + word + "/" + baseTag);
              break;
          }
        } else if (englishTrain.splitVP == 3 || englishTrain.splitVP == 4) {
          // don't split on weirdo categories but deduce
          if (baseTag.equals("VBZ") || baseTag.equals("VBD") || baseTag.equals("VBP") || baseTag.equals("MD")) {
            cat = cat + "-VBF";
          } else if (baseTag.equals("TO") || baseTag.equals("VBG") || baseTag.equals("VBN") || baseTag.equals("VB")) {
            cat = cat + "-" + baseTag;
          } else if (englishTrain.splitVP == 4) {
            String dTag = deduceTag(word);
            cat = cat + "-" + dTag;
          }
        } else if (englishTrain.splitVP == 2) {
          if (baseTag.equals("VBZ") || baseTag.equals("VBD") || baseTag.equals("VBP") || baseTag.equals("MD")) {
            cat = cat + "-VBF";
          } else {
            cat = cat + "-" + baseTag;
          }
        } else if (englishTrain.splitVP == 1) {
          cat = cat + "-" + baseTag;
        }
      }
      if (englishTrain.dominatesV > 0) {
        if (englishTrain.dominatesV == 2) {
          if (hasClausalV(t)) {
            cat = cat + "-v";
          }
        } else if (englishTrain.dominatesV == 3) {
          if (hasV(t.preTerminalYield()) &&
                ! baseCat.equals("WHPP") && ! baseCat.equals("RRC") &&
                ! baseCat.equals("QP") && ! baseCat.equals("PRT")) {
            cat = cat + "-v";
          }
        } else {
          if (hasV(t.preTerminalYield())) {
            cat = cat + "-v";
          }
        }
      }
      if (englishTrain.dominatesI && hasI(t.preTerminalYield())) {
        cat = cat + "-i";
      }
      if (englishTrain.dominatesC && hasC(t.preTerminalYield())) {
        cat = cat + "-c";
      }
      if (englishTrain.splitNPpercent > 0 && word.equals("%")) {
        if (baseCat.equals("NP") ||
            englishTrain.splitNPpercent > 1 && baseCat.equals("ADJP") ||
            englishTrain.splitNPpercent > 2 && baseCat.equals("QP") ||
            englishTrain.splitNPpercent > 3) {
          cat += "-%";
        }
      }
      if (englishTrain.splitNPPRP && baseTag.equals("PRP")) {
        cat += "-PRON";
      }
      if (englishTrain.splitSbar > 0 && baseCat.equals("SBAR")) {
        boolean foundIn = false;
        boolean foundOrder = false;
        boolean infinitive = baseTag.equals("TO");
        for (Tree kid : kids) {
          if (kid.isPreTerminal() && kid.children()[0].value().equalsIgnoreCase("in")) {
            foundIn = true;
          }
          if (kid.isPreTerminal() && kid.children()[0].value().equalsIgnoreCase("order")) {
            foundOrder = true;
          }
        }
        if (englishTrain.splitSbar > 1 && infinitive) {
          cat = cat + "-INF";
        }
        if ((englishTrain.splitSbar == 1 || englishTrain.splitSbar == 3) &&
            foundIn && foundOrder) {
          cat = cat + "-PURP";
        }
      }
      if (englishTrain.splitNPNNP > 0) {
        if (englishTrain.splitNPNNP == 1 && baseCat.equals("NP") && baseTag.equals("NNP")) {
          cat = cat + "-NNP";
        } else if (englishTrain.splitNPNNP == 2 && baseCat.equals("NP") && baseTag.startsWith("NNP")) {
          cat = cat + "-NNP";
        } else if (englishTrain.splitNPNNP == 3 && baseCat.equals("NP")) {
          boolean split = false;
          for (Tree kid : kids) {
            if (kid.value().startsWith("NNP")) {
              split = true;
              break;
            }
          }
          if (split) {
            cat = cat + "-NNP";
          }
        }
      }
      if (englishTrain.collapseWhCategories != 0) {
        if ((englishTrain.collapseWhCategories & 1) !=0) {
          cat = cat.replaceAll("WH(NP|PP|ADVP|ADJP)", "$1");
        }
        if ((englishTrain.collapseWhCategories & 2) != 0) {
          cat = cat.replaceAll("WP", "PRP"); // does both WP and WP$ !!
          cat = cat.replaceAll("WDT", "DT");
          cat = cat.replaceAll("WRB", "RB");
        }
        if ((englishTrain.collapseWhCategories & 4) !=0) {
          cat = cat.replaceAll("WH(PP|ADVP|ADJP)", "$1"); // don't do NP, so it is preserved! Crucial.
        }
      }
      if (englishTrain.splitVPNPAgr && baseCat.equals("NP") &&
          baseParentStr.startsWith("S")) {
        if (baseTag.equals("NNPS") || baseTag.equals("NNS")) {
          cat = cat + "-PL";
        } else if (word.equalsIgnoreCase("many") || word.equalsIgnoreCase("more") || word.equalsIgnoreCase("most") || word.equalsIgnoreCase("plenty")) {
          cat = cat + "-PL";
        } else if (baseTag.equals("NN") || baseTag.equals("NNP") || baseTag.equals("POS") || baseTag.equals("CD") || baseTag.equals("PRP$") || baseTag.equals("JJ") || baseTag.equals("EX") || baseTag.equals("$") || baseTag.equals("RB") || baseTag.equals("FW") || baseTag.equals("VBG") || baseTag.equals("JJS") || baseTag.equals("JJR")) {
        } else if (baseTag.equals("PRP")) {
          if (word.equalsIgnoreCase("they") || word.equalsIgnoreCase("them") || word.equalsIgnoreCase("we") || word.equalsIgnoreCase("us")) {
            cat = cat + "-PL";
          }
        } else if (baseTag.equals("DT") || baseTag.equals("WDT")) {
          if (word.equalsIgnoreCase("these") || word.equalsIgnoreCase("those") || word.equalsIgnoreCase("several")) {
            cat += "-PL";
          }
        } else {
          log.info("XXXX Head of " + t + " is " + word + "/" + baseTag);
        }
      }
      if (englishTrain.splitSTag > 0 &&
          (baseCat.equals("S") || (englishTrain.splitSTag <= 3 && (baseCat.equals("SINV") || baseCat.equals("SQ"))))) {
        if (englishTrain.splitSTag == 1) {
          cat = cat + "-" + baseTag;
        } else if (baseTag.equals("VBZ") || baseTag.equals("VBD") || baseTag.equals("VBP") || baseTag.equals("MD")) {
          cat = cat + "-VBF";
        } else if ((englishTrain.splitSTag == 3 || englishTrain.splitSTag == 5) &&
                   ((baseTag.equals("TO") || baseTag.equals("VBG") || baseTag.equals("VBN") || baseTag.equals("VB")))) {
          cat = cat + "-VBNF";
        }
      }
      if (englishTrain.markContainedVP && containsVP(t)) {
        cat = cat + "-vp";
      }
      if (englishTrain.markCC > 0) {
        // was: for (int i = 0; i < kids.length; i++) {
        // This second version takes an idea from Collins: don't count
        // marginal conjunctions which don't conjoin 2 things.
        for (int i = 1; i < kids.length - 1; i++) {
          String cat2 = kids[i].label().value();
          if (cat2.startsWith("CC")) {
            String word2 = kids[i].children()[0].value(); // get word
            // added this if since -acl03pcfg
            if (!(word2.equals("either") || word2.equals("both") || word2.equals("neither"))) {
              cat = cat + "-CC";
              break;
            } else {
              // log.info("XXX Found non-marginal either/both/neither");
            }
          } else if (englishTrain.markCC > 1 && cat2.startsWith("CONJP")) {
            cat = cat + "-CC";
            break;
          }
        }
      }
      if (englishTrain.splitSGapped == 1 && baseCat.equals("S") && !kids[0].label().value().startsWith("NP")) {
        // this doesn't handle predicative NPs right yet
        // to do that, need to intervene before tree normalization
        cat = cat + "-G";
      } else if (englishTrain.splitSGapped == 2 && baseCat.equals("S")) {
        // better version: you're gapped if there is no NP, or there is just
        // one (putatively predicative) NP with no VP, ADJP, NP, PP, or UCP
        boolean seenPredCat = false;
        int seenNP = 0;
        for (Tree kid : kids) {
          String cat2 = kid.label().value();
          if (cat2.startsWith("NP")) {
            seenNP++;
          } else if (cat2.startsWith("VP") || cat2.startsWith("ADJP") || cat2.startsWith("PP") || cat2.startsWith("UCP")) {
            seenPredCat = true;
          }
        }
        if (seenNP == 0 || (seenNP == 1 && !seenPredCat)) {
          cat = cat + "-G";
        }
      } else if (englishTrain.splitSGapped == 3 && baseCat.equals("S")) {
        // better version: you're gapped if there is no NP, or there is just
        // one (putatively predicative) NP with no VP, ADJP, NP, PP, or UCP
        // NEW: but you're not gapped if you have an S and CC daughter (coord)
        boolean seenPredCat = false;
        boolean seenCC = false;
        boolean seenS = false;
        int seenNP = 0;
        for (Tree kid : kids) {
          String cat2 = kid.label().value();
          if (cat2.startsWith("NP")) {
            seenNP++;
          } else if (cat2.startsWith("VP") || cat2.startsWith("ADJP") || cat2.startsWith("PP") || cat2.startsWith("UCP")) {
            seenPredCat = true;
          } else if (cat2.startsWith("CC")) {
            seenCC = true;
          } else if (cat2.startsWith("S")) {
            seenS = true;
          }
        }
        if ((!(seenCC && seenS)) && (seenNP == 0 || (seenNP == 1 && !seenPredCat))) {
          cat = cat + "-G";
        }
      } else if (englishTrain.splitSGapped == 4 && baseCat.equals("S")) {
        // better version: you're gapped if there is no NP, or there is just
        // one (putatively predicative) NP with no VP, ADJP, NP, PP, or UCP
        // But: not gapped if S(BAR)-NOM-SBJ constituent
        // But: you're not gapped if you have two /^S/ daughters
        boolean seenPredCat = false;
        boolean sawSBeforePredCat = false;
        int seenS = 0;
        int seenNP = 0;
        for (Tree kid : kids) {
          String cat2 = kid.label().value();
          if (cat2.startsWith("NP")) {
            seenNP++;
          } else if (cat2.startsWith("VP") || cat2.startsWith("ADJP") || cat2.startsWith("PP") || cat2.startsWith("UCP")) {
            seenPredCat = true;
          } else if (cat2.startsWith("S")) {
            seenS++;
            if (!seenPredCat) {
              sawSBeforePredCat = true;
            }
          }
        }
        if ((seenS < 2) && (!(sawSBeforePredCat && seenPredCat)) && (seenNP == 0 || (seenNP == 1 && !seenPredCat))) {
          cat = cat + "-G";
        }
      }
      if (englishTrain.splitNumNP && baseCat.equals("NP")) {
        boolean seenNum = false;
        for (Tree kid : kids) {
          String cat2 = kid.label().value();
          if (cat2.startsWith("QP") || cat2.startsWith("CD") || cat2.startsWith("$") || cat2.startsWith("#") || (cat2.startsWith("NN") && cat2.contains("-%"))) {
            seenNum = true;
            break;
          }
        }
        if (seenNum) {
          cat += "-NUM";
        }
      }
      if (englishTrain.splitPoss > 0 && baseCat.equals("NP") &&
          kids[kids.length - 1].label().value().startsWith("POS")) {
        if (englishTrain.splitPoss == 2) {
          // special case splice in a new node!  Do it all here
          Label labelBot;
          if (t.isPrePreTerminal()) {
            labelBot = new CategoryWordTag("NP^POSSP-B", word, tag);
          } else {
            labelBot = new CategoryWordTag("NP^POSSP", word, tag);
          }
          t.setLabel(labelBot);
          List<Tree> oldKids = t.getChildrenAsList();
          // could I use subList() here or is a true copy better?
          // lose the last child
          List<Tree> newKids = new ArrayList<>();
          for (int i = 0; i < oldKids.size() - 1; i++) {
            newKids.add(oldKids.get(i));
          }
          t.setChildren(newKids);
          cat = changeBaseCat(cat, "POSSP");
          Label labelTop = new CategoryWordTag(cat, word, tag);
          List<Tree> newerChildren = new ArrayList<>(2);
          newerChildren.add(t);
          // add POS dtr
          Tree last = oldKids.get(oldKids.size() - 1);
          if ( ! last.value().equals("POS^NP")) {
            log.info("Unexpected POS value (!): " + last);
          }
          last.setValue("POS^POSSP");
          newerChildren.add(last);
          return categoryWordTagTreeFactory.newTreeNode(labelTop, newerChildren);
        } else {
          cat = cat + "-P";
        }
      }
      if (englishTrain.splitBaseNP > 0 && baseCat.equals("NP") &&
          t.isPrePreTerminal()) {
        if (englishTrain.splitBaseNP == 2) {
          if (parentStr.startsWith("NP")) { // already got one above us
            cat = cat + "-B";
          } else {
            // special case splice in a new node!  Do it all here
            Label labelBot = new CategoryWordTag("NP^NP-B", word, tag);
            t.setLabel(labelBot);
            Label labelTop = new CategoryWordTag(cat, word, tag);
            List<Tree> newerChildren = new ArrayList<>(1);
            newerChildren.add(t);
            return categoryWordTagTreeFactory.newTreeNode(labelTop, newerChildren);
          }
        } else {
          cat = cat + "-B";
        }
      }
      if (englishTrain.rightPhrasal && rightPhrasal(t)) {
        cat = cat + "-RX";
      }
    }

    t.setLabel(new CategoryWordTag(cat, word, tag));
    return t;
  }


  private boolean containsVP(Tree t) {
    String cat = tlp.basicCategory(t.label().value());
    if (cat.equals("VP")) {
      return true;
    } else {
      for (Tree kid : t.children()) {
        if (containsVP(kid)) {
          return true;
        }
      }
      return false;
    }
  }

  private static boolean firstOfSeveralNNP(Tree parent, Tree t) {
    boolean firstIsT = false;
    int numNNP = 0;
    for (Tree kid : parent.children()) {
      if (kid.value().startsWith("NNP")) {
        if (t.equals(kid) && numNNP == 0) {
          firstIsT = true;
        }
        numNNP++;
      }
    }
    return numNNP > 1 && firstIsT;
  }

  private static boolean lastOfSeveralNNP(Tree parent, Tree t) {
    Tree last = null;
    int numNNP = 0;
    for (Tree kid : parent.children()) {
      if (kid.value().startsWith("NNP")) {
        numNNP++;
        last = kid;
      }
    }
    return numNNP > 1 && t.equals(last);
  }


  // quite heuristic, but not useless given tagging errors?
  private static String deduceTag(String w) {
    String word = w.toLowerCase();
    if (word.endsWith("ing")) {
      return "VBG";
    } else if (word.endsWith("d") || word.endsWith("t")) {
      return "VBN";
    } else if (word.endsWith("s")) {
      return "VBZ";
    } else if (word.equals("to")) {
      return "TO";
    } else {
      return "VB";
    }
  }

  private static boolean rightPhrasal(Tree t) {
    while (!t.isLeaf()) {
      t = t.lastChild();
      String str = t.label().value();
      if (str.startsWith("NP") || str.startsWith("PP") || str.startsWith("VP") || str.startsWith("S") || str.startsWith("Q") || str.startsWith("A")) {
        return true;
      }
    }
    return false;
  }


  private static String subCatify(Tree t) {
    StringBuilder sb = new StringBuilder("^a");
    boolean n = false;
    boolean s = false;
    boolean p = false;
    for (int i = 0; i < t.children().length; i++) {
      String childStr = t.children()[i].label().value();
      n = (n || childStr.startsWith("NP"));
      s = (s || childStr.startsWith("S"));
      p = (p || childStr.startsWith("PP"));
    }
    n = false;
    if (n) {
      sb.append('N');
    }
    if (p) {
      sb.append('P');
    }
    if (s) {
      sb.append('S');
    }
    return sb.toString();
  }


  private String ditrans(Tree t) {
    int n = 0;
    for (Tree kid : t.children()) {
      String childStr = kid.label().value();
      if (childStr.startsWith("NP") && !childStr.contains("-TMP")) {
        n++;
      } else if (englishTrain.markDitransV == 1 && childStr.startsWith("S")) {
        n++;
      }
    }
    if (n >= 2) {
      return "^2Arg";
    } else {
      return "";
    }
  }


  private String changeBaseCat(String cat, String newBaseCat) {
    int i = 1;  // not 0 in case tag is annotation introducing char
    int length = cat.length();
    for (; (i < length); i++) {
      if (tlp.isLabelAnnotationIntroducingCharacter(cat.charAt(i))) {
        break;
      }
    }
    if (i < length) {
      return newBaseCat + cat.substring(i);
    } else {
      return newBaseCat;
    }
  }


  /** This version doesn't count verbs in baseNPs: they're generally
   *  gerunds in compounds like "operating income".  It would also
   *  catch modal tagging mistakes like "May/MD 15".
   *  @param tree A tree to assess
   *  @return true if there is a verb or modal, not within a base NP
   */
  private static boolean hasClausalV(Tree tree) {
    // this is originally called only called on phrasal nodes
    if (tree.isPhrasal()) {
      if (tree.isPrePreTerminal() &&
          tree.value().startsWith("NP")) {
        return false;
      }
      Tree[] kids = tree.children();
      for (Tree t : kids) {
        if (hasClausalV(t)) {
          return true;
        }
      }
      return false;
    } else {
      String str = tree.value();
      return str.startsWith("VB") || str.startsWith("MD");
    }
  }

  private static boolean hasV(List<? extends Label> tags) {
    for (Label tag : tags) {
      String str = tag.toString();
      if (str.startsWith("V") || str.startsWith("MD")) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasI(List<? extends Label> tags) {
    for (Label tag : tags) {
      if (tag.toString().startsWith("I")) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasC(List<? extends Label> tags) {
    for (Label tag : tags) {
      if (tag.toString().startsWith("CC")) {
        return true;
      }
    }
    return false;
  }


  @Override
  public void display() {
    englishTrain.display();
  }


  /**
   * Set language-specific options according to flags.
   * This routine should process the option starting in args[i] (which
   * might potentially be several arguments long if it takes arguments).
   * It should return the index after the last index it consumed in
   * processing.  In particular, if it cannot process the current option,
   * the return value should be i.
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    // [CDM 2008: there are no generic options!] first, see if it's a generic option
    // int j = super.setOptionFlag(args, i);
    // if(i != j) return j;

    //lang. specific options
    if (args[i].equalsIgnoreCase("-splitIN")) {
      englishTrain.splitIN = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitPercent")) {
      englishTrain.splitPercent = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitQuotes")) {
      englishTrain.splitQuotes = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitSFP")) {
      englishTrain.splitSFP = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitNNP")) {
      englishTrain.splitNNP = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-rbGPA")) {
      englishTrain.tagRBGPA = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitTRJJ")) {
      englishTrain.splitTRJJ = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitJJCOMP")) {
      englishTrain.splitJJCOMP = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitMoreLess")) {
      englishTrain.splitMoreLess = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unaryDT")) {
      englishTrain.unaryDT = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unaryRB")) {
      englishTrain.unaryRB = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unaryIN")) {
      englishTrain.unaryIN = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-markReflexivePRP")) {
      englishTrain.markReflexivePRP = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitCC") && i + 1 < args.length) {
      englishTrain.splitCC = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitRB")) {
      englishTrain.splitRB = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitAux") && i+1 < args.length) {
      englishTrain.splitAux = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitSbar") && i+1 < args.length) {
      englishTrain.splitSbar = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitVP") && i + 1 < args.length) {
      englishTrain.splitVP = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitVPNPAgr")) {
      englishTrain.splitVPNPAgr = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-gpaRootVP")) {
      englishTrain.gpaRootVP = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-makePPTOintoIN")) {
      englishTrain.makePPTOintoIN = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-collapseWhCategories") && i + 1 < args.length) {
      englishTrain.collapseWhCategories = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitSTag")) {
      englishTrain.splitSTag = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitSGapped") && (i + 1 < args.length)) {
      englishTrain.splitSGapped = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitNPpercent") && (i+1 < args.length)) {
      englishTrain.splitNPpercent = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitNPPRP")) {
      englishTrain.splitNPPRP = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-dominatesV") && (i+1 < args.length)) {
      englishTrain.dominatesV = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-dominatesI")) {
      englishTrain.dominatesI = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-dominatesC")) {
      englishTrain.dominatesC = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-splitNPNNP") && (i+1 < args.length)) {
      englishTrain.splitNPNNP = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitTMP") && (i + 1 < args.length)) {
      englishTrain.splitTMP = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitNPADV") && (i+1 < args.length)) {
      englishTrain.splitNPADV = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-markContainedVP")) {
      englishTrain.markContainedVP = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-markDitransV") && (i+1 < args.length)) {
      englishTrain.markDitransV = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitPoss") && (i+1 < args.length)) {
      englishTrain.splitPoss = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-baseNP") && (i+1 < args.length)) {
      englishTrain.splitBaseNP = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-joinNounTags")) {
      englishTrain.joinNounTags = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-correctTags")) {
      englishTrain.correctTags = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noCorrectTags")) {
      englishTrain.correctTags = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-markCC") && (i + 1 < args.length)) {
      englishTrain.markCC = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-noAnnotations")) {
      englishTrain.splitVP = 0;
      englishTrain.splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_NONE;
      englishTrain.splitSGapped = 0;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-retainNPTMPSubcategories")) {
      englishTest.retainNPTMPSubcategories = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-retainTMPSubcategories")) {
      englishTest.retainTMPSubcategories = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-retainADVSubcategories")) {
      englishTest.retainADVSubcategories = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-leaveItAll") && (i + 1 < args.length)) {
      englishTrain.leaveItAll = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-headFinder") && (i + 1 < args.length)) {
      try {
        headFinder = (HeadFinder) Class.forName(args[i + 1]).getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        log.info("Error: Unable to load HeadFinder; default HeadFinder will be used.");
        e.printStackTrace();
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-makeCopulaHead")) {
      englishTest.makeCopulaHead = true;
      i += 1;
    } else if(args[i].equalsIgnoreCase("-originalDependencies")) {
      setGenerateOriginalDependencies(true);
      i += 1;
    } else if (args[i].equalsIgnoreCase("-acl03pcfg")) {
      englishTrain.splitIN = 3;
      englishTrain.splitPercent = true;
      englishTrain.splitPoss = 1;
      englishTrain.splitCC = 2;
      englishTrain.unaryDT = true;
      englishTrain.unaryRB = true;
      englishTrain.splitAux = 1;
      englishTrain.splitVP = 2;
      englishTrain.splitSGapped = 3;
      englishTrain.dominatesV = 1;
      englishTrain.splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_ACL03PCFG;
      englishTrain.splitBaseNP = 1;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-jenny")) {
      englishTrain.splitIN = 3;
      englishTrain.splitPercent = true;
      englishTrain.splitPoss = 1;
      englishTrain.splitCC = 2;
      englishTrain.unaryDT = true;
      englishTrain.unaryRB = true;
      englishTrain.splitAux = 1;
      englishTrain.splitVP = 2;
      englishTrain.splitSGapped = 3;
      englishTrain.dominatesV = 1;
      englishTrain.splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_ACL03PCFG;
      englishTrain.splitBaseNP = 1;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-linguisticPCFG")) {
      englishTrain.splitIN = 3;
      englishTrain.splitPercent = true;
      englishTrain.splitPoss = 1;
      englishTrain.splitCC = 2;
      englishTrain.unaryDT = true;
      englishTrain.unaryRB = true;
      englishTrain.splitAux = 2;
      englishTrain.splitVP = 3;
      englishTrain.splitSGapped = 4;
      englishTrain.dominatesV = 0;  // not for linguistic
      englishTrain.splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_ACL03PCFG;
      englishTrain.splitBaseNP = 1;
      englishTrain.splitMoreLess = true;
      englishTrain.correctTags = true;  // different from acl03pcfg
      i += 1;
    } else if (args[i].equalsIgnoreCase("-goodPCFG")) {
      englishTrain.splitIN = 4;  // different from acl03pcfg
      englishTrain.splitPercent = true;
      englishTrain.splitNPpercent = 0;  // no longer different from acl03pcfg
      englishTrain.splitPoss = 1;
      englishTrain.splitCC = 1;
      englishTrain.unaryDT = true;
      englishTrain.unaryRB = true;
      englishTrain.splitAux = 2;   // different from acl03pcfg
      englishTrain.splitVP = 3;   // different from acl03pcfg
      englishTrain.splitSGapped = 4;
      englishTrain.dominatesV = 1;
      englishTrain.splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_ACL03PCFG;
      englishTrain.splitNPADV = 1; // different from acl03pcfg
      englishTrain.splitBaseNP = 1;
      // englishTrain.splitMoreLess = true;   // different from acl03pcfg
      englishTrain.correctTags = true;  // different from acl03pcfg
      englishTrain.markDitransV = 2; // different from acl03pcfg
      i += 1;
    } else if (args[i].equalsIgnoreCase("-ijcai03")) {
      englishTrain.splitIN = 3;
      englishTrain.splitPercent = true;
      englishTrain.splitPoss = 1;
      englishTrain.splitCC = 2;
      englishTrain.unaryDT = false;
      englishTrain.unaryRB = false;
      englishTrain.splitAux = 0;
      englishTrain.splitVP = 2;
      englishTrain.splitSGapped = 4;
      englishTrain.dominatesV = 0;
      englishTrain.splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_ACL03PCFG;
      englishTrain.splitBaseNP = 1;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-goodFactored")) {
      englishTrain.splitIN = 3;
      englishTrain.splitPercent = true;
      englishTrain.splitPoss = 1;
      englishTrain.splitCC = 2;
      englishTrain.unaryDT = false;
      englishTrain.unaryRB = false;
      englishTrain.splitAux = 0;
      englishTrain.splitVP = 3;  // different from ijcai03
      englishTrain.splitSGapped = 4;
      englishTrain.dominatesV = 0;
      englishTrain.splitTMP = NPTmpRetainingTreeNormalizer.TEMPORAL_ACL03PCFG;
      englishTrain.splitBaseNP = 1;
      // BAD!! englishTrain.markCC = 1;  // different from ijcai03
      englishTrain.correctTags = true;  // different from ijcai03
      i += 1;
    }
    return i;
  }


  /** {@inheritDoc} */
  @Override
  public List<Word> defaultTestSentence() {
    List<Word> ret = new ArrayList<>();
    String[] sent = {"This", "is", "just", "a", "test", "."};
    for (String str : sent) {
      ret.add(new Word(str));
    }
    return ret;
  }

  @Override
  public List<GrammaticalStructure>
    readGrammaticalStructureFromFile(String filename)
  {
    try {
      if (generateOriginalDependencies) {
        return EnglishGrammaticalStructure.
            readCoNLLXGrammaticalStructureCollection(filename);
      } else {
        return UniversalEnglishGrammaticalStructure.
            readCoNLLXGrammaticalStructureCollection(filename);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  @Override
  public GrammaticalStructure getGrammaticalStructure(Tree t,
                                                      Predicate<String> filter,
                                                      HeadFinder hf) {
    if (generateOriginalDependencies) {
      return new EnglishGrammaticalStructure(t, filter, hf);
    } else {
      return new UniversalEnglishGrammaticalStructure(t, filter, hf);
    }
  }

  @Override
  public boolean supportsBasicDependencies() {
    return true;
  }

  private static final String[] RETAIN_TMP_ARGS = { "-retainTmpSubcategories" };

  @Override
  public String[] defaultCoreNLPFlags() {
    return RETAIN_TMP_ARGS;
  }

  public static void main(String[] args) {
    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    Treebank tb = tlpp.memoryTreebank();
    tb.loadPath(args[0]);
    for (Tree t : tb) {
      t.pennPrint();
    }
  }

  private static final long serialVersionUID = 4153878351331522581L;

}
