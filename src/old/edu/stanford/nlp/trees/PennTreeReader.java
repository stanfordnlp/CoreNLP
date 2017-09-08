package old.edu.stanford.nlp.trees;

import java.io.*;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Stack;

import old.edu.stanford.nlp.process.Tokenizer;
import old.edu.stanford.nlp.ling.HasIndex;

/**
 * This class implements the <code>TreeReader</code> interface to read Penn Treebank-style 
 * files. The reader is implemented as a pushdown automaton (PDA) that parses the Lisp-style 
 * format in which the trees are stored. This reader is compatible with both PTB
 * and PATB trees. 
 *
 * @author Christopher Manning
 * @author Roger Levy
 * @author Spence Green
 */
public class PennTreeReader implements TreeReader {

  private final Reader reader;
  private final Tokenizer<String> tokenizer;
  private final TreeNormalizer treeNormalizer;
  private final TreeFactory treeFactory;

  private static final boolean DEBUG = false;

  private Tree currentTree = null;
  private Stack<Tree> stack = null;
  private static final String leftParen = "(";
  private static final String rightParen = ")";

  /**
   * Read parse trees from a <code>Reader</code>.
   * For the defaulted arguments, you get a
   * <code>SimpleTreeFactory</code>, no <code>TreeNormalizer</code>, and
   * a <code>PennTreebankTokenizer</code>.
   *
   * @param in The <code>Reader</code>
   */
  public PennTreeReader(Reader in) {
    this(in, new SimpleTreeFactory());
  }


  /**
   * Read parse trees from a <code>Reader</code>.
   *
   * @param in the Reader
   * @param tf TreeFactory -- factory to create some kind of Tree
   */
  public PennTreeReader(Reader in, TreeFactory tf) {
    this(in, tf, null, new PennTreebankTokenizer(in));
  }

  /**
   * Read parse trees from a <code>Reader</code>.
   *
   * @param in The Reader
   * @param st The Tokenizer
   */
  public PennTreeReader(Reader in, Tokenizer<String> st) {
    this(in, new SimpleTreeFactory(), null, st);
  }


  /**
   * Read parse trees from a Reader.
   *
   * @param in Reader
   * @param tf TreeFactory -- factory to create some kind of Tree
   * @param tn the method of normalizing trees
   */
  public PennTreeReader(Reader in, TreeFactory tf, TreeNormalizer tn) {
    this(in, tf, tn, new PennTreebankTokenizer(in));
  }


  /**
   * Read parse trees from a Reader.
   *
   * @param in Reader
   * @param tf TreeFactory -- factory to create some kind of Tree
   * @param tn the method of normalizing trees
   * @param st Tokenizer that divides up Reader
   */
  public PennTreeReader(Reader in, TreeFactory tf, TreeNormalizer tn, Tokenizer<String> st) {
    reader = in;
    treeFactory = tf;
    treeNormalizer = tn;
    tokenizer = st;

    // check for whacked out headers still present in Brown corpus in Treebank 3
    String first = (st.hasNext() ? st.peek() : null);
    if (first != null && first.startsWith("*x*x*x")) {
      if (DEBUG) {
        System.err.printf("%s: Skipping past whacked out header (%s)\n",this.getClass().getName(),first);
      }
      int foundCount = 0;
      while (foundCount < 4 && st.hasNext()) {
        first = st.next();
        if (first != null && first.startsWith("*x*x*x")) {
          foundCount++;
        }
      }
    }

    if (DEBUG) {
      System.err.printf("%s: Built from\n %s ", this.getClass().getName(), in.getClass().getName());
      System.err.println(" " + ((tf == null) ? "no tf" : tf.getClass().getName()));
      System.err.println(" " + ((tn == null) ? "no tn" : tn.getClass().getName()));
      System.err.println(" " + ((st == null) ? "no st" : st.getClass().getName()));
    }
  }

  /**
   * Reads a single tree in standard Penn Treebank format from the
   * input stream. The method supports additional parentheses around the
   * tree (an unnamed ROOT node) so long as they are balanced. If the token stream 
   * ends before the current tree is complete, then the method will throw an
   * <code>IOException</code>.
   * <p>
   * Note that the method will skip malformed trees and attempt to
   * read additional trees from the input stream. It is possible, however,
   * that a malformed tree will corrupt the token stream. In this case,
   * an <code>IOException</code> will eventually be thrown.
   * 
   * @return A single tree, or <code>null</code> at end of token stream.
   */
  public Tree readTree() throws IOException {
    Tree t = null;  

    while(tokenizer.hasNext() && t == null) {

      //Setup PDA
      this.currentTree = null;
      this.stack = new Stack<Tree>();

      try {
        t = getTreeFromInputStream();
      } catch (NoSuchElementException e) {
        throw new IOException("End of token stream encountered before parsing could complete.");
      }

      if(t == null)
        System.err.printf("%s: Skipping malformed tree\n", this.getClass().getName());
      else if(treeNormalizer != null && treeFactory != null)
        t = treeNormalizer.normalizeWholeTree(t, treeFactory);
    }      

    return t;
  }


  private Tree getTreeFromInputStream() throws NoSuchElementException {
    int wordIndex = 0;

    //FSA
    while(tokenizer.hasNext()) {
      String token = tokenizer.next();

      if(token.equals(leftParen)) {
        
        String label = (tokenizer.peek().equals(leftParen)) ? "" : tokenizer.next();
        if(label.equals(rightParen)) //Skip past empty trees
          continue;
        else if(label.length() != 0 && treeNormalizer != null)
            label = treeNormalizer.normalizeNonterminal(label);

        Tree newTree = treeFactory.newTreeNode(label, new ArrayList<Tree>());
        if(currentTree == null)
          stack.push(newTree);
        else {
          currentTree.addChild(newTree);
          stack.push(currentTree);
        }

        currentTree = newTree;

      } else if(token.equals(rightParen)) {
        if(stack.isEmpty()) break;
        
        //Accept
        currentTree = stack.pop();
        if(stack.isEmpty()) return currentTree;

      } else {
        
        if(currentTree == null) break;

        String terminal = (treeNormalizer == null) ? token : treeNormalizer.normalizeTerminal(token);
        Tree leaf = treeFactory.newLeaf(terminal);
        if (leaf.label() instanceof HasIndex) {
          HasIndex hi = (HasIndex) leaf.label();
          hi.setIndex(wordIndex);
        }
        wordIndex++;

        currentTree.addChild(leaf);
      }
    }

    //Reject
    return null;
  }


  /**
   * Closes the underlying <code>Reader</code> used to create this
   * class.
   */
  public void close() throws IOException {
    reader.close();
  }


  /**
   * Loads treebank data from first argument and prints it.
   *
   * @param args Array of command-line arguments: specifies a filename
   */
  public static void main(String[] args) {
    try {
      TreeFactory tf = new LabeledScoredTreeFactory();
      Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
      TreeReader tr = new PennTreeReader(r, tf);
      Tree t = tr.readTree();
      while (t != null) {
        System.out.println(t);
        System.out.println();
        t = tr.readTree();
      }
      r.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

}
