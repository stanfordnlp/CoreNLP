package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.HasCategory;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.Generics;

import java.util.Arrays;
import java.util.List;
import java.util.Set;


/**
 * Implements a 'semantic head' variant of the the HeadFinder found
 * in Michael Collins' 1999 thesis.
 * This version chooses the semantic head verb rather than the verb form
 * for cases with verbs.  And it makes similar themed changes to other
 * categories: e.g., in question phrases, like "Which Brazilian game", the
 * head is made "game" not "Which" as in common PTB head rules.<p/>
 * <p/>
 * By default the SemanticHeadFinder uses a treatment of copula where the
 * complement of the copula is taken as the head.  That is, a sentence like
 * "Bill is big" will be analyzed as <p/>
 * <p/>
 * <code>nsubj</code>(big, Bill) <br/>
 * <code>cop</code>(big, is) <p/>
 * <p/>
 * This analysis is used for questions and declaratives for adjective
 * complements and declarative nominal complements.  However Wh-sentences
 * with nominal complements do not receive this treatment.
 * "Who is the president?" is analyzed with "the president" as nsubj and "who"
 * as "attr" of the copula:<p/><p>
 * <code>nsubj</code>(is, president)<br/>
 * <code>attr</code>(is, Who) <p/>
 * <p/>
 * (Such nominal copula sentences are complex: arguably, depending on the
 * circumstances, several analyses are possible, with either the overt NP able
 * to be any of the subject, the predicate, or one of two referential entities
 * connected by an equational copula.  These uses aren't differentiated.)
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

  /* Tricky auxiliaries: "na" is from "gonna", "ve" from "Weve", etc. */
  private static final String[] auxiliaries = {"will", "wo", "shall", "sha", "may", "might", "should", "would", "can", "could", "ca", "must", "has", "have", "had", "having", "get", "gets", "getting", "got", "gotten", "do", "does", "did", "to", "'ve", "ve", "'d", "d", "'ll", "ll", "na" };
  private static final String[] beGetVerbs = {"be", "being", "been", "am", "are", "r", "is", "ai", "was", "were", "'m", "'re", "'s", "s", "get", "getting", "gets", "got"};
  private static final String[] copulaVerbs = {"be", "being", "been", "am", "are", "r", "is", "ai", "was", "were", "'m", "'re", "'s", "s", "seem", "seems", "seemed", "appear", "appears", "appeared", "stay", "stays", "stayed", "remain", "remains", "remained", "resemble", "resembles", "resembled", "become", "becomes", "became"};

  private static final String[] verbTags = {"TO", "MD", "VB", "VBD", "VBP", "VBZ", "VBG", "VBN", "AUX", "AUXG"};

  private final Set<String> verbalAuxiliaries;
  private final Set<String> copulars;
  private final Set<String> passiveAuxiliaries;
  private final Set<String> verbalTags;


  public SemanticHeadFinder() {
    this(new PennTreebankLanguagePack(), true);
  }

  public SemanticHeadFinder(boolean cop) {
    this(new PennTreebankLanguagePack(), cop);
  }


  /** Create a SemanticHeadFinder.
   *
   * @param tlp The TreebankLanguagePack, used by the superclass to get basic
   *     category of constituents.
   * @param cop If true, a copular verb (be, seem, appear, stay, remain, resemble, become)
   *     is not treated as head when it has an AdjP or NP complement.  If false,
   *     a copula verb is still always treated as a head.  But it will still
   *     be treated as an auxiliary in periphrastic tenses with a VP complement.
   */
  public SemanticHeadFinder(TreebankLanguagePack tlp, boolean cop) {
    super(tlp);
    ruleChanges();

    // make a distinction between auxiliaries and copula verbs to
    // get the NP has semantic head in sentences like "Bill is an honest man".  (Added "sha" for "shan't" May 2009
    verbalAuxiliaries = Generics.newHashSet();
    verbalAuxiliaries.addAll(Arrays.asList(auxiliaries));

    passiveAuxiliaries = Generics.newHashSet();
    passiveAuxiliaries.addAll(Arrays.asList(beGetVerbs));

    //copula verbs having an NP complement
    copulars = Generics.newHashSet();
    if (cop) {
      copulars.addAll(Arrays.asList(copulaVerbs));
    } // a few times the apostrophe is missing on "'s"

    verbalTags = Generics.newHashSet();
    // include Charniak tags so can do BLLIP right
    verbalTags.addAll(Arrays.asList(verbTags));

  }

  //makes modifications of Collins' rules to better fit with semantic notions of heads
  private void ruleChanges() {
    //  NP: don't want a POS to be the head
    nonTerminalInfo.put("NP", new String[][]{{"rightdis", "NN", "NNP", "NNPS", "NNS", "NX", "NML", "JJR"}, {"left", "NP", "PRP"}, {"rightdis", "$", "ADJP", "FW"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "QP", "DT", "WDT", "NML", "PRN", "RB", "RBR", "ADVP"}, {"left", "POS"}});
    // WHNP clauses should have the same sort of head as an NP
    // but it a WHNP has a NP and a WHNP under it, the WHNP should be the head.  E.g.,  (WHNP (WHNP (WP$ whose) (JJ chief) (JJ executive) (NN officer))(, ,) (NP (NNP James) (NNP Gatward))(, ,))
    nonTerminalInfo.put("WHNP", new String[][]{{"rightdis", "NN", "NNP", "NNPS", "NNS", "NX", "NML", "JJR", "WP"}, {"left", "WHNP", "NP"}, {"rightdis", "$", "ADJP", "PRN", "FW"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP"}, {"left", "WHPP", "WHADJP", "WP$", "WDT"}});
    //WHADJP
    nonTerminalInfo.put("WHADJP", new String[][]{{"left", "ADJP", "JJ", "JJR"}, {"right", "RB"}, {"right"}});
    //WHADJP
    nonTerminalInfo.put("WHADVP", new String[][]{{"rightdis", "WRB", "WHADVP", "RB", "JJ"}}); // if not WRB or WHADVP, probably has flat NP structure, allow JJ for "how long" constructions
    // QP: we don't want the first CD to be the semantic head (e.g., "three billion": head should be "billion"), so we go from right to left
    nonTerminalInfo.put("QP", new String[][]{{"right", "$", "NNS", "NN", "CD", "JJ", "PDT", "DT", "IN", "RB", "NCD", "QP", "JJR", "JJS"}});

    // S, SBAR and SQ clauses should prefer the main verb as the head
    // S: "He considered him a friend" -> we want a friend to be the head
    nonTerminalInfo.put("S", new String[][]{{"left", "VP", "S", "FRAG", "SBAR", "ADJP", "UCP", "TO"}, {"right", "NP"}});

    nonTerminalInfo.put("SBAR", new String[][]{{"left", "S", "SQ", "SINV", "SBAR", "FRAG", "VP", "WHNP", "WHPP", "WHADVP", "WHADJP", "IN", "DT"}});
    // VP shouldn't be needed in SBAR, but occurs in one buggy tree in PTB3 wsj_1457 and otherwise does no harm

    nonTerminalInfo.put("SQ", new String[][]{{"left", "VP", "SQ", "ADJP", "VB", "VBZ", "VBD", "VBP", "MD", "AUX", "AUXG"}});


    // UCP take the first element as head
    nonTerminalInfo.put("UCP", new String[][]{{"left"}});

    // CONJP: we want different heads for "but also" and "but not" and we don't want "not" to be the head in "not to mention"; now make "mention" head of "not to mention"
    nonTerminalInfo.put("CONJP", new String[][]{{"right", "VB", "JJ", "RB", "IN", "CC"}});

    // FRAG: crap rule needs to be change if you want to parse glosses; but it is correct to have ADJP and ADVP before S because of weird parses of reduced sentences.
    nonTerminalInfo.put("FRAG", new String[][]{{"left", "IN"}, {"right", "RB"}, {"left", "NP"}, {"left", "ADJP", "ADVP", "FRAG", "S", "SBAR", "VP"}});

    // PRN: sentence first
    nonTerminalInfo.put("PRN", new String[][]{{"left", "VP", "SQ", "S", "SINV", "SBAR", "NP", "ADJP", "PP", "ADVP", "INTJ", "WHNP", "NAC", "VBP", "JJ", "NN", "NNP"}});

    // add the constituent XS (special node to add a layer in a QP tree introduced in our QPTreeTransformer)
    nonTerminalInfo.put("XS", new String[][]{{"right", "IN"}});

    // add a rule to deal with the CoNLL data
    nonTerminalInfo.put("EMBED", new String[][]{{"right", "INTJ"}});

  }


  /**
   * Overwrite the postOperationFix method.  For a, b and c: we want a to be the head
   */
  @Override
  protected int postOperationFix(int headIdx, Tree[] daughterTrees) {
    if (headIdx >= 2) {
      String prevLab = tlp.basicCategory(daughterTrees[headIdx - 1].value());
      if (prevLab.equals("CC") || prevLab.equals("CONJP")) {
        int newHeadIdx = headIdx - 2;
        // Don't allow INTJ - important in informal genres "Oh and don't forget to call!"
        while (newHeadIdx >= 0 && (daughterTrees[newHeadIdx].isPreTerminal() && tlp.isPunctuationTag(daughterTrees[newHeadIdx].value()) ||
                                   "INTJ".equals(daughterTrees[newHeadIdx].value()))) {
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
   * heads determined.  Uses special rule for VP heads
   *
   * @param t The parse tree to examine the daughters of.
   *          This is assumed to never be a leaf
   * @return The parse tree that is the head
   */
  @Override
  protected Tree determineNonTrivialHead(Tree t, Tree parent) {
    String motherCat = tlp.basicCategory(t.label().value());

    if (DEBUG) {
      System.err.println("At " + motherCat + ", my parent is " + parent);
    }

    // do VPs with auxiliary as special case
    if ((motherCat.equals("VP") || motherCat.equals("SQ") || motherCat.equals("SINV"))) {
      Tree[] kids = t.children();
      // try to find if there is an auxiliary verb

      if (DEBUG) {
        System.err.println("Semantic head finder: at VP");
        System.err.println("Class is " + t.getClass().getName());
        t.pennPrint(System.err);
        //System.err.println("hasVerbalAuxiliary = " + hasVerbalAuxiliary(kids, verbalAuxiliaries));
      }

      // looks for auxiliaries
      if (hasVerbalAuxiliary(kids, verbalAuxiliaries) || hasPassiveProgressiveAuxiliary(kids, passiveAuxiliaries)) {
        // String[] how = new String[] {"left", "VP", "ADJP", "NP"};
        // Including NP etc seems okay for copular sentences but is
        // problematic for other auxiliaries, like 'he has an answer'
        // But maybe doing ADJP is fine!
        String[] how = { "left", "VP", "ADJP" };
        Tree pti = traverseLocate(kids, how, false);
        if (DEBUG) {
          System.err.println("Determined head (case 1) for " + t.value() + " is: " + pti);
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
      if (hasVerbalAuxiliary(kids, copulars) && ! isExistential(t, parent) && ! isWHQ(t, parent)) {
        String[] how;
        if (motherCat.equals("SQ")) {
          how = new String[]{"right", "VP", "ADJP", "NP", "WHADJP", "WHNP"};
        } else {
          how = new String[]{"left", "VP", "ADJP", "NP", "WHADJP", "WHNP"};
        }
        Tree pti = traverseLocate(kids, how, false);
        // don't allow a temporal to become head
        if (pti != null && pti.label() != null && pti.label().value().contains("-TMP")) {
          pti = null;
        }
        // In SQ, only allow an NP to become head if there is another one to the left (then it's probably predicative)
        if (motherCat.equals("SQ") && pti != null && pti.label() != null && pti.label().value().startsWith("NP")) {
            boolean foundAnotherNp = false;
            for (Tree kid : kids) {
              if (kid == pti) {
                break;
              } else if (kid.label() != null && kid.label().value().startsWith("NP")) {
                foundAnotherNp = true;
                break;
              }
            }
          if ( ! foundAnotherNp) {
            pti = null;
          }
        }

        if (DEBUG) {
          System.err.println("Determined head (case 2) for " + t.value() + " is: " + pti);
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

    Tree hd = super.determineNonTrivialHead(t, parent);

    // Heuristically repair punctuation heads
    Tree[] hdChildren = hd.children();
    if (hdChildren != null && hdChildren.length > 0 &&
        hdChildren[0].isLeaf()) {
      if (tlp.isPunctuationWord(hdChildren[0].label().value())) {
         Tree[] tChildren = t.children();
         if (DEBUG) {
           System.err.printf("head is punct: %s\n", hdChildren[0].label());
         }
         for (int i = tChildren.length - 1; i >= 0; i--) {
           if (!tlp.isPunctuationWord(tChildren[i].children()[0].label().value())) {
             hd = tChildren[i];
             if (DEBUG) {
               System.err.printf("New head: %s %s", hd.label(), hd.children()[0].label());
             }
             break;
           }
         }
      }
    }

    if (DEBUG) {
      System.err.println("Determined head (case 3) for " + t.value() + " is: " + hd);
    }
    return hd;
  }

  /* Checks whether the tree t is an existential constituent
   * There are two cases:
   * -- affirmative sentences in which "there" is a left sister of the VP
   * -- questions in which "there" is a daughter of the SQ.
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


  // now overally complex so it deals with coordinations.  Maybe change this class to use tregrex?
  private boolean hasPassiveProgressiveAuxiliary(Tree[] kids, Set<String> verbalSet) {
    if (DEBUG) {
      System.err.println("Checking for passive/progressive auxiliary");
    }
    boolean foundPassiveVP = false;
    boolean foundPassiveAux = false;
    for (Tree kid : kids) {
      if (DEBUG) {
        System.err.println("  checking in " + kid);
      }
      if (kid.isPreTerminal()) {
        Label kidLabel = kid.label();
        String tag = null;
        if (kidLabel instanceof HasTag) {
          tag = ((HasTag) kidLabel).tag();
        }
        if (tag == null) {
          tag = kid.value();
        }
        Label wordLabel = kid.firstChild().label();
        String word = null;
        if (wordLabel instanceof HasWord) {
          word = ((HasWord) wordLabel).word();
        }
        if (word == null) {
          word = wordLabel.value();
        }

        if (DEBUG) {
          System.err.println("Checking " + kid.value() + " head is " + word + '/' + tag);
        }
        String lcWord = word.toLowerCase();
        if (verbalTags.contains(tag) && verbalSet.contains(lcWord)) {
          if (DEBUG) {
            System.err.println("hasPassiveProgressiveAuxiliary found passive aux");
          }
          foundPassiveAux = true;
        }
      } else if (kid.isPhrasal()) {
        Label kidLabel = kid.label();
        String cat = null;
        if (kidLabel instanceof HasCategory) {
          cat = ((HasCategory) kidLabel).category();
        }
        if (cat == null) {
          cat = kid.value();
        }
        if ( ! cat.startsWith("VP")) {
          continue;
        }
        if (DEBUG) {
          System.err.println("hasPassiveProgressiveAuxiliary found VP");
        }
        Tree[] kidkids = kid.children();
        boolean foundParticipleInVp = false;
        for (Tree kidkid : kidkids) {
          if (DEBUG) {
            System.err.println("  hasPassiveProgressiveAuxiliary examining " + kidkid);
          }
          if (kidkid.isPreTerminal()) {
            Label kidkidLabel = kidkid.label();
            String tag = null;
            if (kidkidLabel instanceof HasTag) {
              tag = ((HasTag) kidkidLabel).tag();
            }
            if (tag == null) {
              tag = kidkid.value();
            }
            // we allow in VBD because of frequent tagging mistakes
            if ("VBN".equals(tag) || "VBG".equals(tag) || "VBD".equals(tag)) {
              foundPassiveVP = true;
              if (DEBUG) {
                System.err.println("hasPassiveAuxiliary found VBN/VBG/VBD VP");
              }
              break;
            } else if ("CC".equals(tag) && foundParticipleInVp) {
              foundPassiveVP = true;
              if (DEBUG) {
                System.err.println("hasPassiveAuxiliary [coordination] found (VP (VP[VBN/VBG/VBD] CC");
              }
              break;
            }
          } else if (kidkid.isPhrasal()) {
            String catcat = null;
            if (kidLabel instanceof HasCategory) {
              catcat = ((HasCategory) kidLabel).category();
            }
            if (catcat == null) {
              catcat = kid.value();
            }
            if ("VP".equals(catcat)) {
              if (DEBUG) {
                System.err.println("hasPassiveAuxiliary found (VP (VP)), recursing");
              }
              foundParticipleInVp = vpContainsParticiple(kidkid);
            } else if (("CONJP".equals(catcat) || "PRN".equals(catcat)) && foundParticipleInVp) { // occasionally get PRN in CONJ-like structures
              foundPassiveVP = true;
              if (DEBUG) {
                System.err.println("hasPassiveAuxiliary [coordination] found (VP (VP[VBN/VBG/VBD] CONJP");
              }
              break;
            }
          }
        }
      }
      if (foundPassiveAux && foundPassiveVP) {
        break;
      }
    } // end for (Tree kid : kids)
    if (DEBUG) {
      System.err.println("hasPassiveProgressiveAuxiliary returns " + (foundPassiveAux && foundPassiveVP));
    }
    return foundPassiveAux && foundPassiveVP;
  }

  private static boolean vpContainsParticiple(Tree t) {
    for (Tree kid : t.children()) {
      if (DEBUG) {
        System.err.println("vpContainsParticiple examining " + kid);
      }
      if (kid.isPreTerminal()) {
        Label kidLabel = kid.label();
        String tag = null;
        if (kidLabel instanceof HasTag) {
          tag = ((HasTag) kidLabel).tag();
        }
        if (tag == null) {
          tag = kid.value();
        }
        if ("VBN".equals(tag) || "VBG".equals(tag) || "VBD".equals(tag)) {
          if (DEBUG) {
            System.err.println("vpContainsParticiple found VBN/VBG/VBD VP");
          }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Reinserted so samples.GetSubcats compiles ... should rework if
   * this is going to stay.
   *
   * @param t A tree to examine for being an auxiliary.
   * @return Whether it is a verbal auxiliary (be, do, have, get)
   */
  public boolean isVerbalAuxiliary(Tree t) {
    Tree[] trees = { t };
    return hasVerbalAuxiliary(trees, verbalAuxiliaries);
  }


  /** This looks to see whether any of the children is a preterminal headed by a word
   *  which is within the set verbalSet (which in practice is either
   *  auxiliary or copula verbs).  It only returns true if it's a preterminal head, since
   *  you don't want to pick things up in phrasal daughters.  That is an error.
   *
   * @param kids The child trees
   * @param verbalSet The set of words
   * @return Returns true if one of the child trees is a preterminal verb headed
   *      by a word in verbalSet
   */
  private boolean hasVerbalAuxiliary(Tree[] kids, Set<String> verbalSet) {
    if (DEBUG) {
      System.err.println("Checking for verbal auxiliary");
    }
    for (Tree kid : kids) {
      if (DEBUG) {
        System.err.println("  checking in " + kid);
      }
      if (kid.isPreTerminal()) {
        Label kidLabel = kid.label();
        String tag = null;
        if (kidLabel instanceof HasTag) {
          tag = ((HasTag) kidLabel).tag();
        }
        if (tag == null) {
          tag = kid.value();
        }
        Label wordLabel = kid.firstChild().label();
        String word = null;
        if (wordLabel instanceof HasWord) {
          word = ((HasWord) wordLabel).word();
        }
        if (word == null) {
          word = wordLabel.value();
        }

        if (DEBUG) {
          System.err.println("Checking " + kid.value() + " head is " + word + '/' + tag);
        }
        String lcWord = word.toLowerCase();
        if (verbalTags.contains(tag) && verbalSet.contains(lcWord)) {
          if (DEBUG) {
            System.err.println("hasVerbalAuxiliary returns true");
          }
          return true;
        }
      }
    }
    if (DEBUG) {
      System.err.println("hasVerbalAuxiliary returns false");
    }
    return false;
  }


  private static final long serialVersionUID = 5721799188009249808L;

}
