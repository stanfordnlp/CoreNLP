package edu.stanford.nlp.trees;



import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.util.StringUtils;

import java.util.List;
import java.util.Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Transforms an English structure parse tree in order to get the dependencies right:
 * Adds an extra structure in QP phrases:
 * (QP (RB well) (IN over) (CD 9)) becomes
 *
 * (QP (XS (RB well) (IN over)) (CD 9))
 *
 *
 * @author mcdm
 */
public class QPTreeTransformer implements TreeTransformer {

  /**
   * Right now (July 2007) we only deal with the following QP structures:
   *
   * QP (RB IN CD|DT ...)   well over, more than
   * QP (JJR IN CD|DT ...)  fewer than
   * QP (IN JJS CD|DT ...)  at least
   *
   * @param t tree to be transformed
   * @return  t with an extra layer if there was a QP structure matching the ones mentioned above
   */

  public Tree transformTree(Tree t) {
     return QPtransform(t);
  }


  /**
   * Transforms t if it contains one of the following QP structure:
   * QP (RB IN CD|DT ...)   well over, more than
   * QP (JJR IN CD|DT ...)  fewer than
   * QP (IN JJS CD|DT ...)  at least
   *
   * @param t a tree to be transformed
   * @return t transformed
   */
  public static Tree QPtransform(Tree t) {
    doTransform(t);
    return t;
  }



  /*
   * Given a tree t, if this tree contains a QP of the form
   * QP (RB IN CD|DT ...)   well over, more than
   * QP (JJR IN CD|DT ...)  fewer than
   * QP (IN JJS CD|DT ...)  at least
   *
   * it will transform it
   *
   */
  private static void doTransform(Tree t) {

    if (t.value().startsWith("QP")) {
      //look at the children
      List<Tree> children = t.getChildrenAsList();
      if (children.size() >= 3 && children.get(0).isPreTerminal()) {
        //go through the children and check if they match the structure we want
        String child1 = children.get(0).value();
        String child2 = children.get(1).value();
        String child3 = children.get(2).value();
        if((child3.startsWith("CD") || child3.startsWith("DT")) &&
           (child1.startsWith("RB") || child1.startsWith("JJ") || child1.startsWith("IN")) &&
           (child2.startsWith("IN") || child2.startsWith("JJ"))) {
          transformQP(t);
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
      for (Tree child : t.getChildrenAsList()) {
        doTransform(child);
      }
    }
  }


  private static void transformQP(Tree t) {
    List<Tree> children = t.getChildrenAsList();
    TreeFactory tf = t.treeFactory();
    LabelFactory lf = t.label().labelFactory();

    //create the new XS having the first two children of the QP
    Tree left = tf.newTreeNode(lf.newLabel("XS"), null);
    for (int i = 0; i < 2; i++) {
      left.addChild(children.get(i));
    }

    // remove all the two first children of t before
    for (int i = 0; i < 2; i++) {
      t.removeChild(0);
    }

    // add XS as the first child
    t.addChild(0, left);
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
