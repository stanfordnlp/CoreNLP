package edu.stanford.nlp.trees;


import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Coordination transformer transforms a PennTreebank tree containing a coordination in a flat structure
 * in order to get the dependencies right.
 *
 * @author Marie-Catherine de Marneffe
 */
public class CoordinationTransformer implements TreeTransformer {

  private static final boolean VERBOSE = false;
  private final TreeTransformer tn = new DependencyTreeTransformer(); //to get rid of unwanted nodes and tag
  private final TreeTransformer qp = new QPTreeTransformer();         //to restructure the QP constituents
  private final TreeTransformer dates = new DateTreeTransformer();    //to flatten date patterns


  // default constructor
  public CoordinationTransformer() {
  }

  /**
   * Transforms t if it contains a coordination in a flat structure (CCtransform)
   * and transforms UCP (UCPtransform).
   *
   * @param t a tree to be transformed
   * @return t transformed
   */
  public Tree transformTree(Tree t) {
    if (VERBOSE) {
      System.err.println("Input to CoordinationTransformer: " + t);
    }
    Tree tx = tn.transformTree(t);
    if (VERBOSE) {
      System.err.println("After DependencyTreeTransformer:  " + tx);
    }
    if (tx == null) {
      return tx;
    }
    Tree tt = UCPtransform(tx);
    if (VERBOSE) {
      System.err.println("After UCPTransformer:             " + t);
    }
    Tree ttt = CCtransform(tt);
    if (VERBOSE) {
      System.err.println("After CCTransformer:              " + t);
    }
    Tree tttt = qp.transformTree(ttt);
    if (VERBOSE) {
      System.err.println("After QPTreeTransformer:          " + t);
    }
    Tree ret = dates.transformTree(tttt);
    if (VERBOSE) {
      System.err.println("After DateTreeTransformer:        " + t);
    }
    return ret;
  }


  private static final TregexPattern[][] matchPatterns = {
    {
      // UCP (JJ ...) -> ADJP
      TregexPattern.safeCompile("/^UCP/=ucp <, /^JJ|ADJP/", true),
      // UCP (DT JJ ...) -> ADJP
      TregexPattern.safeCompile("/^UCP/=ucp <, (DT $+ /^JJ|ADJP/)", true)
    },
    {
      // UCP (N ...) -> NP
      TregexPattern.safeCompile("/^UCP/=ucp <, /^N/", true),
      TregexPattern.safeCompile("/^UCP/=ucp <, (DT $+ /^N/)", true)
    }
  };

  private static final TsurgeonPattern[] operations = {
    Tsurgeon.parseOperation("relabel ucp /^UCP(.*)$/ADJP$1/"),
    Tsurgeon.parseOperation("relabel ucp /^UCP(.*)$/NP$1/"),
  };

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
    Tree firstChild = t.firstChild();
    if (firstChild != null) {
      List<Pair<TregexPattern,TsurgeonPattern>> ops = Generics.newArrayList();

      for (int i = 0; i < operations.length; i++) {
        for (TregexPattern pattern : matchPatterns[i]) {
          ops.add(Generics.newPair(pattern, operations[i]));
        }
      }

      return Tsurgeon.processPatternsOnTree(ops, t);
    } else {
      return t;
    }
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
      System.err.println("transformCC in:  " + t);
    }
    //System.out.println(ccIndex);
    // use the factories of t to create new nodes
    TreeFactory tf = t.treeFactory();
    LabelFactory lf = t.label().labelFactory();

    Tree[] ccSiblings = t.children();

    //check if other CC
    List<Integer> ccPositions = new ArrayList<Integer>();
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
        if (VERBOSE) {System.err.println("more CC index " +  index);}
        if (ccSiblings[index - 1].value().equals(",")) {//to handle the case of a comma ("soya and maize oil, and vegetables")
          index = index - 1;
          comma = true;
        }
        if (VERBOSE) {System.err.println("more CC index " +  index);}
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
      System.err.println("transformCC out: " + t);
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
        if (parent.value().startsWith("NP")) {
          List<Tree> children = parent.getChildrenAsList();
          //System.out.println(children);
          int ccIndex = children.indexOf(t);
          if (children.size() > ccIndex + 2 && notNP(children, ccIndex) && ccIndex != 0 && (ccIndex == children.size() - 1 || !children.get(ccIndex+1).value().startsWith("CC"))) {
            transformCC(parent, ccIndex);
            if (VERBOSE) {
              System.err.println("After transformCC:             " + root);
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


  public static void main(String[] args) {

    CoordinationTransformer transformer = new CoordinationTransformer();
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
