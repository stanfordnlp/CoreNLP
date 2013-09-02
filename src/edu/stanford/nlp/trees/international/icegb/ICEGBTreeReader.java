package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeReader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * An <code>ICEGBTreeReader</code> is a <code>TreeReader</code> that
 * reads in ICE-GB-style files.
 *
 * @author Pi-Chuan Chang
 */

public final class ICEGBTreeReader implements TreeReader {

  private static final boolean debug = false;

  private Reader in;
  private ICEGBTokenizer tok;
  private static ICEGBTokenizer treeTok;
  private TreeFactory tf;
  private ICEGBLabelFactory lf;

  boolean start = false;

  boolean stop = false;
  boolean first = true;

  /**
   * Read parse trees from a <code>Reader</code>.
   * For the defaulted arguments, you get a
   * <code>LabeledScoredTreeFactory</code>, no <code>TreeNormalizer</code>, and
   * a <code>ICEGBTokenizer</code>.
   *
   * @param in The <code>Reader</code>
   */
  public ICEGBTreeReader(Reader in) {
    this.in = in;
    tok = new ICEGBTokenizer(in);
    tf = new LabeledScoredTreeFactory();
    lf = new ICEGBLabelFactory();
  }

  /**
   * Read parse trees from a <code>Reader</code>.  It uses the
   * the ICEGBTokenizer as a default
   *
   * @param in the Reader
   * @param tf TreeFactory -- factory to create some kind of Tree
   */
  public ICEGBTreeReader(Reader in, TreeFactory tf) {
    this.in = in;
    tok = new ICEGBTokenizer(in);
    this.tf = tf;
    lf = new ICEGBLabelFactory();
  }

  /**
   * Closes the underlying reader.
   */
  public void close() throws IOException {
    in.close();
  }


  static ICEGBToken current = null;

  /**
   * @return The filterd String representation of a ICE-GB tree
   */
  private String printTree() throws IOException {
    boolean ignore = false;
    boolean start = false;
    boolean inSquareBracket = false;
    StringBuilder treeBuffer = new StringBuilder();
    StringBuilder lineBuffer = new StringBuilder();

    if (!tok.hasNext()) {
      return null;
    }

    while (tok.hasNext()) {
      ICEGBToken token = tok.next();
      /* --------------------------------------------------------*/
      // this tree is done
      if (token.type == ICEGBLexer.SEPARATE) {
        // process this line (node)
        String line = lineBuffer.toString();
        line = line.replaceAll("[\\n\\r]", "");
        if (line.length() > 0) {
          treeBuffer.append(line);
          treeBuffer.append("\n");
        }
        break;
      }

      // this file is done. Return the last NULL tree as an indicator
      if (token.type == ICEGBLexer.YYEOF) {
        return null;
      }
      /* --------------------------------------------------------*/
      switch (token.type) {
        case ICEGBLexer.LSB:
          // if it's not in the form of "{[}" or "{]}" ...
          if (current == null || (current.type != ICEGBLexer.LBR || tok.peek().type != ICEGBLexer.RBR)) {
            inSquareBracket = true;
          }
          if (start && !inSquareBracket) {
            lineBuffer.append(token.text);
          }
          break;
        case ICEGBLexer.RSB:
          if (start && !inSquareBracket) {
            lineBuffer.append(token.text);
          }
          // if it's not in the form of "{[}" or "{]}" ...
          if (current.type != ICEGBLexer.LBR || tok.peek().type != ICEGBLexer.RBR) {
            inSquareBracket = false;
          }
          break;
        case ICEGBLexer.TAG:
          if (token.text.startsWith("<#X")) // when this unit contains <#Xdd:d> markup
          {
            ignore = true;
          }
          if (start && !inSquareBracket) {
            lineBuffer.append(token.text);
          }
          break;
        case ICEGBLexer.STRING:
          if (token.text.equals("PU")) {
            start = true;
          }
          if (start && !inSquareBracket) {
            lineBuffer.append(token.text);
          }
          break;
        default:
          if (start && !inSquareBracket) {
            lineBuffer.append(token.text);
          }
      }
      /* --------------------------------------------------------*/
      // a line is finished. Since a line represents a node in the tree, let's process this line
      if (token.type == ICEGBLexer.LINEEND) {
        // process this line (node)
        String line = lineBuffer.toString();
        line = line.replaceAll("[\\n\\r]", "");
        if (line.length() > 0) {
          treeBuffer.append(line);
          treeBuffer.append("\n");
        }
        lineBuffer = new StringBuilder();
      }
      /* --------------------------------------------------------*/
      current = token;
    }
    if (ignore) {
      // this unit should be ignored because it's <#Xdd:d> format
      return printTree();
    }
    if (!start && treeBuffer.length() == 0) {
      // no Parsing Unit (PU), go on to next tree
      return printTree();
    }
    return treeBuffer.toString();
  }


