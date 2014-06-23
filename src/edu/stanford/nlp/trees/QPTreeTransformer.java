package edu.stanford.nlp.trees;



import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Transforms an English structure parse tree in order to get the dependencies right:
 * Adds an extra structure in QP phrases:
 * <br>
 * (QP (RB well) (IN over) (CD 9)) becomes
 * <br>
 * (QP (XS (RB well) (IN over)) (CD 9))
 * <br>
 * (QP (...) (CC ...) (...)) becomes
 * <br>
 * (QP (NP ...) (CC ...) (NP ...))
 *
 *
 * @author mcdm
 */
public class QPTreeTransformer implements TreeTransformer {

  /**
   * Right now (Jan 2013) we only deal with the following QP structures:
   * <ul>
   * <li> NP (QP ...) (QP (CC and/or) ...)
   * <li> QP (RB IN CD|DT ...)   well over, more than
   * <li> QP (JJR IN CD|DT ...)  fewer than
   * <li> QP (IN JJS CD|DT ...)  at least
   * <li> QP (... CC ...)        between 5 and 10
   * </ul>
   *
   * @param t tree to be transformed
   * @return The tree t with an extra layer if there was a QP structure matching the ones mentioned above
   */
  @Override
  public Tree transformTree(Tree t) {
     return QPtransform(t);
  }


  private static TregexPattern flattenNPoverQPTregex =
    TregexPattern.compile("NP < (QP=left $+ (QP=right < CC))");

  private static TsurgeonPattern flattenNPoverQPTsurgeon =
    Tsurgeon.parseOperation("[createSubtree QP left right] [excise left left] [excise right right]");

  private static TregexPattern multiwordXSTregex =
    // TODO: should add NN and $ to the numeric expressions captured
    //   NN is for words such as "half" which are probably misparsed
    // TODO: <3 (IN < as|than) is to avoid one weird case in PTB, 
    // "more than about".  Perhaps there is some way to generalize this
    // TODO: "all but X"
    // TODO: "all but about X"
    TregexPattern.compile("QP <1 /^RB|JJ|IN/=left [ ( <2 /^JJ|IN/=right <3 /^CD|DT/ ) | ( <2 /^JJ|IN/ <3 ( IN=right < /^(?i:as|than)$/ ) <4 /^CD|DT/ ) ] ");

  private static TsurgeonPattern multiwordXSTsurgeon =
    Tsurgeon.parseOperation("createSubtree XS left right");

  /**
   * Transforms t if it contains one of the following QP structure:
   * <ul>
   * <li> NP (QP ...) (QP (CC and/or) ...)
   * <li> QP (RB IN CD|DT ...)   well over, more than
   * <li> QP (JJR IN CD|DT ...)  fewer than
   * <li> QP (IN JJS CD|DT ...)  at least
   * <li> QP (... CC ...)        between 5 and 10
   * </ul>
   *
   * @param t a tree to be transformed
   * @return t transformed
   */
  public static Tree QPtransform(Tree t) {
    t = Tsurgeon.processPattern(flattenNPoverQPTregex, flattenNPoverQPTsurgeon, t);
    t = Tsurgeon.processPattern(multiwordXSTregex, multiwordXSTsurgeon, t);

    doTransform(t);
    return t;
  }

  /**
   * Given a tree t, if this tree contains a QP of the form
   * QP (... CC ...)        between 5 and 10
   * it will transform it
   *
   */
  private static void doTransform(Tree t) {

    if (t.value().startsWith("QP")) {
      //look at the children
      List<Tree> children = t.getChildrenAsList();
      // If the children include a CC, we split that into left and
      // right subtrees with the CC in the middle so the headfinders
      // have an easier time interpreting the tree later on
      if (children.size() >= 3) {
        boolean isFlat = isFlat(children);
        if (isFlat) {
          for (int i = 1; i < children.size() - 1; ++i) {
            if (children.get(i).value().startsWith("CC")) {
              transformCC(t, children.subList(0, i), children.get(i), children.subList(i + 1, children.size()));
              children = t.getChildrenAsList();
              isFlat = false;
              break;
            }
          }
        }

        if (isFlat) {
          boolean isMoney = children.get(0).value().startsWith("$");
          if (isMoney) {
            for (int i = 1; i < children.size(); ++i) {
              if (!children.get(i).value().startsWith("CD")) {
                isMoney = false;
                break;
              }
            }
          }
          if (isMoney) {
            transformMoney(t, children);
          }
        }
      }
    /* --- to be written or deleted
    } else if (t.value().startsWith("NP")) {
      //look at the children
      List<Tree> children = t.getChildrenAsList();
      if (children.size() >= 3) {

      }
    ---- */
    } else if (t.isPhrasal()) {
      for (Tree child : t.children()) {
        doTransform(child);
      }
    }
  }

  private static boolean isFlat(List<Tree> children) {
    for (int i = 0; i < children.size(); ++i) {
      if (!children.get(i).isPreTerminal()) {
        return false;
      }
    }
    return true;
  }

  private static void transformCC(Tree t, List<Tree> left, Tree conj, List<Tree> right) {
    TreeFactory tf = t.treeFactory();
    LabelFactory lf = t.label().labelFactory();
    Tree leftQP = tf.newTreeNode(lf.newLabel("NP"), left);
    Tree rightQP = tf.newTreeNode(lf.newLabel("NP"), right);
    List<Tree> newChildren = new ArrayList<Tree>();
    newChildren.add(leftQP);
    newChildren.add(conj);
    newChildren.add(rightQP);
    t.setChildren(newChildren);
  }

  private static void transformMoney(Tree t, List<Tree> children) {
    TreeFactory tf = t.treeFactory();
    LabelFactory lf = t.label().labelFactory();
    Tree rightQP = tf.newTreeNode(lf.newLabel("QP"), children.subList(1, children.size()));
    List<Tree> newChildren = new ArrayList<Tree>();
    newChildren.add(children.get(0));
    newChildren.add(rightQP);
    t.setChildren(newChildren);
  }


  public static void main(String[] args) {

    QPTreeTransformer transformer = new QPTreeTransformer();
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
