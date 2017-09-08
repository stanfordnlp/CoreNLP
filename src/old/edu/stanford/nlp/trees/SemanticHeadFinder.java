package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.HasTag;
import old.edu.stanford.nlp.ling.HasWord;
import old.edu.stanford.nlp.ling.Label;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


/**
 * Implements a 'semantic head' variant of the the HeadFinder found
 * in Michael Collins' 1999 thesis.
 * This version chooses the semantic head verb rather than the verb form
 * for cases with verbs.  Should remember auxiliaries to differentiate past
 * and passive, though.<p/>
 * <p/>
 * By default the SemanticHeadFinder uses a treatment of copula, i.e., a sentence like
 * "Bill is big" will be analyzed as <p/>
 * <p/>
 * <code>nsubj</code>(big, Bill) <br/>
 * <code>cop</code>(big, is) <p/>
 * <p/>
 * However WH-sentences do not receive this treatment.
 * A lot of special rules would be needed to make a distinction between
 * "Which country is she in?" and "Which country is big?"
 * Moreoverthe parser often gets wrong sentences like "How much was the check?":
 * "How much" is tagged as a WHNP, which complicates the rules...
 * (We cannot rely on looking for a WHNP and then see the structure in the SQ).
 * <p/>
 * Existential sentences are treated as follows:  <br/>
 * "There is a man" <br/>
 * <code>expl</code>(is, There) <br/>
 * <code>det</code>(man-4, a-3) <br/>
 * <code>nsubj</code>(is-2, man-4)<br/>
 *
 * @author John Rappaport
 * @author Marie-Catherine de Marneffe
 * @author Anna Rafferty
 */
public class SemanticHeadFinder extends ModCollinsHeadFinder {

  private static final boolean DEBUG = false;

  private HashSet<String> verbalAuxiliaries;
  private HashSet<String> copulars;
  private HashSet<String> verbalTags;

  private List<Tree> seen;

  public SemanticHeadFinder() {
    this(new PennTreebankLanguagePack(), true);
  }

  public SemanticHeadFinder(boolean cop) {
    this(new PennTreebankLanguagePack(), cop);
  }

  public SemanticHeadFinder(TreebankLanguagePack tlp, boolean cop) {
    super(tlp);
    ruleChanges();

    seen = new ArrayList<Tree>();
    // make a distinction between auxiliaries and copular verbs to
    // get the NP has semantic head in sentences like "Bill is an honest man".  (Added "sha" for "shan't" May 2009
    verbalAuxiliaries = new HashSet<String>();
    verbalAuxiliaries.addAll(Arrays.asList(new String[]{"will", "wo", "shall", "sha", "may", "might", "should", "would", "can", "could", "ca", "must", "has", "have", "had", "having", "be", "being", "been", "get", "gets", "getting", "got", "gotten", "do", "does", "did", "to", "'ve", "'d", "'ll"}));

    //copular verbs having an NP complement
    copulars = new HashSet<String>();
    if (cop) {
      copulars.addAll(Arrays.asList(new String[]{"be", "being", "Being", "am", "are", "is", "was", "were", "'m", "'re", "'s", "s", "seem", "seems", "seemed", "appear", "appears", "appeared", "stay", "stays", "stayed", "remain", "remains", "remained", "resemble", "resembles", "resembled", "become", "becomes", "became"}));
    }// a few times the apostrophe is missing on "'s"


    verbalTags = new HashSet<String>();
    // include Charniak tags so can do BLLIP right
    verbalTags.addAll(Arrays.asList(new String[]{"TO", "MD", "VB", "VBD", "VBP", "VBZ", "VBG", "VBN", "AUX", "AUXG"}));
  }

