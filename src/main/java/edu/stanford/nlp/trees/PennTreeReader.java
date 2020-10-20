package edu.stanford.nlp.trees; 
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;

/**
 * This class implements the {@code TreeReader} interface to read Penn Treebank-style
 * files. The reader is implemented as a push-down automaton (PDA) that parses the Lisp-style
 * format in which the trees are stored. This reader is compatible with both PTB
 * and PATB trees.
 * <br>
 * One small detail to note is that the {@code PennTreeReader}
 * silently replaces \* with * and \/ with /.  Two possible designs
 * for this were to make the {@code PennTreeReader} always do
 * this or to make the {@code TreeNormalizers} do this.  We
 * decided to put it in the {@code PennTreeReader} class itself
 * to avoid the problem of people making new
 * {@code TreeNormalizers} and forgetting to include the
 * unescaping.
 * <br>
 * Also removed are -LRB- and -RRB- as leaves.  A corresponding
 * re-escaping in the writers prints that back out.  This way, tools
 * such as the parser see () instead of -LRB- -RRB-.
 *
 * @author Christopher Manning
 * @author Roger Levy
 * @author Spence Green
 */
public class PennTreeReader implements TreeReader  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(PennTreeReader.class);

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
   * Read parse trees from a {@code Reader}.
   * For the defaulted arguments, you get a
   * {@code SimpleTreeFactory}, no {@code TreeNormalizer}, and
   * a {@code PennTreebankTokenizer}.
   *
   * @param in The {@code Reader}
   */
  public PennTreeReader(Reader in) {
    this(in, new LabeledScoredTreeFactory());
  }


  /**
   * Read parse trees from a {@code Reader}.
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
        System.err.printf("%s: Skipping past whacked out header (%s)%n",this.getClass().getName(),first);
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
      System.err.printf("%s: Built from%n %s ", this.getClass().getName(), in.getClass().getName());
      log.info(' ' + ((tf == null) ? "no tf" : tf.getClass().getName()));
      log.info(' ' + ((tn == null) ? "no tn" : tn.getClass().getName()));
      log.info(' ' + ((st == null) ? "no st" : st.getClass().getName()));
    }
  }

  /**
   * Reads a single tree in standard Penn Treebank format from the
   * input stream. The method supports additional parentheses around the
   * tree (an unnamed ROOT node) so long as they are balanced. If the token stream
   * ends before the current tree is complete, then the method will throw an
   * {@code IOException}.
   * <p>
   * Note that the method will skip malformed trees and attempt to
   * read additional trees from the input stream. It is possible, however,
   * that a malformed tree will corrupt the token stream. In this case,
   * an {@code IOException} will eventually be thrown.
   *
   * @return A single tree, or {@code null} at end of token stream.
   */
  @Override
  public Tree readTree() throws IOException {
    Tree t = null;

    while (tokenizer.hasNext() && t == null) {

      //Setup PDA
      this.currentTree = null;
      this.stack = new ArrayList<>();

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
        if (t != null) {
          t.indexLeaves(true);
        }
      }
    }

    return t;
  }

  private static final Pattern STAR_PATTERN = Pattern.compile("\\\\\\*");
  private static final Pattern SLASH_PATTERN = Pattern.compile("\\\\/");
  private static final Pattern LRB_PATTERN = Pattern.compile("-LRB-|=LRB=");
  private static final Pattern RRB_PATTERN = Pattern.compile("-RRB-|=RRB=");


  private Tree getTreeFromInputStream() throws NoSuchElementException {
    int wordIndex = 1;

    // FSA
    label:
    while (tokenizer.hasNext()) {
      String token = tokenizer.next();

      switch (token) {
        case leftParen:

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
            // do not convert -LRB- or -RRB- tags internal to the tree
          }

          Tree newTree = treeFactory.newTreeNode(label, null); // dtrs are added below

          if (currentTree == null)
            stack.add(newTree);
          else {
            currentTree.addChild(newTree);
            stack.add(currentTree);
          }

          currentTree = newTree;

          break;
        case rightParen:
          if (stack.isEmpty()) {
            // Warn that file has too many right parentheses
            log.info("PennTreeReader: warning: file has extra non-matching right parenthesis [ignored]");
            break label;
          }

          //Accept
          currentTree = stack.remove(stack.size() - 1);  // i.e., stack.pop()

          if (stack.isEmpty()) return currentTree;

          break;
        default:

          if (currentTree == null) {
            // A careful Reader should warn here, but it's kind of useful to
            // suppress this because then the TreeReader doesn't print a ton of
            // messages if there is a README file in a directory of Trees.
            // log.info("PennTreeReader: warning: file has extra token not in a s-expression tree: " + token + " [ignored]");
            break label;
          }

          String terminal = (treeNormalizer == null) ? token : treeNormalizer.normalizeTerminal(token);
          terminal = STAR_PATTERN.matcher(terminal).replaceAll("*");
          terminal = SLASH_PATTERN.matcher(terminal).replaceAll("/");
          terminal = LRB_PATTERN.matcher(terminal).replaceAll("(");
          terminal = RRB_PATTERN.matcher(terminal).replaceAll(")");
          Tree leaf = treeFactory.newLeaf(terminal);
          if (leaf.label() instanceof HasIndex) {
            HasIndex hi = (HasIndex) leaf.label();
            hi.setIndex(wordIndex);
          }
          if (leaf.label() instanceof HasWord) {
            HasWord hw = (HasWord) leaf.label();
            hw.setWord(leaf.label().value());
          }
          if (leaf.label() instanceof HasTag) {
            HasTag ht = (HasTag) leaf.label();
            ht.setTag(currentTree.label().value());
          }
          wordIndex++;

          currentTree.addChild(leaf);
          // cdm: Note: this implementation just isn't as efficient as the old recursive descent parser (see 2008 code), where all the daughters are gathered before the tree is made....
          break;
      }
    }

    //Reject
    if (currentTree != null) {
      log.info("PennTreeReader: warning: incomplete tree (extra left parentheses in input): " + currentTree);
    }
    return null;
  }


  /**
   * Closes the underlying {@code Reader} used to create this
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
      throw new RuntimeIOException(ioe);
    }
  }

}
