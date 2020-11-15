package edu.stanford.nlp.trees;

import edu.stanford.nlp.io.ExtensionFileFilter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Sets;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;


/**
 * A {@code Treebank} object provides access to a corpus of examples with
 * given tree structures.
 * This class now implements the Collection interface. However, it may offer
 * less than the full power of the Collection interface: some Treebanks are
 * read only, and so may throw the UnsupportedOperationException.
 *
 * @author Christopher Manning
 * @author Roger Levy (added encoding variable and method)
 */
public abstract class Treebank extends AbstractCollection<Tree> {

  /**
   * Stores the {@code TreeReaderFactory} that will be used to
   * create a {@code TreeReader} to process a file of trees.
   */
  private TreeReaderFactory trf;

  /**
   * Stores the charset encoding of the Treebank on disk.
   */
  private String encoding = TreebankLanguagePack.DEFAULT_ENCODING;

  public static final String DEFAULT_TREE_FILE_SUFFIX = "mrg";

  /**
   * Create a new Treebank (using a LabeledScoredTreeReaderFactory).
   */
  public Treebank() {
    this(new LabeledScoredTreeReaderFactory());
  }


  /**
   * Create a new Treebank.
   *
   * @param trf the factory class to be called to create a new
   *            {@code TreeReader}
   */
  public Treebank(TreeReaderFactory trf) {
    this.trf = trf;
  }


  /**
   * Create a new Treebank.
   *
   * @param trf      the factory class to be called to create a new
   *                 {@code TreeReader}
   * @param encoding The charset encoding to use for treebank file decoding
   */
  public Treebank(TreeReaderFactory trf, String encoding) {
    this.trf = trf;
    this.encoding = encoding;
  }


  /**
   * Create a new Treebank.
   *
   * @param initialCapacity The initial size of the underlying Collection,
   *                        (if a Collection-based storage mechanism is being provided)
   */
  public Treebank(int initialCapacity) {
    this(initialCapacity, new LabeledScoredTreeReaderFactory());
  }


  /**
   * Create a new Treebank.
   *
   * @param initialCapacity The initial size of the underlying Collection,
   *                        (if a Collection-based storage mechanism is being provided)
   * @param trf             the factory class to be called to create a new
   *                        {@code TreeReader}
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public Treebank(int initialCapacity, TreeReaderFactory trf) {
    this.trf = trf;
  }


  /**
   * Get the {@code TreeReaderFactory} for a {@code Treebank} --
   * this method is provided in order to make the
   * {@code TreeReaderFactory} available to subclasses.
   *
   * @return The TreeReaderFactory
   */
  public TreeReaderFactory treeReaderFactory() {
    return trf;
  }


  /**
   * Returns the encoding in use for treebank file bytestream access.
   *
   * @return The encoding in use for treebank file bytestream access.
   */
  public String encoding() {
    return encoding;
  }


  /**
   * Empty a {@code Treebank}.
   */
  @Override
  public abstract void clear();


  /**
   * Load a sequence of trees from given directory and its subdirectories.
   * Trees should reside in files with the suffix "mrg".
   * Or: load a single file with the given pathName (including extension)
   *
   * @param pathName file or directory name
   */
  public void loadPath(String pathName) {
    loadPath(new File(pathName));
  }


  /**
   * Load a sequence of trees from given file or directory and its subdirectories.
   * Either this loads from a directory (tree) and
   * trees must reside in files with the suffix "mrg" (this is an English
   * Penn Treebank holdover!),
   * or it loads a single file with the given path (including extension)
   *
   * @param path File specification
   */
  public void loadPath(File path) {
    loadPath(path, DEFAULT_TREE_FILE_SUFFIX, true);
  }


  /**
   * Load trees from given directory.
   *
   * @param pathName    File or directory name
   * @param suffix      Extension of files to load: If {@code pathName}
   *                    is a directory, then, if this is
   *                    non-{@code null}, all and only files ending in "." followed
   *                    by this extension will be loaded; if it is {@code null},
   *                    all files in directories will be loaded.  If {@code pathName}
   *                    is not a directory, this parameter is ignored.
   * @param recursively descend into subdirectories as well
   */
  public void loadPath(String pathName, String suffix, boolean recursively) {
    loadPath(new File(pathName), new ExtensionFileFilter(suffix, recursively));
  }