  //makes modifications of Collins' rules to better fit with semantic notions of heads
  private void ruleChanges() {
    //  NP: don't want a POS to be the head
    nonTerminalInfo.remove("NP");
    nonTerminalInfo.put("NP", new String[][]{{"rightdis", "NN", "NNP", "NNPS", "NNS", "NX", "JJR"}, {"left", "NP", "PRP"}, {"rightdis", "$", "ADJP", "PRN"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP", "DT", "WDT", "RBR", "ADVP"}, {"left", "POS"}});
    // WHNP clauses should have the same sort of head as an NP
    nonTerminalInfo.remove("WHNP");
    nonTerminalInfo.put("WHNP", new String[][]{{"left", "NP", "WP"}, {"rightdis", "NN", "NNP", "NNPS", "NNS", "NX", "POS", "JJR"}, {"rightdis", "$", "ADJP", "PRN"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP"}, {"left", "WHNP", "WHPP", "WHADJP", "WP$", "WP", "WDT"}});
    //WHADJP
    nonTerminalInfo.remove("WHADJP");
    nonTerminalInfo.put("WHADJP", new String[][]{{"left", "ADJP", "JJ", "WRB", "CC"}});
    // ADJP
    nonTerminalInfo.remove("ADJP");
    nonTerminalInfo.put("ADJP", new String[][]{{"left", "$", "JJ", "NNS", "NN", "QP", "VBN", "VBG", "ADJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB"}});
    // QP: we don't want the first CD to be the semantic head (e.g., "three billion": head should be "billion"), so we go from right to left
    nonTerminalInfo.remove("QP");
    nonTerminalInfo.put("QP", new String[][]{{"right", "$", "NNS", "NN", "CD", "JJ", "PDT", "DT", "IN", "RB", "NCD", "QP", "JJR", "JJS"}});
  
    // S, SBAR and SQ clauses should prefer the main verb as the head
    // S: "He considered him a friend" -> we want a friend to be the head
    nonTerminalInfo.remove("S");
    nonTerminalInfo.put("S", new String[][]{{"left", "VP", "S", "FRAG", "SBAR", "ADJP", "UCP", "TO"}, {"right", "NP"}});

    nonTerminalInfo.remove("SBAR");
    nonTerminalInfo.put("SBAR", new String[][]{{"left", "S", "SQ", "SINV", "SBAR", "FRAG", "WHNP", "WHPP", "WHADVP", "WHADJP", "IN", "DT"}});

    nonTerminalInfo.remove("SQ");
    nonTerminalInfo.put("SQ", new String[][]{{"left", "VP", "SQ", "VB", "VBZ", "VBD", "VBP", "MD", "AUX", "AUXG"}});

    // UCP take the first element as head
    nonTerminalInfo.remove("UCP");
    nonTerminalInfo.put("UCP", new String[][]{{"left"}});

    // CONJP: we want different heads for "but also" and "but not" and we don't want "not" to be the head in "not to mention"
    nonTerminalInfo.remove("CONJP");
    nonTerminalInfo.put("CONJP", new String[][]{{"right", "TO", "RB", "IN", "CC"}});

    // FRAG: crap rule needs to be change if you want to parse glosses
    nonTerminalInfo.remove("FRAG");
    nonTerminalInfo.put("FRAG", new String[][]{{"left", "ADJP", "ADVP", "FRAG", "S"}});

    // PP first word (especially in coordination of PPs)
    nonTerminalInfo.remove("PP");
    nonTerminalInfo.put("PP", new String[][]{{"right", "IN", "TO", "VBG", "VBN", "RP", "FW"}, {"left", "PP"}});

    // PRN: sentence first
    nonTerminalInfo.put("PRN", new String[][]{{"left", "VP", "S", "SINV", "SBAR", "NP", "ADJP", "PP", "ADVP", "INTJ", "WHNP", "NAC", "VBP", "JJ", "NN", "NNP"}});


    // add the consistuent XS (special node to add a layer in a QP tree)
    nonTerminalInfo.put("XS", new String[][]{{"right", "IN"}});

  }


  /**
   * Overwrite the postOperationFix method: a, b and c -> we want a to be the head
   */
  @Override
  protected int postOperationFix(int headIdx, Tree[] daughterTrees) {
    if (headIdx >= 2) {
      String prevLab = tlp.basicCategory(daughterTrees[headIdx - 1].value());
      if (prevLab.equals("CC") || prevLab.equals("CONJP")) {
        int newHeadIdx = headIdx - 2;
        Tree t = daughterTrees[newHeadIdx];
        while (newHeadIdx >= 0 && t.isPreTerminal() && tlp.isPunctuationTag(t.value())) {
          newHeadIdx--;
        }
        while (newHeadIdx >= 2 && tlp.isPunctuationTag(daughterTrees[newHeadIdx - 1].value())) {
          newHeadIdx = newHeadIdx - 2;
        }
        if (newHeadIdx >= 0) {
          headIdx = newHeadIdx;
        }
      }
    }
    return headIdx;
  }

  /**
   * Determine which daughter of the current parse tree is the
   * head.  It assumes that the daughters already have had their
   * heads determined.  Uses special rule for VPheads
   *
   * @param t The parse tree to examine the daughters of.
   *          This is assumed to never be a leaf
   * @return The parse tree that is the head
   */
  @Override
  protected Tree determineNonTrivialHead(Tree t, Tree parent) {
    String motherCat = tlp.basicCategory(t.label().value());

    if (DEBUG) {
      System.err.println("My parent is " + parent);
    }

    // do VPs with auxiliary as special case
    if ((motherCat.equals("VP") || motherCat.equals("SQ") || motherCat.equals("SINV")) & !seen.contains(t)) {
      Tree[] kids = t.children();
      // try to find if there is an auxiliary verb

      seen.add(t);

      if (DEBUG) {
        System.err.println("Semantic head finder: at VP");
        System.err.println("Class is " + t.getClass().getName());
        t.pennPrint(System.err);
        //System.err.println("hasVerbalAuxiliary = " + hasVerbalAuxiliary(kids, verbalAuxiliaries));
      }

      // looks for auxiliaries
      if (hasVerbalAuxiliary(kids, verbalAuxiliaries)) {
        // String[] how = new String[] {"left", "VP", "ADJP", "NP"};
        // Including NP etc seems okay for copular sentences but is
        // problematic for other auxiliaries, like 'he has an answer'
        // But maybe doing ADJP is fine!
        String[] how = new String[]{"left", "VP", "ADJP"};
        Tree pti = traverseLocate(kids, how, false);
        if (DEBUG) {
          System.err.println("Determined head is: " + pti);
        }
        if (pti != null) {
          return pti;
        } else {
          // System.err.println("------");
          // System.err.println("SemanticHeadFinder failed to reassign head for");
          // t.pennPrint(System.err);
          // System.err.println("------");
        }
      }

      // looks for copular verbs
      if (hasVerbalAuxiliary(kids, copulars) && !isExistential(t, parent) && !isWHQ(t, parent)) {
        String[] how;
        if (motherCat.equals("SQ")) {
          how = new String[]{"right", "VP", "ADJP", "NP", "WHADJP", "WHNP"};
        } else {
          how = new String[]{"left", "VP", "ADJP", "NP", "WHADJP", "WHNP"};
        }
        Tree pti = traverseLocate(kids, how, false);
        if (DEBUG) {
          System.err.println("Determined head is: " + pti);
        }
        if (pti != null) {
          return pti;
        } else {
          if (DEBUG) {
            System.err.println("------");
            System.err.println("SemanticHeadFinder failed to reassign head for");
            t.pennPrint(System.err);
            System.err.println("------");
          }
        }
      }
    }

    return super.determineNonTrivialHead(t, parent);
  }

  /* Checks whether the tree t is an existential constituent
   * There are two cases:
   * -- affirmative sentences in wich "there" is a left sister of the VP
   * -- questions in which "there" is a daughter of the SQ
   *
   */
  private boolean isExistential(Tree t, Tree parent) {
    if (DEBUG) {
      System.err.println("isExistential: " + t + ' ' + parent);
    }
    boolean toReturn = false;
    String motherCat = tlp.basicCategory(t.label().value());
    // affirmative case
    if (motherCat.equals("VP") && parent != null) {
      //take t and the sisters
      Tree[] kids = parent.children();
      // iterate over the sisters before t and checks if existential
      for (Tree kid : kids) {
        if (!kid.value().equals("VP")) {
          List<Label> tags = kid.preTerminalYield();
          for (Label tag : tags) {
            if (tag.value().equals("EX")) {
              toReturn = true;
            }
          }
        } else {
          break;
        }
      }
    }
    // question case
    else if (motherCat.startsWith("SQ") && parent != null) {
      //take the daughters
      Tree[] kids = parent.children();
      // iterate over the daughters and checks if existential
      for (Tree kid : kids) {
        if (!kid.value().startsWith("VB")) {//not necessary to look into the verb
          List<Label> tags = kid.preTerminalYield();
          for (Label tag : tags) {
            if (tag.value().equals("EX")) {
              toReturn = true;
            }
          }
        }
      }
    }

    if (DEBUG) {
      System.err.println("decision " + toReturn);
    }

    return toReturn;
  }


  /* Is the tree t a WH-question?
   *  At present this is only true if the tree t is a SQ having a WH.* sister
   *  and headed by a SBARQ.
   * (It was changed to looser definition in Feb 2006.)
   *
   */
  private static boolean isWHQ(Tree t, Tree parent) {
    if (t == null) {
      return false;
    }
    boolean toReturn = false;
    if (t.value().startsWith("SQ")) {
      if (parent != null && parent.value().equals("SBARQ")) {
        Tree[] kids = parent.children();
        for (Tree kid : kids) {
          // looks for a WH.*
          if (kid.value().startsWith("WH")) {
            toReturn = true;
          }
        }
      }
    }

    if (DEBUG) {
      System.err.println("in isWH, decision: " + toReturn + " for node " + t);
    }

    return toReturn;
  }

  /**
   * Reinserted so samples.GetSubcats compiles ... should rework if
   * this is going to stay.
   *
   * @param t A tree to examine for being an auxiliary.
   * @return Whether it is a verbal auxiliary (be, do, have, get)
   */
  public boolean isVerbalAuxiliary(Tree t) {
    Tree[] trees = new Tree[]{t};
    return hasVerbalAuxiliary(trees, verbalAuxiliaries);
  }

  private boolean hasVerbalAuxiliary(Tree[] kids, HashSet<String> verbalSet) {

    for (Tree kid : kids) {

      Label kidLabel = kid.label();

      String cat = tlp.basicCategory(kidLabel.value());
      String word = null;
      if (kidLabel instanceof HasWord) {
        word = ((HasWord) kidLabel).word();
      }
      if (word == null) {
        Label htl = kid.headTerminal(this).label();
        if (htl instanceof HasWord) {
          word = ((HasWord) htl).word();
        }
        if (word == null) {
          word = htl.value();
        }
      }

      String tag = null;
      if (kidLabel instanceof HasTag) {
        tag = ((HasTag) kidLabel).tag();
      }
      if (tag == null) {
        tag = kid.headPreTerminal(this).value();
      }
      if (DEBUG) {
        System.err.println("Checking " + kid.value() + " head is " + word + '/' + tag);
      }
      String lcWord = word.toLowerCase();
      // got to not match on to/TO if in PP!
      if ((!"PP".equals(cat)) && verbalTags.contains(tag) && verbalSet.contains(lcWord)) {
        return true;
      }
    }

    return false;
  }

  private static final long serialVersionUID = 5721799188009249808L;

}
