package edu.stanford.nlp.trees; 

import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Coordination transformer transforms a PennTreebank tree containing
 * a coordination in a flat structure in order to get the dependencies
 * right.
 * <br>
 * The transformer goes through several steps:
 * <ul>
 * <li> Removes empty nodes and simplifies many tags (<code>DependencyTreeTransformer</code>)
 * <li> Relabels UCP phrases to either ADVP or NP depending on their content
 * <li> Turn flat CC structures into structures with an intervening node
 * <li> Add extra structure to QP phrases - combine "well over", unflattened structures with CC (<code>QPTreeTransformer</code>)
 * <li> Flatten SQ structures to get the verb as the head
 * <li> Rearrange structures that appear to be dates
 * <li> Flatten X over only X structures
 * <li> Turn some fixed conjunction phrases into CONJP, such as "and yet", etc
 * <li> Attach RB such as "not" to the next phrase to get the RB headed by the phrase it modifies
 * <li> Turn SBAR to PP if parsed as SBAR in phrases such as "The day after the airline was planning ..."
 * <li> Rearrange "now that" into an SBAR phrase if it was misparsed as ADVP
 * <li> (Only for universal dependencies) Extracts multi-word expressions and attaches all nodes to a new MWE constituent
 * </ul>
 *
 * @author Marie-Catherine de Marneffe
 * @author John Bauer
 * @author Sebastian Schuster
 */
