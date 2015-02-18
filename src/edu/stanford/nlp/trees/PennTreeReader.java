package edu.stanford.nlp.trees;

import java.io.*;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.HasWord;

/**
 * This class implements the <code>TreeReader</code> interface to read Penn Treebank-style
 * files. The reader is implemented as a push-down automaton (PDA) that parses the Lisp-style
 * format in which the trees are stored. This reader is compatible with both PTB
 * and PATB trees.
 * <br>
 * One small detail to note is that the <code>PennTreeReader</code>
 * silently replaces \* with * and \/ with /.  Two possible designs
 * for this were to make the <code>PennTreeReader</code> always do
 * this or to make the <code>TreeNormalizers</code> do this.  We
 * decided to put it in the <code>PennTreeReader</code> class itself
 * to avoid the problem of people making new
 * <code>TreeNormalizers</code> and forgetting to include the
 * unescaping.
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

  private Tree currentTree;
  // misuse a list as a stack, since we want to avoid the synchronized and old Stack, but don't need the power and JDK 1.6 dependency of a Deque
  private ArrayList<Tree> stack;
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
    this(in, new LabeledScoredTreeFactory());
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
      System.err.println(' ' + ((tf == null) ? "no tf" : tf.getClass().getName()));
      System.err.println(' ' + ((tn == null) ? "no tn" : tn.getClass().getName()));
      System.err.println(' ' + ((st == null) ? "no st" : st.getClass().getName()));
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
  @Override
  public Tree readTree() throws IOException {
    Tree t = null;

    while (tokenizer.hasNext() && t == null) {

      //Setup PDA
      this.currentTree = null;
      this.stack = new ArrayList<Tree>();

      try {
        t = getTreeFromInputStream();
      } catch (NoSuchElementException e) {
        throw new IOException("End of token stream encountered before parsing could complete.");
      }

      if (t != null) {
        // cdm 20100618: Don't do this!  This was never the historical behavior!!!
        // Escape empty trees e.g. (())
        // while(t != null && (t.value() == null || t.value().equals("")) && t.numChildren() <= 1)
        //   t = t.firstChild();

        if (treeNormalizer != null && treeFactory != null) {
          t = treeNormalizer.normalizeWholeTree(t, treeFactory);
        }
        t.indexLeaves(true);
      }
    }

    return t;
  }

  private static final Pattern STAR_PATTERN = Pattern.compile("\\\\\\*");
  private static final Pattern SLASH_PATTERN = Pattern.compile("\\\\/");


  private Tree getTreeFromInputStream() throws NoSuchElementException {
    int wordIndex = 1;

    // FSA
    while (tokenizer.hasNext()) {
      String token = tokenizer.next();

      if (token.equals(leftParen)) {

        // cdm 20100225: This next line used to have "" instead of null, but the traditional and current tree normalizers depend on the label being null not "" when there is no label on a tree (like the outermost English PTB level)
        String label = (tokenizer.peek().equals(leftParen)) ? null : tokenizer.next();
        if (rightParen.equals(label)) {//Skip past empty trees
          continue;
        } else if (treeNormalizer != null) {
          label = treeNormalizer.normalizeNonterminal(label);
        }

        if (label != null) {
          label = STAR_PATTERN.matcher(label).replaceAll("*");
          label = SLASH_PATTERN.matcher(label).replaceAll("/");
        }

        Tree newTree = treeFactory.newTreeNode(label, null); // dtrs are added below
        if(currentTree == null)
          stack.add(newTree);
        else {
          currentTree.addChild(newTree);
          stack.add(currentTree);
        }

        currentTree = newTree;

      } else if(token.equals(rightParen)) {
        if (stack.isEmpty()) {
          // Warn that file has too many right parens
          System.err.println("PennTreeReader: warning: file has extra non-matching right parenthesis [ignored]");
          break;
        }

        //Accept
        currentTree = stack.remove(stack.size() - 1);  // i.e., stack.pop()
        if (stack.isEmpty()) return currentTree;

      } else {

        if (currentTree == null) {
          // A careful Reader should warn here, but it's kind of useful to
          // suppress this because then the TreeReader doesn't print a ton of
          // messages if there is a README file in a directory of Trees.
          // System.err.println("PennTreeReader: warning: file has extra token not in a s-expression tree: " + token + " [ignored]");
          break;
        }

        String terminal = (treeNormalizer == null) ? token : treeNormalizer.normalizeTerminal(token);
        terminal = STAR_PATTERN.matcher(terminal).replaceAll("*");
        terminal = SLASH_PATTERN.matcher(terminal).replaceAll("/");
        Tree leaf = treeFactory.newLeaf(terminal);
        if(leaf.label() instanceof HasIndex) {
          HasIndex hi = (HasIndex) leaf.label();
          hi.setIndex(wordIndex);
        }
        if(leaf.label() instanceof HasWord) {
          HasWord hw = (HasWord) leaf.label();
          hw.setWord(leaf.label().value());
        }
        wordIndex++;

        currentTree.addChild(leaf);
        // cdm: Note: this implementation just isn't as efficient as the old recursive descent parser (see 2008 code), where all the daughters are gathered before the tree is made....
      }
    }

    //Reject
    if (currentTree != null) {
      System.err.println("PennTreeReader: warning: incomplete tree (extra left parentheses in input): " + currentTree);
    }
    return null;
  }


  /**
   * Closes the underlying <code>Reader</code> used to create this
   * class.
   */
  @Override
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