  /**
   * Load trees from given directory.
   *
   * @param path        file or directory to load from
   * @param suffix      suffix of files to load
   * @param recursively descend into subdirectories as well
   */
  public void loadPath(File path, String suffix, boolean recursively) {
    loadPath(path, new ExtensionFileFilter(suffix, recursively));
  }


  /**
   * Load a sequence of trees from given directory and its subdirectories
   * which match the file filter.
   * Or: load a single file with the given pathName (including extension)
   *
   * @param pathName file or directory name
   * @param filt     A filter used to determine which files match
   */
  public void loadPath(String pathName, FileFilter filt) {
    loadPath(new File(pathName), filt);
  }


  /**
   * Load trees from given path specification.
   *
   * @param path file or directory to load from
   * @param filt a FilenameFilter of files to load
   */
  public abstract void loadPath(File path, FileFilter filt);

  /**
   * Apply a TreeVisitor to each tree in the Treebank.
   * For all current implementations of Treebank, this is the fastest
   * way to traverse all the trees in the Treebank.
   *
   * @param tp The TreeVisitor to be applied
   */
  public abstract void apply(TreeVisitor tp);


  /**
   * Return a Treebank (actually a TransformingTreebank) where each
   * Tree in the current treebank has been transformed using the
   * TreeTransformer.  The argument Treebank is unchanged (assuming
   * that the TreeTransformer correctly doesn't change input Trees).
   *
   * @param treeTrans The TreeTransformer to use
   * @return A Treebank (actually a TransformingTreebank) where each
   * Tree in the current treebank has been transformed using the
   * TreeTransformer.
   */
  public Treebank transform(TreeTransformer treeTrans) {
    return new TransformingTreebank(this, treeTrans);
  }