  /**
   * Reads a single tree in ICE-GB format and adds
   * a ROOT node
   *
   * @return A single tree, or <code>null</code> at end of file.
   */
  public Tree readTree() throws IOException {
    // pre-process the tree
    String treeStr = printTree();
    if (treeStr == null || treeStr.length() == 0) {
      return null;
    }
    // now treeStr contains the content of a tree, all the unnecessary stuffs are ripped off
    // tokenize it again with ICEGBTokenizer
    if (debug) {
      System.err.println(treeStr);
      System.err.println("----------");
    }
    treeTok = new ICEGBTokenizer(new StringReader(treeStr));

    Tree t = readTree(0);
    if (t!= null && t.label().value().equals("EMPTY")) // ignore the "EMPTY" tree
      return readTree();

    if (t == null) {
      return null;
    } else { // attach a "ROOT" to the front
      List<Tree> l = new ArrayList<Tree>();
      l.add(t);
      return tf.newTreeNode(new StringLabel("ROOT"), l);
    }
    //return readTree(0);
  }

  private Tree readTree(int baseLevel) throws IOException {
    //String line;
    //int level = -1;
    //int baseLevel = -1;

    Label nodeLabel = getNextNodeLabel();
    // the node is "ignore" or "UNTAG"
    if (nodeLabel == null)
      return null;
    
    //System.err.println(baseLevel+":\t"+nodeLabel);
    List<Tree> children = readTrees(baseLevel + 1);
    if (children.size() > 0) {
      return tf.newTreeNode(nodeLabel, children);
    } else {
      return tf.newLeaf(nodeLabel);
    }
  }

  private List<Tree> readTrees(int baseLevel) throws IOException {
    List<Tree> trees = new ArrayList<Tree>();
    int level = -1;

    while ((level = peekNextLevel(baseLevel)) == baseLevel) {
      Tree t = readTree(baseLevel);
      if (t != null) {
        trees.add(t);
      }
    }
    return trees;
  }

  private int peekNextLevel(int preLevel) {
    int level = -2;
    if (treeTok.hasNext()) {
      ICEGBToken p = treeTok.peek();
      if (p.type == ICEGBLexer.WHITESPACE) {
        level = p.text.length();
      } else if (p.text.equals("PU")) { //p.type == ICEGBLexer.STRING && p.text.equals("PU")) {
        level = 0; //the first level
      } else if (p.type == ICEGBLexer.LBR) { // the next token is a word (terminal), so return preLevel+1
        level = preLevel;
      } else {
        System.err.println("!!!! ERROR !! the first token of a line must be WHITESPACE or PU!!!!!!");
      }
    }
    //System.err.println("peek:\t"+level);
    return level;
  }

  // could return ICEGBLabel or StringLabel
  private Label getNextNodeLabel() {
    Label l = null;
    StringBuilder str = new StringBuilder();
    boolean leafNode = false;

    // peek the first one
    if (treeTok.hasNext()) {
      ICEGBToken p = treeTok.peek();
      if (p.type == ICEGBLexer.LBR) {
        leafNode = true;
      }
    }

    while (treeTok.hasNext()) {
      ICEGBToken p = treeTok.peek();
      // if the first token is not LBR (i.e., leafNode==false), 
      // but we see a LBR, means the current tree node is terminated
      if (p.type == ICEGBLexer.LBR && !leafNode) {
        break;
      } else {
        p = treeTok.next();
        if (p.type == ICEGBLexer.LINEEND) {
          break;
        } else {
          str.append(p.text);
        }
      }
    }
    
    if (str.length() > 0) {
      l = lf.newLabel(str.toString());
    }

    // check for UNTAG and ignore
    if (l instanceof ICEGBLabel) {
      if (((ICEGBLabel)l).features().contains("ignore") || ((ICEGBLabel)l).category().equals("UNTAG")) {
        // ignore all the way to the end of this line
        while(treeTok.hasNext()) {
          ICEGBToken t = treeTok.next();
          if (t.type == ICEGBLexer.LINEEND) {
            break;
          }
        }
        return null;
      }
    }
    
    return l;
  }
    
  private static void outputTree(Tree t) {
    if (t != null) {
      outputTree(t, 0);
    }
  }

  private static void outputTree(Tree t, int pad) {
    for (int i = 0; i < pad; i++) {
      System.err.print(" ");
    }
    System.err.println(t.label());
    for (Tree c : t.children()) {
      outputTree(c, pad + 1);
    }
  }

  /**
   * Loads data from first argument and prints it.
   *
   * @param args Array of command-line arguments: specifies a filename
   */
  public static void main(String[] args) {
    try {
      Tree t;
      ICEGBTreeReader tr = new ICEGBTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8")));
      t = tr.readTree();
      if (debug) outputTree(t);

      System.err.println("===================================");
      while (t != null) {
        //t.pennPrint();
        //ICEGBHeadFinder hf = new ICEGBHeadFinder();
        //Tree hd = hf.determineHead(t);
        //hd.pennPrint();
        t = tr.readTree();
        if (debug) outputTree(t);
        System.err.println("===================================");
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

  }

  /**
   * Finds the level of indentation of the current line
   */
  public int indentOf(String str) {
    int i = 0;
    String[] ss = str.split(" ");
    while (ss[i].equals("")) {
      i++;
    }
    return i;
  }

  /**
   * Tests whether the current line contains a leaf
   */
  public boolean isLeaf(String str) {
    if (str.matches(".*\\{.*")) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Gets the leaf node from the current line
   */
  public String getLeaf(String str) {
    String leaf1 = str.replaceAll(".*\\{", "");
    String leaf = leaf1.replaceAll("\\}", "");
    return leaf;
  }

  public String getNode(String str) {
    String node1 = str.replaceAll("\\(.*", "");
    String node2 = node1.replaceAll("^ *", "");
    String node = node2.trim();
    return node;
  }
}
