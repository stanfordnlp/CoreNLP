package old.edu.stanford.nlp.trees;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import old.edu.stanford.nlp.ling.StringLabel;
import old.edu.stanford.nlp.trees.tregex.TregexPattern;
import old.edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import old.edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import old.edu.stanford.nlp.util.Generics;
import old.edu.stanford.nlp.util.Pair;
import old.edu.stanford.nlp.util.StringUtils;

/**
 * Coordination transformer transforms a PennTreebank tree containing a coordination in a flat structure
 * in order to get the dependencies right
 *
 * @author Marie-Catherine de Marneffe
 */
public class CoordinationTransformer implements TreeTransformer {

  boolean VERBOSE = false;
  boolean notDone = true;
  TreeTransformer tn = new DependencyTreeTransformer(); //to get rid of unwanted nodes and tag
  TreeTransformer qp = new QPTreeTransformer();         //to restructure the QP constituents
  // default constructor
  public CoordinationTransformer() {

  }

  /**
   * Transforms t if it contains a coordination in a flat structure (CCtransform)
   * and transforms UCP (UCPtransform)
   *
   * @param t a tree to be transformed
   * @return t transformed
   */
  public Tree transformTree(Tree t) {
    tn.transformTree(t);
    Tree tt = UCPtransform(t);
    Tree ttt = CCtransform(tt);
    return qp.transformTree(ttt);
  }


  private static final TregexPattern[][] matchPatterns = {
    {
      // UCP (JJ ...) -> ADJP
      TregexPattern.safeCompile("UCP=ucp <, /^JJ|ADJP/", true),
      // UCP (DT JJ ...) -> ADJP
      TregexPattern.safeCompile("UCP=ucp <, (DT $+ /^JJ|ADJP/)", true)
    },
    {
      // UCP (N ...) -> NP
      TregexPattern.safeCompile("UCP=ucp <, /^N/", true),
      TregexPattern.safeCompile("UCP=ucp <, (DT $+ /^N/)", true)
    }
  };