  /**
   * Return the whole treebank as a series of big bracketed lists.
   * Calling this is a really bad idea if your treebank is large.
   */
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    apply(t -> {
      sb.append(t);
      sb.append('\n');
    });
    return sb.toString();
  }


  private static final class CounterTreeProcessor implements TreeVisitor {
    int i; // = 0;

    @Override
    public void visitTree(Tree t) {
      i++;
    }

    public int total() {
      return i;
    }
  }


  /**
   * Returns the size of the Treebank.
   *
   * @return size How many trees are in the treebank
   */
  @Override
  public int size() {
    CounterTreeProcessor counter = new CounterTreeProcessor();
    apply(counter);
    return counter.total();
  }


  /** Divide a Treebank into 3, by taking every 9th sentence for the dev
   *  set and every 10th for the test set.  Penn people do this.
   */
  public void decimate(Writer trainW, Writer devW, Writer testW) {
    PrintWriter trainPW = new PrintWriter(trainW, true);
    PrintWriter devPW = new PrintWriter(devW, true);
    PrintWriter testPW = new PrintWriter(testW, true);
    int i = 0;
    for (Tree t : this) {
      if (i == 8) {
        t.pennPrint(devPW);
      } else if (i == 9) {
        t.pennPrint(testPW);
      } else {
        t.pennPrint(trainPW);
      }
      i = (i+1) % 10;
    }
  }

  /**
   * Return various statistics about the treebank (number of sentences,
   * words, tag set, etc.).
   *
   * @return A String with various statistics about the treebank (number of
   * sentences, words, tag set, etc.).
   */
  public String textualSummary() {
    return textualSummary(null);
  }

  /**
   * Return various statistics about the treebank (number of sentences,
   * words, tag set, etc.).
   *
   * @param tlp The TreebankLanguagePack used to determine punctuation and an
   *            appropriate character encoding
   * @return A big string for human consumption describing the treebank
   */
  public String textualSummary(TreebankLanguagePack tlp) {
    int numTrees = 0;
    int numTreesLE40 = 0;
    int numNonUnaryRoots = 0;
    Tree nonUnaryEg = null;
    ClassicCounter<Tree> nonUnaries = new ClassicCounter<>();
    ClassicCounter<String> roots = new ClassicCounter<>();
    Map<String,Tree> rootEgs = new HashMap<>();
    ClassicCounter<String> starts = new ClassicCounter<>();
    ClassicCounter<String> puncts = new ClassicCounter<>();
    int numUnenclosedLeaves = 0;
    int numLeaves = 0;
    int numNonPhrasal = 0;
    int numPreTerminalWithMultipleChildren = 0;
    int numWords = 0;
    int numTags = 0;
    int shortestSentence = Integer.MAX_VALUE;
    int longestSentence = 0;
    int numNullLabel = 0;
    Set<String> words = Generics.newHashSet();
    ClassicCounter<String> tags = new ClassicCounter<>();
    ClassicCounter<String> cats = new ClassicCounter<>();
    Tree leafEg = null;
    Tree preTerminalMultipleChildrenEg = null;
    Tree nullLabelEg = null;
    Tree rootRewritesAsTaggedWordEg = null;
    for (Tree t : this) {
      roots.incrementCount(t.value());
      rootEgs.put(t.value(), t);
      numTrees++;
      int leng = t.yield().size();
      if (leng <= 40) {
        numTreesLE40++;
      }
      if (leng < shortestSentence) {
        shortestSentence = leng;
      }
      if (leng > longestSentence) {
        longestSentence = leng;
      }
      if (t.numChildren() > 1) {
        if (numNonUnaryRoots == 0) {
          nonUnaryEg = t;
        }
        if (numNonUnaryRoots < 100) {
          nonUnaries.incrementCount(t.localTree());
        }
        numNonUnaryRoots++;
      } else if (t.isLeaf()) {
        numUnenclosedLeaves++;
      } else {
        Tree t2 = t.firstChild();
        if (t2.isLeaf()) {
          numLeaves++;
          leafEg = t;
        } else if (t2.isPreTerminal()) {
          if (numNonPhrasal == 0) {
            rootRewritesAsTaggedWordEg = t;
          }
          numNonPhrasal++;
        }
        starts.incrementCount(t2.value());
      }
      for (Tree subtree : t) {
        Label lab = subtree.label();
        if (lab == null || lab.value() == null || lab.value().isEmpty()) {
          if (numNullLabel == 0) {
            nullLabelEg = subtree;
          }
          numNullLabel++;
          if (lab == null) {
            subtree.setLabel(new StringLabel(""));
          } else if (lab.value() == null) {
            subtree.label().setValue("");
          }
        }
        if (subtree.isLeaf()) {
          numWords++;
          words.add(subtree.value());
        } else if (subtree.isPreTerminal()) {
          numTags++;
          tags.incrementCount(subtree.value());
          if (tlp != null && tlp.isPunctuationTag(subtree.value())) {
            puncts.incrementCount(subtree.firstChild().value());
          }
        } else if (subtree.isPhrasal()) {
          boolean hasLeafChild = false;
          for (Tree kt : subtree.children()) {
            if (kt.isLeaf()) {
              hasLeafChild = true;
            }
          }
          if (hasLeafChild) {
            numPreTerminalWithMultipleChildren++;
            if (preTerminalMultipleChildrenEg == null) {
              preTerminalMultipleChildrenEg = subtree;
            }
          }
          cats.incrementCount(subtree.value());
        } else {
          throw new IllegalStateException("Treebank: Bad tree in treebank!: " + subtree);
        }
      }
    }
    StringWriter sw = new StringWriter(2000);
    PrintWriter pw = new PrintWriter(sw);
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(0);
    pw.println("Treebank has " + numTrees + " trees (" + numTreesLE40 + " of length <= 40) and " + numWords + " words (tokens)");
    if (numTrees > 0) {
      if (numTags != numWords) {
        pw.println("  Warning! numTags differs and is " + numTags);
      }
      if (roots.size() == 1) {
        String root = (String) roots.keySet().toArray()[0];
        pw.println("  The root category is: " + root);
      } else {
        pw.println("  Warning! " + roots.size() + " different roots in treebank: " + Counters.toString(roots, nf));
        pw.println("  Examples:");
        for (Tree t : rootEgs.values()) {
          pw.println("  " + t);
        }
      }
      if (numNonUnaryRoots > 0) {
        pw.print("  Warning! " + numNonUnaryRoots + " trees without unary initial rewrite.  ");

        if (numNonUnaryRoots > 100) {
          pw.print("First 100 ");
        }
        pw.println("Rewrites: " + Counters.toString(nonUnaries, nf));
        pw.println("    Example: " + nonUnaryEg);
      }
      if (numUnenclosedLeaves > 0 || numLeaves > 0 || numNonPhrasal > 0) {
        pw.println("  Warning! Non-phrasal trees: " + numUnenclosedLeaves + " bare leaves; " + numLeaves + " root rewrites as leaf; and " + numNonPhrasal + " root rewrites as tagged word");
        if (numLeaves > 0) {
          pw.println("  Example bad root rewrites as leaf: " + leafEg);
        }
        if (numNonPhrasal > 0) {
          pw.println("  Example bad root rewrites as tagged word: " + rootRewritesAsTaggedWordEg);
        }
      }
      if (numNullLabel > 0) {
        pw.println("  Warning! " + numNullLabel + " tree nodes with null or empty string labels, e.g.:");
        pw.println("    " + nullLabelEg);
      }
      if (numPreTerminalWithMultipleChildren > 0) {
        pw.println("  Warning! " + numPreTerminalWithMultipleChildren + " preterminal nodes with multiple children.");
        pw.println("    Example: " + preTerminalMultipleChildrenEg);
      }
      pw.println("  Sentences range from " + shortestSentence + " to " + longestSentence + " words, with an average length of " + (((numWords * 100) / numTrees) / 100.0) + " words.");
      pw.println("  " + cats.size() + " phrasal category types, " + tags.size() + " tag types, and " + words.size() + " word types");
      String[] empties = {"*", "0", "*T*", "*RNR*", "*U*",
              "*?*", "*EXP*", "*ICH*", "*NOT*", "*PPA*",
              "*OP*", "*pro*", "*PRO*"};
      // What a dopey choice using 0 as an empty element name!!
      // The problem with the below is that words aren't turned into a basic
      // category, but empties commonly are indexed....  Would need to look
      // for them with a suffix of -[0-9]+
      Set<String> knownEmpties = Generics.newHashSet(Arrays.asList(empties));
      Set<String> emptiesIntersection = Sets.intersection(words, knownEmpties);
      if ( ! emptiesIntersection.isEmpty()) {
        pw.println("  Caution! " + emptiesIntersection.size() +
                " word types are known empty elements: " +
                emptiesIntersection);
      }
      Set<String> joint = Sets.intersection(cats.keySet(), tags.keySet());
      if ( ! joint.isEmpty()) {
        pw.println("  Warning! " + joint.size() + " items are tags and categories: " + joint);
      }
      for (String cat : cats.keySet()) {
        if (cat != null && cat.contains("@")) {
          pw.println("  Warning!! Stanford Parser does not work with categories containing '@' like: " + cat);
          break;
        }
      }
      for (String cat : tags.keySet()) {
        if (cat != null && cat.contains("@")) {
          pw.println("  Warning!! Stanford Parser does not work with tags containing '@' like: " + cat);
          break;
        }
      }
      pw.println("    Cats: " + Counters.toString(cats, nf));
      pw.println("    Tags: " + Counters.toString(tags, nf));
      pw.println("    " + starts.size() + " start categories: " + Counters.toString(starts, nf));
      if ( ! puncts.isEmpty()) {
        pw.println("    Puncts: " + Counters.toString(puncts, nf));
      }
    }
    return sw.toString();
  }


  /**
   * This operation isn't supported for a Treebank.  Tell them immediately.
   */
  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("Treebank is read-only");
  }

}