public class CoordinationTransformer implements TreeTransformer  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CoordinationTransformer.class);

  private static final boolean VERBOSE = System.getProperty("CoordinationTransformer", null) != null;
  private final TreeTransformer tn = new DependencyTreeTransformer(); //to get rid of unwanted nodes and tag
  private final TreeTransformer dates = new DateTreeTransformer();    //to flatten date patterns
  private final TreeTransformer qp;                                   //to restructure the QP constituents

  private final HeadFinder headFinder;
  private final boolean performMWETransformation;

  // default constructor
  public CoordinationTransformer(HeadFinder hf) {
    this(hf, false);
  }
  
  /**
   * Constructor
   * 
   * @param hf the headfinder
   * @param performMWETransformation Parameter for backwards compatibility. 
   * If set to false, multi-word expressions won't be attached to a new "MWE" node
   */
  public CoordinationTransformer(HeadFinder hf, boolean performMWETransformation) {
    this.headFinder = hf;
    this.performMWETransformation = performMWETransformation;
    qp = new QPTreeTransformer(performMWETransformation);
  }

  public void debugLine(String prefix, Tree t) {
    if (t instanceof TreeGraphNode) {
      log.info(prefix + ((TreeGraphNode) t).toOneLineString());
    } else {
      log.info(prefix + t);
    }
  }

  /**
   * Transforms t if it contains a coordination in a flat structure (CCtransform)
   * and transforms UCP (UCPtransform).
   *
   * @param t a tree to be transformed
   * @return t transformed
   */
  @Override
  public Tree transformTree(Tree t) {
    if (VERBOSE) {
      debugLine("Input to CoordinationTransformer: ", t);
    }

    if (performMWETransformation) {
      t = gappingTransform(t);
      if (VERBOSE) {
        debugLine("After t = gappingTransform(t);:   ", t);
      }
    }

    t = tn.transformTree(t);
    if (VERBOSE) {
      debugLine("After DependencyTreeTransformer:  ", t);
    }
    if (t == null) {
      return t;
    }

    if (performMWETransformation) {
      t = MWETransform(t);
      if (VERBOSE) {
        debugLine("After MWETransform:               ", t);
      }

      t = MWFlatTransform(t);
      if (VERBOSE) {
        debugLine("After MWFlatTransform:            ", t);
      }

      t = prepCCTransform(t);
      if (VERBOSE) {
        debugLine("After prepCCTransform:            ", t);
      }
    }

    t = UCPtransform(t);
    if (VERBOSE) {
      debugLine("After UCPTransformer:             ", t);
    }
    t = CCtransform(t);
    if (VERBOSE) {
      debugLine("After CCTransformer:              ", t);
    }
    t = qp.transformTree(t);
    if (VERBOSE) {
      debugLine("After QPTreeTransformer:          ", t);
    }
    t = SQflatten(t);
    if (VERBOSE) {
      debugLine("After SQ flattening:              ", t);
    }
    t = dates.transformTree(t);
    if (VERBOSE) {
      debugLine("After DateTreeTransformer:        ", t);
    }
    t = removeXOverX(t);
    if (VERBOSE) {
      debugLine("After removeXoverX:               ", t);
    }
    t = combineConjp(t);
    if (VERBOSE) {
      debugLine("After combineConjp:               ", t);
    }
    t = moveRB(t);
    if (VERBOSE) {
      debugLine("After moveRB:                     ", t);
    }
    t = changeSbarToPP(t);
    if (VERBOSE) {
      debugLine("After changeSbarToPP:             ", t);
    }
    t = rearrangeNowThat(t);
    if (VERBOSE) {
      debugLine("After rearrangeNowThat:           ", t);
    }
    t = mergeYodaVerbs(t);
    if (VERBOSE) {
      debugLine("After mergeYodaVerbs:             ", t);
    }
    return t;
  }

  private static final TregexPattern rearrangeNowThatTregex =
    TregexPattern.compile("ADVP=advp <1 (RB < /^(?i:now)$/) <2 (SBAR=sbar <1 (IN < /^(?i:that)$/))");

  private static final TsurgeonPattern rearrangeNowThatTsurgeon =
    Tsurgeon.parseOperation("[relabel advp SBAR] [excise sbar sbar]");

  private static Tree rearrangeNowThat(Tree t) {
    if (t == null) {
      return t;
    }
    return Tsurgeon.processPattern(rearrangeNowThatTregex, rearrangeNowThatTsurgeon, t);
  }


  private static final TregexPattern mergeYodaVerbsTregex =
    TregexPattern.compile("VP=home < VBN=vbn $+ (VP=willbe <... {(__=will < will|have|has) ; (VP < (__=be << be|been))})");

  private static final TsurgeonPattern mergeYodaVerbsTsurgeon =
    Tsurgeon.parseOperation("[createSubtree VP vbn] [move will >-1 home] [move be >-1 home] [prune willbe]");

  /**
   * Text such as "Also excluded will be ---" should have similar dependencies to "--- also will be excluded".
   * Rearranging the verbs with these tsurgeon makes the headfinder more accurate on that type of sentence.
   */
  private static Tree mergeYodaVerbs(Tree t) {
    if (t == null) {
      return t;
    }
    return Tsurgeon.processPattern(mergeYodaVerbsTregex, mergeYodaVerbsTsurgeon, t);
  }

  private static final TregexPattern changeSbarToPPTregex =
    TregexPattern.compile("NP < (NP $++ (SBAR=sbar < (IN < /^(?i:after|before|until|since|during)$/ $++ S)))");

  private static final TsurgeonPattern changeSbarToPPTsurgeon =
    Tsurgeon.parseOperation("relabel sbar PP");

  /**
   * For certain phrases, we change the SBAR to a PP to get prep/pcomp
   * dependencies.  For example, in "The day after the airline was
   * planning...", we want prep(day, after) and pcomp(after,
   * planning).  If "after the airline was planning" was parsed as an
   * SBAR, either by the parser or in the treebank, we fix that here.
   */

  private static Tree changeSbarToPP(Tree t) {
    if (t == null) {
      return null;
    }
    return Tsurgeon.processPattern(changeSbarToPPTregex, changeSbarToPPTsurgeon, t);
  }

  private static final TregexPattern findFlatConjpTregex =
    // TODO: add more patterns, perhaps ignore case
    // for example, what should we do with "and not"?  Is it right to
    // generally add the "not" to the following tree with moveRB, or
    // should we make "and not" a CONJP?
    // also, perhaps look at ADVP
    TregexPattern.compile("/^(S|PP|VP)/ < (/^(S(?!YM)|PP|VP)/ $++ (CC=start $+ (RB|ADVP $+ /^(S(?!YM)|PP|VP)/) " +
                          "[ (< and $+ (RB=end < yet)) | " +  // TODO: what should be the head of "and yet"?
                          "  (< and $+ (RB=end < so)) | " +
                          "  (< and $+ (ADVP=end < (RB|IN < so))) ] ))"); // TODO: this structure needs a dependency

  private static final TsurgeonPattern addConjpTsurgeon =
    Tsurgeon.parseOperation("createSubtree CONJP start end");

  private static Tree combineConjp(Tree t) {
    if (t == null) {
      return null;
    }
    return Tsurgeon.processPattern(findFlatConjpTregex, addConjpTsurgeon, t);
  }

  private static final TregexPattern[] moveRBTregex = {
    TregexPattern.compile("/^S|PP|VP|NP/ < (/^(S|PP|VP|NP)/ $++ (/^(,|CC|CONJP)$/ [ $+ (RB=adv [ < not | < then ]) | $+ (ADVP=adv <: RB) ])) : (=adv $+ /^(S(?!YM)|PP|VP|NP)/=dest) "),
    TregexPattern.compile("/^ADVP/ < (/^ADVP/ $++ (/^(,|CC|CONJP)$/ [$+ (RB=adv [ < not | < then ]) | $+ (ADVP=adv <: RB)])) : (=adv $+ /^NP-ADV|ADVP|PP/=dest)"),
    TregexPattern.compile("/^FRAG/ < (ADVP|RB=adv $+ VP=dest)"),
  };

  private static final TsurgeonPattern moveRBTsurgeon =
    Tsurgeon.parseOperation("move adv >0 dest");

  static Tree moveRB(Tree t) {
    if (t == null) {
      return null;
    }
    for (TregexPattern pattern : moveRBTregex) {
      t = Tsurgeon.processPattern(pattern, moveRBTsurgeon, t);
    }
    return t;
  }

  // Matches to be questions if the question starts with WHNP, such as
  // Who, What, if there is an SQ after the WH question.
  //
  // TODO: maybe we want to catch more complicated tree structures
  // with something in between the WH and the actual question.
  private static final TregexPattern flattenSQTregex =
    TregexPattern.compile("SBARQ < ((WHNP=what < WP) $+ (SQ=sq < (/^VB/=verb < " + EnglishPatterns.copularWordRegex + ") " +
                          // match against "is running" if the verb is under just a VBG
                          " !< (/^VB/ < !" + EnglishPatterns.copularWordRegex + ") " +
                          // match against "is running" if the verb is under a VP - VBG
                          " !< (/^V/ < /^VB/ < !" + EnglishPatterns.copularWordRegex + ") " +
                          // match against "What is on the test?"
                          " !< (PP $- =verb) " +
                          // match against "is there"
                          " !<, (/^VB/ < " + EnglishPatterns.copularWordRegex + " $+ (NP < (EX < there)))" +
                          // match against "good at"
                          " !< (ADJP < (PP <: IN|TO))))");

  private static final TsurgeonPattern flattenSQTsurgeon = Tsurgeon.parseOperation("excise sq sq");

  /**
   * Removes the SQ structure under a WHNP question, such as "Who am I
   * to judge?".  We do this so that it is easier to pick out the head
   * and then easier to connect that head to all of the other words in
   * the question in this situation.  In the specific case of making
   * the copula head, we don't do this so that the existing headfinder
   * code can easily find the "am" or other copula verb.
   */
  public Tree SQflatten(Tree t) {
    if (headFinder != null && (headFinder instanceof CopulaHeadFinder)) {
      if (((CopulaHeadFinder) headFinder).makesCopulaHead()) {
        return t;
      }
    }
    if (t == null) {
      return null;
    }
    return Tsurgeon.processPattern(flattenSQTregex, flattenSQTsurgeon, t);
  }

  private static final TregexPattern removeXOverXTregex =
    TregexPattern.compile("__=repeat <: (~repeat < __)");

  private static final TsurgeonPattern removeXOverXTsurgeon = Tsurgeon.parseOperation("excise repeat repeat");

  public static Tree removeXOverX(Tree t) {
    return Tsurgeon.processPattern(removeXOverXTregex, removeXOverXTsurgeon, t);
  }

  // UCP (JJ ...) -> ADJP
  // UCP (DT JJ ...) -> ADJP
  // UCP (... (ADJP (JJR older|younger))) -> ADJP
  // UCP (N ...) -> NP
  // UCP ADVP -> ADVP
  // Might want to look for ways to include RB for flatter structures,
  // but then we have to watch out for (RB not) for example
  // Note that the order of OR expressions means the older|younger
  // pattern takes precedence
  // By searching for everything at once, then using one tsurgeon
  // which fixes everything at once, we can save quite a bit of time
  private static final TregexPattern ucpRenameTregex =
    TregexPattern.compile("/^UCP/=ucp [ <, /^JJ|ADJP/=adjp | ( <1 DT <2 /^JJ|ADJP/=adjp ) |" +
                          " <- (ADJP=adjp < (JJR < /^(?i:younger|older)$/)) |" +
                          " <, /^N/=np | ( <1 DT <2 /^N/=np ) | " +
                          " <, /^ADVP/=advp ]");

  // TODO: this turns UCP-TMP into ADVP instead of ADVP-TMP.  What do we actually want?
  private static final TsurgeonPattern ucpRenameTsurgeon =
    Tsurgeon.parseOperation("[if exists adjp relabel ucp /^UCP(.*)$/ADJP$1/] [if exists np relabel ucp /^UCP(.*)$/NP$1/] [if exists advp relabel ucp /^UCP(.*)$/ADVP/]");

  /**
   * Transforms t if it contains an UCP, it will change the UCP tag
   * into the phrasal tag of the first word of the UCP
   * (UCP (JJ electronic) (, ,) (NN computer) (CC and) (NN building))
   * will become
   * (ADJP (JJ electronic) (, ,) (NN computer) (CC and) (NN building))
   *
   * @param t a tree to be transformed
   * @return t transformed
   */
  public static Tree UCPtransform(Tree t) {
    if (t == null) {
      return null;
    }
    return Tsurgeon.processPattern(ucpRenameTregex, ucpRenameTsurgeon, t);
  }


  /**
   * Transforms t if it contains a coordination in a flat structure
   *
   * @param t a tree to be transformed
   * @return t transformed (give t not null, return will not be null)
   */
  public static Tree CCtransform(Tree t) {
    boolean notDone = true;
    while (notDone) {
      Tree cc = findCCparent(t, t);
      if (cc != null) {
        t = cc;
      } else {
        notDone = false;
      }
    }
    return t;
  }

  private static String getHeadTag(Tree t) {
    if (t.value().startsWith("NN")) {
      return "NP";
    } else if (t.value().startsWith("JJ")) {
      return "ADJP";
    } else {
      return "NP";
    }
  }


  /** If things match, this method destructively changes the children list
   *  of the tree t.  When this method is called, t is an NP and there must
   *  be at least two children to the right of ccIndex.
   *
   *  @param t The tree to transform a conjunction in
   *  @param ccIndex The index of the CC child
   *  @return t
   */
  private static Tree transformCC(Tree t, int ccIndex) {
    if (VERBOSE) {
      log.info("transformCC in:  " + t);
    }
    //System.out.println(ccIndex);
    // use the factories of t to create new nodes
    TreeFactory tf = t.treeFactory();
    LabelFactory lf = t.label().labelFactory();

    Tree[] ccSiblings = t.children();

    //check if other CC
    List<Integer> ccPositions = new ArrayList<>();
    for (int i = ccIndex + 1; i < ccSiblings.length; i++) {
      if (ccSiblings[i].value().startsWith("CC") && i < ccSiblings.length - 1) { // second conjunct to ensure that a CC we add isn't the last child
        ccPositions.add(Integer.valueOf(i));
      }
    }

    // a CC b c ... -> (a CC b) c ...  with b not a DT
    String beforeSibling = ccSiblings[ccIndex - 1].value();
    if (ccIndex == 1 && (beforeSibling.equals("DT") || beforeSibling.equals("JJ") || beforeSibling.equals("RB") || ! (ccSiblings[ccIndex + 1].value().equals("DT"))) && ! (beforeSibling.startsWith("NP")
            || beforeSibling.equals("ADJP")
            || beforeSibling.equals("NNS"))) { // && (ccSiblings.length == ccIndex + 3 || !ccPositions.isEmpty())) {  // something like "soya or maize oil"
      String leftHead = getHeadTag(ccSiblings[ccIndex - 1]);
      //create a new tree to be inserted as first child of t
      Tree left = tf.newTreeNode(lf.newLabel(leftHead), null);
      for (int i = 0; i < ccIndex + 2; i++) {
        left.addChild(ccSiblings[i]);
      }
      if (VERBOSE) {
        System.out.println("print left tree");
        left.pennPrint();
        System.out.println();
      }

      // remove all the children of t before ccIndex+2
      for (int i = 0; i < ccIndex + 2; i++) {
        t.removeChild(0);
      }
      if (VERBOSE) { if (t.numChildren() == 0) { System.out.println("Youch! No t children"); } }

      // if stuff after (like "soya or maize oil and vegetables")
      // we need to put the tree in another tree
      if (!ccPositions.isEmpty()) {
        boolean comma = false;
        int index = ccPositions.get(0);
        if (VERBOSE) {log.info("more CC index " +  index);}
        if (ccSiblings[index - 1].value().equals(",")) {//to handle the case of a comma ("soya and maize oil, and vegetables")
          index = index - 1;
          comma = true;
        }
        if (VERBOSE) {log.info("more CC index " +  index);}
        String head = getHeadTag(ccSiblings[index - 1]);

        if (ccIndex + 2 < index) {
          Tree tree = tf.newTreeNode(lf.newLabel(head), null);
          tree.addChild(0, left);

          int k = 1;
          for (int j = ccIndex+2; j<index; j++) {
            if (VERBOSE) ccSiblings[j].pennPrint();
            t.removeChild(0);
            tree.addChild(k, ccSiblings[j]);
            k++;
          }

          if (VERBOSE) {
            System.out.println("print t");
            t.pennPrint();

            System.out.println("print tree");
            tree.pennPrint();
            System.out.println();
          }
          t.addChild(0, tree);
        } else {
          t.addChild(0, left);
        }

        Tree rightTree = tf.newTreeNode(lf.newLabel("NP"), null);
        int start = 2;
        if (comma) {
          start++;
        }
        while (start < t.numChildren()) {
          Tree sib = t.getChild(start);
          t.removeChild(start);
          rightTree.addChild(sib);
        }
        t.addChild(rightTree);
      } else {
        t.addChild(0, left);
      }
    }
    // DT a CC b c -> DT (a CC b) c
    else if (ccIndex == 2 && ccSiblings[0].value().startsWith("DT") && !ccSiblings[ccIndex - 1].value().equals("NNS") && (ccSiblings.length == 5 || (!ccPositions.isEmpty() && ccPositions.get(0) == 5))) {
      String head = getHeadTag(ccSiblings[ccIndex - 1]);
      //create a new tree to be inserted as second child of t (after the determiner
      Tree child = tf.newTreeNode(lf.newLabel(head), null);

      for (int i = 1; i < ccIndex + 2; i++) {
        child.addChild(ccSiblings[i]);
      }
      if (VERBOSE) { if (child.numChildren() == 0) { System.out.println("Youch! No child children"); } }

      // remove all the children of t between the determiner and ccIndex+2
      //System.out.println("print left tree");
      //child.pennPrint();

      for (int i = 1; i < ccIndex + 2; i++) {
        t.removeChild(1);
      }

      t.addChild(1, child);
    }

    // ... a, b CC c ... -> ... (a, b CC c) ...
    else if (ccIndex > 2 && ccSiblings[ccIndex - 2].value().equals(",") && !ccSiblings[ccIndex - 1].value().equals("NNS")) {
      String head = getHeadTag(ccSiblings[ccIndex - 1]);
      Tree child = tf.newTreeNode(lf.newLabel(head), null);

      for (int i = ccIndex - 3; i < ccIndex + 2; i++) {
        child.addChild(ccSiblings[i]);
      }
      if (VERBOSE) { if (child.numChildren() == 0) { System.out.println("Youch! No child children"); } }

      int i = ccIndex - 4;
      while (i > 0 && ccSiblings[i].value().equals(",")) {
        child.addChild(0, ccSiblings[i]);    // add the comma
        child.addChild(0, ccSiblings[i - 1]);  // add the word before the comma
        i = i - 2;
      }

      if (i < 0) {
        i = -1;
      }

      // remove the old children
      for (int j = i + 1; j < ccIndex + 2; j++) {
        t.removeChild(i + 1);
      }
      // put the new tree
      t.addChild(i + 1, child);
    }

    // something like "the new phone book and tour guide" -> multiple heads
    // we want (NP the new phone book) (CC and) (NP tour guide)
    else {
      boolean commaLeft = false;
      boolean commaRight = false;
      boolean preconj = false;
      int indexBegin = 0;
      Tree conjT = tf.newTreeNode(lf.newLabel("CC"), null);

      // create the left tree
      String leftHead = getHeadTag(ccSiblings[ccIndex - 1]);
      Tree left = tf.newTreeNode(lf.newLabel(leftHead), null);


      // handle the case of a preconjunct (either, both, neither)
      Tree first = ccSiblings[0];
      String leaf = first.firstChild().value().toLowerCase();
      if (leaf.equals("either") || leaf.equals("neither") || leaf.equals("both")) {
        preconj = true;
        indexBegin = 1;
        conjT.addChild(first.firstChild());
      }

      for (int i = indexBegin; i < ccIndex - 1; i++) {
        left.addChild(ccSiblings[i]);
      }
      // handle the case of a comma ("GM soya and maize, and food ingredients")
      if (ccSiblings[ccIndex - 1].value().equals(",")) {
        commaLeft = true;
      } else {
        left.addChild(ccSiblings[ccIndex - 1]);
      }

      // create the CC tree
      Tree cc = ccSiblings[ccIndex];

      // create the right tree
      int nextCC;
      if (ccPositions.isEmpty()) {
        nextCC = ccSiblings.length;
      } else {
        nextCC = ccPositions.get(0);
      }
      String rightHead = getHeadTag(ccSiblings[nextCC - 1]);
      Tree right = tf.newTreeNode(lf.newLabel(rightHead), null);
      for (int i = ccIndex + 1; i < nextCC - 1; i++) {
        right.addChild(ccSiblings[i]);
      }
      // handle the case of a comma ("GM soya and maize, and food ingredients")
      if (ccSiblings[nextCC - 1].value().equals(",")) {
        commaRight = true;
      } else {
        right.addChild(ccSiblings[nextCC - 1]);
      }

      if (VERBOSE) {
        if (left.numChildren() == 0) { System.out.println("Youch! No left children"); }
        if (right.numChildren() == 0) { System.out.println("Youch! No right children"); }
      }

      // put trees together in old t, first we remove the old nodes
      for (int i = 0; i < nextCC; i++) {
        t.removeChild(0);
      }
      if (!ccPositions.isEmpty()) { // need an extra level
        Tree tree = tf.newTreeNode(lf.newLabel("NP"), null);

        if (preconj) {
          tree.addChild(conjT);
        }
        if (left.numChildren() > 0) {
          tree.addChild(left);
        }
        if (commaLeft) {
          tree.addChild(ccSiblings[ccIndex - 1]);
        }
        tree.addChild(cc);
        if (right.numChildren() > 0) {
          tree.addChild(right);
        }
        if (commaRight) {
          t.addChild(0, ccSiblings[nextCC - 1]);
        }
        t.addChild(0, tree);
      } else {
        if (preconj) {
          t.addChild(conjT);
        }
        if (left.numChildren() > 0) {
          t.addChild(left);
        }
        if (commaLeft) {
          t.addChild(ccSiblings[ccIndex - 1]);
        }
        t.addChild(cc);
        if (right.numChildren() > 0) {
          t.addChild(right);
        }
        if (commaRight) {
          t.addChild(ccSiblings[nextCC - 1]);
        }
      }
    }

    if (VERBOSE) {
      log.info("transformCC out: " + t);
    }
    return t;
  }

  private static boolean notNP(List<Tree> children, int ccIndex) {
    for (int i = ccIndex, sz = children.size(); i < sz; i++) {
      if (children.get(i).value().startsWith("NP")) {
        return false;
      }
    }
    return true;
  }

  /*
   * Given a tree t, if this tree contains a CC inside a NP followed by 2 nodes
   * (i.e. we have a flat structure that will not work for the dependencies),
   * it will call transform CC on the NP containing the CC and the index of the
   * CC, and then return the root of the whole transformed tree.
   * If it finds no such tree, this method returns null.
   */
  private static Tree findCCparent(Tree t, Tree root) {
    if (t.isPreTerminal()) {
      if (t.value().startsWith("CC")) {
        Tree parent = t.parent(root);
        if (parent != null && parent.value().startsWith("NP")) {
          List<Tree> children = parent.getChildrenAsList();
          //System.out.println(children);
          int ccIndex = children.indexOf(t);
          if (children.size() > ccIndex + 2 && notNP(children, ccIndex) && ccIndex != 0 && (ccIndex == children.size() - 1 || !children.get(ccIndex+1).value().startsWith("CC"))) {
            transformCC(parent, ccIndex);
            if (VERBOSE) {
              log.info("After transformCC:             " + root);
            }
            return root;
          }
        }
      }
    } else {
      for (Tree child : t.getChildrenAsList()) {
        Tree cur = findCCparent(child, root);
        if (cur != null) {
          return cur;
        }
      }
    }
    return null;
  }

  /**
   * Multi-word expression patterns
   */
  private static final TregexPattern[] MWE_PATTERNS = {
    TregexPattern.compile("@CONJP <1 (RB=node1 < /^(?i)as$/) <2 (RB=node2 < /^(?i)well$/) <- (IN=node3 < /^(?i)as$/)"), //as well as
    TregexPattern.compile("@ADVP|CONJP <1 (RB=node1 < /^(?i)as$/) <- (IN|RB=node2 < /^(?i)well$/)"), //as well
    TregexPattern.compile("@PP < ((JJ=node1 < /^(?i)such$/) $+ (IN=node2 < /^(?i)as$/))"), //such as
    TregexPattern.compile("@PP < ((JJ|IN=node1 < /^(?i)due$/) $+ (IN|TO=node2 < /^(?i)to$/))"), //due to 
    TregexPattern.compile("@PP|CONJP < ((IN|RB=node1 < /^(?i)(because|instead)$/) $+ (IN=node2 < of))"), //because of/instead of 
    TregexPattern.compile("@ADVP|SBAR < ((IN|RB=node1 < /^(?i)in$/) $+ (NN=node2 < /^(?i)case$/))"), //in case
    TregexPattern.compile("@ADVP|PP < ((IN|RB=node1 < /^(?i)of$/) $+ (NN|RB=node2 < /^(?i)course$/))"), //of course
    TregexPattern.compile("@SBAR|PP < ((IN|RB=node1 < /^(?i)in$/) $+ (NN|NP|RB=node2 [< /^(?i)order$/ | <: (NN < /^(?i)order$/)]))"), //in order
    TregexPattern.compile("@PP|CONJP|SBAR < ((IN|RB=node1 < /^(?i)rather$/) $+ (IN=node2 < /^(?i)than$/))"), //rather than
    TregexPattern.compile("@CONJP < ((IN|RB=node1 < /^(?i)not$/) $+ (TO=node2 < /^(?i)to$/ $+ (VB|RB=node3 < /^(?i)mention$/)))"), //not to mention
    TregexPattern.compile("@PP|SBAR < ((JJ|IN|RB=node1 < /^(?i)so$/) $+ (IN|TO=node2 < /^(?i)that$/))"), //so that 
    TregexPattern.compile("@SBAR < ((IN|RB=node1 < /^(?i)as$/) $+ (IN=node2 < /^(?i)if$/))"), //as if
    TregexPattern.compile("@PP < ((JJ|RB=node1 < /^(?i)prior$/) $+ (TO|IN=node2 < /^(?i)to$/))"), //prior to
    TregexPattern.compile("@PP < ((IN=node1 < /^(?i)as$/) $+ (TO|IN=node2 < /^(?i)to$/))"), //as to
    TregexPattern.compile("@ADVP < ((RB|NN=node1 < /^(?i)kind|sort$/) $+ (IN|RB=node2 < /^(?i)of$/))"), //kind of, sort of
    TregexPattern.compile("@SBAR < ((IN|RB=node1 < /^(?i)whether$/) $+ (CC=node2 < /^(?i)or$/ $+ (RB=node3 < /^(?i)not$/)))"), //whether or not
    TregexPattern.compile("@CONJP < ((IN=node1 < /^(?i)as$/) $+ (VBN=node2 < /^(?i)opposed$/ $+ (TO|IN=node3 < /^(?i)to$/)))"), //as opposed to
    TregexPattern.compile("@ADVP|CONJP < ((VB|RB|VBD=node1 < /^(?i)let$/) $+ (RB|JJ=node2 < /^(?i)alone$/))"), //let alone
    //TODO: "so as to"
    TregexPattern.compile("@ADVP|PP < ((IN|RB=node1 < /^(?i)in$/) $+ (IN|NP|PP|RB|ADVP=node2 [< /^(?i)between$/ | <: (IN|RB < /^(?i)between$/)]))"), //in between
    TregexPattern.compile("@ADVP|QP|ADJP < ((DT|RB=node1 < /^(?i)all$/) $+ (CC|RB|IN=node2 < /^(?i)but$/))"), //all but
    TregexPattern.compile("@ADVP|INTJ < ((NN|DT|RB=node1 < /^(?i)that$/) $+ (VBZ|RB=node2 < /^(?i)is$/))"), //that is
    TregexPattern.compile("@WHADVP < ((WRB=node1 < /^(?i:how)$/) $+ (VB=node2 < /^(?i)come$/))"), //how come
    TregexPattern.compile("@VP < ((VBD=node1 < had|'d) $+ (@PRT|ADVP=node2 <: (RBR < /^(?i)better$/)))"), //had better
    TregexPattern.compile("@QP|XS < ((JJR|RBR|IN=node1 < /^(?i)(more|less)$/) $+ (IN=node2 < /^(?i)than$/))"), //more/less than
    TregexPattern.compile("@QP|XS < ((JJR|RBR||RB|RP|IN=node1 < /^(?i)(up)$/) $+ (IN|TO=node2 < /^(?i)to$/))"), // up to
    TregexPattern.compile("@QP < ((JJR|RBR|RB|RP|IN=node1 < /^(?i)up$/) $+ (IN|TO=node2 < /^(?i)to$/))"), //up to
    TregexPattern.compile("@S|SQ|VP|ADVP|PP < (@ADVP < ((IN|RB=node1 < /^(?i)at$/) $+ (JJS|RBS=node2 < /^(?i)least$/)) !$+ (RB < /(?i)(once|twice)/))"), //at least
  };
  
  private static final TsurgeonPattern MWE_OPERATION = Tsurgeon.parseOperation("[createSubtree MWE node1 node2] [if exists node3 move node3 $- node2]");
  
  private static final TregexPattern ACCORDING_TO_PATTERN = TregexPattern.compile("PP=pp1 < (VBG=node1 < /^(?i)according$/ $+ (PP=pp2 < (TO|IN=node2 < to)))");
  private static final TsurgeonPattern ACCORDING_TO_OPERATION = Tsurgeon.parseOperation("[createSubtree MWE node1] [move node2 $- node1] [excise pp2 pp2]");

  /* "but also" is not a MWE, so break up the CONJP. */ 
  private static final TregexPattern BUT_ALSO_PATTERN = TregexPattern.compile("CONJP=conjp < (CC=cc < but) < (RB=rb < also) ?$+ (__=nextNode < (__ < __))");
  private static final TsurgeonPattern BUT_ALSO_OPERATION = Tsurgeon.parseOperation("[move cc $- conjp] [move rb $- cc] [if exists nextNode move rb >1 nextNode] [createSubtree ADVP rb] [delete conjp]");

  /*
   * "not only" is not a MWE, so break up the CONJP similar to "but also".
   * compensate for some JJ tagged "only" in this expression
   */
  private static final TregexPattern NOT_ONLY_PATTERN = TregexPattern.compile("CONJP|ADVP=conjp < (RB=not < /^(?i)not$/) < (RB|JJ=only < /^(?i)only|just|merely|even$/) ?$+ (__=nextNode < (__ < __))");
  private static final TsurgeonPattern NOT_ONLY_OPERATION = Tsurgeon.parseOperation("[move not $- conjp] [move only $- not] [if exists nextNode move only >1 nextNode] [if exists nextNode move not >1 nextNode] [createSubtree ADVP not] [createSubtree ADVP only] [delete conjp]");

  /* at least / at most / at best / at worst / ... should be treated as if "at"
     was a preposition and the RBS was a noun. Assumes that the MWE "at least"
     has already been extracted. */
  private static final TregexPattern AT_RBS_PATTERN = TregexPattern.compile("@ADVP|QP < ((IN|RB=node1 < /^(?i)at$/) $+ (JJS|RBS=node2))");
  private static final TsurgeonPattern AT_RBS_OPERATION = Tsurgeon.parseOperation("[relabel node1 IN] [createSubtree ADVP node1] [move node2 $- node1] [createSubtree NP node2]");

  /* at all should be treated like a PP. */
  private static final TregexPattern AT_ALL_PATTERN = TregexPattern.compile("@ADVP=head < (RB|IN=node1 < /^(?i)at$/ $+ (RB|DT=node2 < /^(?i)all$/))");
  private static final TsurgeonPattern AT_ALL_OPERATION = Tsurgeon.parseOperation("[relabel head PP] [relabel node1 IN] [createSubtree NP node2]");

  /**
   * Puts all multi-word expressions below a single constituent labeled "MWE".
   * Patterns for multi-word expressions are defined in MWE_PATTERNS.
   */
  public static Tree MWETransform(Tree t) {
    for (TregexPattern p : MWE_PATTERNS) {
      Tsurgeon.processPattern(p, MWE_OPERATION, t);
    }
    
    Tsurgeon.processPattern(ACCORDING_TO_PATTERN, ACCORDING_TO_OPERATION, t);
    Tsurgeon.processPattern(BUT_ALSO_PATTERN, BUT_ALSO_OPERATION, t);
    Tsurgeon.processPattern(NOT_ONLY_PATTERN, NOT_ONLY_OPERATION, t);
    Tsurgeon.processPattern(AT_RBS_PATTERN, AT_RBS_OPERATION, t);
    Tsurgeon.processPattern(AT_ALL_PATTERN, AT_ALL_OPERATION, t);

    return t;
  }

  private static final TregexPattern[] MW_FLAT_PATTERNS = {
    TregexPattern.compile("@NP|ADVP <... {(__=node1 < /^(?i)en$/); (__=node2 < /^(?i)masse$/)}"), // en masse, which is tagged in different ways in PTB
  };

  private static final TsurgeonPattern MW_FLAT_OPERATION = Tsurgeon.parseOperation("[createSubtree FLAT node1 node2] [if exists node3 move node3 $- node2]");

  public static Tree MWFlatTransform(Tree t) {
    for (TregexPattern p : MW_FLAT_PATTERNS) {
      Tsurgeon.processPattern(p, MW_FLAT_OPERATION, t);
    }

    return t;
  }

  private static final TregexPattern FLAT_PREP_CC_PATTERN = TregexPattern.compile("PP <, (/^(IN|TO)$/=p1 $+ (CC=cc $+ /^(IN|TO)$/=p2))");
  private static final TsurgeonPattern FLAT_PREP_CC_OPERATION = Tsurgeon.parseOperation("[createSubtree PCONJP p1 cc] [move p2 $- cc]");
  
  public static Tree prepCCTransform(Tree t) {
    
    Tsurgeon.processPattern(FLAT_PREP_CC_PATTERN, FLAT_PREP_CC_OPERATION, t);

    return t;
  }

  private static final TregexPattern GAPPING_PATTERN = TregexPattern.compile("/^[^G].*/=gphrase < (/^[^V].*-ORPH.*/ $ /^[^V].*-ORPH.*/)");
  private static final TsurgeonPattern GAPPING_OPERATION = Tsurgeon.parseOperation("[adjoinH (GP (GAPPINGP@ )) gphrase] ");


  public static Tree gappingTransform(Tree t) {

    Tsurgeon.processPattern(GAPPING_PATTERN, GAPPING_OPERATION, t);

    return t;
  }

  public static void main(String[] args) {

    CoordinationTransformer transformer = new CoordinationTransformer(null);
    Treebank tb = new MemoryTreebank();
    Properties props = StringUtils.argsToProperties(args);
    String treeFileName = props.getProperty("treeFile");

    if (treeFileName != null) {
      try {
        TreeReader tr = new PennTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(treeFileName))), new LabeledScoredTreeFactory());
        for (Tree t ; (t = tr.readTree()) != null; ) {
          tb.add(t);
        }
      } catch (IOException e) {
        throw new RuntimeException("File problem: " + e);
      }
    }

    for (Tree t : tb) {
      System.out.println("Original tree");
      t.pennPrint();
      System.out.println();
      System.out.println("Tree transformed");
      Tree tree = transformer.transformTree(t);
      tree.pennPrint();
      System.out.println();
      System.out.println("----------------------------");
    }
  }

}