  private static final TsurgeonPattern[] operations = {
    Tsurgeon.parseOperation("relabel ucp ADJP"),
    Tsurgeon.parseOperation("relabel ucp NP"),
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
   * @return t transformed
   */
  public Tree CCtransform(Tree t) {
    notDone = true;
    Tree toReturn = new LabeledScoredTreeNode();
    while (notDone) {
      Tree cc = findCCparent(t, t);
      if (cc != null) {
        toReturn = cc;
        t = cc;
      } else {
        notDone = false;
        toReturn = t;
      }
    }
    return toReturn;
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

  private Tree transformCC(Tree t, int ccIndex) {
    //System.out.println(t);
    //System.out.println(ccIndex);

    Tree[] ccSiblings = t.children();

    //check if other CC
    List<Integer> list = new ArrayList<Integer>();
    for (int i = ccIndex + 1; i < ccSiblings.length; i++) {
      if (ccSiblings[i].value().startsWith("CC")) {
        list.add(Integer.valueOf(i));
      }
    }

    // a CC b c ... -> (a CC b) c ...
    if (ccIndex == 1 && !(ccSiblings[ccIndex - 1].value().equals("NP") || ccSiblings[ccIndex - 1].value().equals("ADJP") || ccSiblings[ccIndex - 1].value().equals("NNS"))) {// && (ccSiblings.length == ccIndex + 3 || !list.isEmpty())) {  // something like "soya or maize oil"
      String leftHead = getHeadTag(ccSiblings[ccIndex - 1]);
      //create a new tree to be inserted as first child of t
      Tree left = new LabeledScoredTreeNode(new StringLabel(leftHead), null);
      for (int i = 0; i < ccIndex + 2; i++) {
        left.addChild(ccSiblings[i]);
      }

      if(VERBOSE) {
        System.out.println("print left tree");
        left.pennPrint();
        System.out.println();
      }

      // remove all the children of t before ccIndex+2
      for (int i = 0; i < ccIndex + 2; i++) {
        t.removeChild(0);
      }

      // if stuff after (like "soya or maize oil and vegetables")
      // we need to put the tree in another tree
      if (!list.isEmpty()) {
        boolean comma = false;
        int index = list.get(0);
        if (VERBOSE) {System.err.println("more CC index " +  index);}
        if (ccSiblings[index - 1].value().equals(",")) {//to handle the case of a coma ("soya and maize oil, and vegetables")
          index = index - 1;
          comma = true;
        }
        if (VERBOSE) {System.err.println("more CC index " +  index);}
        String head = getHeadTag(ccSiblings[index - 1]);
        Tree tree = new LabeledScoredTreeNode(new StringLabel(head), null);
        tree.addChild(0, left);

        int k = 1;
        for(int j = ccIndex+2; j<index; j++) {
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
    }
    // DT a CC b c -> DT (a CC b) c
    else if (ccIndex == 2 && ccSiblings[0].value().startsWith("DT") && !ccSiblings[ccIndex - 1].value().equals("NNS") && (ccSiblings.length == 5 || (!list.isEmpty() && list.get(0) == 5))) {
      String head = getHeadTag(ccSiblings[ccIndex - 1]);
      //create a new tree to be inserted as second child of t (after the determiner
      Tree child = new LabeledScoredTreeNode(new StringLabel(head), null);
      for (int i = 1; i < ccIndex + 2; i++) {
        child.addChild(ccSiblings[i]);
      }
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
      Tree child = new LabeledScoredTreeNode(new StringLabel(head), null);
      for (int i = ccIndex - 3; i < ccIndex + 2; i++) {
        child.addChild(ccSiblings[i]);
      }
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
      Tree conjT = new LabeledScoredTreeNode(new StringLabel("CC"), null);
      // create the left tree
      String leftHead = getHeadTag(ccSiblings[ccIndex - 1]);
      Tree left = new LabeledScoredTreeNode(new StringLabel(leftHead), null);

      // handle the case of a preconjunct (either, both, neither)
      Tree first = ccSiblings[0];
      String leaf = first.children()[0].value().toLowerCase();
      if(leaf.equals("either") || leaf.equals("neither") || leaf.equals("both")) {
        preconj = true;
        indexBegin = 1;
        conjT.addChild(first.children()[0]);
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
      if (list.isEmpty()) {
        nextCC = ccSiblings.length;
      } else {
        nextCC = list.get(0);
      }
      String rightHead = getHeadTag(ccSiblings[nextCC - 1]);
      Tree right = new LabeledScoredTreeNode(new StringLabel(rightHead), null);
      for (int i = ccIndex + 1; i < nextCC - 1; i++) {
        right.addChild(ccSiblings[i]);
      }
      // handle the case of a comma ("GM soya and maize, and food ingredients")
      if (ccSiblings[nextCC - 1].value().equals(",")) {
        commaRight = true;
      } else {
        right.addChild(ccSiblings[nextCC - 1]);
      }

      // put trees together in old t, first we remove the old nodes
      for (int i = 0; i < nextCC; i++) {
        t.removeChild(0);
      }
      if (!list.isEmpty()) { // need an extra level
        Tree tree = new LabeledScoredTreeNode(new StringLabel("NP"), null);
        if(preconj) {
          tree.addChild(conjT);
        }
        tree.addChild(left);
        if (commaLeft) {
          tree.addChild(ccSiblings[ccIndex - 1]);
        }
        tree.addChild(cc);
        tree.addChild(right);
        if (commaRight) {
          t.addChild(0, ccSiblings[nextCC - 1]);
        }
        t.addChild(0, tree);
      } else {
         if(preconj) {
          t.addChild(conjT);
        }
        t.addChild(left);
        if (commaLeft) {
          t.addChild(ccSiblings[ccIndex - 1]);
        }
        t.addChild(cc);
        t.addChild(right);
        if (commaRight) {
          t.addChild(ccSiblings[nextCC - 1]);
        }
      }
    }

    return t;
  }

  private static boolean notNP(List<Tree> children, int ccIndex) {
    boolean toReturn = true;
    for (int i = ccIndex; i < children.size(); i++) {
      if (children.get(i).value().startsWith("NP")) {
        toReturn = false;
        break;
      }
    }
    return toReturn;
  }

  /*
   * Given a tree t, if this tree contains a CC inside a NP followed by 2 nodes
   * (i.e. we have a flat structure that will not work for the dependencies),
   * it will return the NP containing the CC and the index of the CC
   *
   */
  private Tree findCCparent(Tree t, Tree root) {
    if (t.isPreTerminal()) {
      if (t.value().startsWith("CC")) {
        Tree parent = t.parent(root);
        if (parent.value().startsWith("NP")) {
          List<Tree> children = parent.getChildrenAsList();
          //System.out.println(children);
          int ccIndex = children.indexOf(t);
          if (children.size() > ccIndex + 2 && notNP(children, ccIndex) && ccIndex != 0) {
            Tree newChild = transformCC(parent, ccIndex);
            parent = newChild;
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
        Tree t;
        while ((t = tr.readTree()) != null) {
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
