package edu.stanford.nlp.international.spanish.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.spanish.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/**
 * A tool which accepts raw AnCora-3.0 Spanish XML files and produces
 * normalized / pre-processed PTB-style treebanks for use with CoreNLP
 * tools.
 *
 * This is a substitute for an awkward and complicated string of
 * command-line invocations. The produced corpus is the standard
 * treebank which has been used to train the CoreNLP Spanish models.
 *
 * The preprocessing steps performed here include:
 *
 * - Expansion and automatic tagging of multi-word tokens (see
 *   {@link MultiWordPreprocessor},
 *   {@link SpanishTreeNormalizer#normalizeForMultiWord(Tree, TreeFactory)}
 * - Heuristic parsing of expanded multi-word tokens (see
 *   {@link MultiWordTreeExpander}
 * - Splitting of elided forms (<em>al</em>, <em>del</em>,
 *   <em>conmigo</em>, etc.) and clitic pronouns from verb forms (see
 *   {@link SpanishTreeNormalizer#expandElisions(Tree)},
 *   {@link SpanishTreeNormalizer#expandCliticPronouns(Tree)}
 * - Miscellaneous cleanup of parse trees, spelling fixes, parsing
 *   error corrections (see {@link SpanishTreeNormalizer})
 *
 * Apart from raw corpus data, this processor depends upon unigram
 * part-of-speech tag data. If not provided explicitly to the
 * processor, the data will be collected from the given files. (You can
 * pre-compute POS data from AnCora XML using {@link AnCoraPOSStats}.)
 *
 * For invocation options, execute the class with no arguments.
 *
 * @author Jon Gauthier
 */
public class AnCoraProcessor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AnCoraProcessor.class);

  private final List<File> inputFiles;
  private final Properties options;

  private final TwoDimensionalCounter<String, String> unigramTagger;

  @SuppressWarnings("unchecked")
  public AnCoraProcessor(List<File> inputFiles, Properties options)
    throws IOException, ClassNotFoundException {

    this.inputFiles = inputFiles;
    this.options = options;

    if (options.containsKey("unigramTagger")) {
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(options.getProperty
        ("unigramTagger")));
      unigramTagger = (TwoDimensionalCounter<String, String>) ois.readObject();
    } else {
      unigramTagger = new TwoDimensionalCounter<>();
    }
  }

  public List<Tree> process() throws
    InterruptedException, IOException, ExecutionException {

    // Each of the following subroutines are multithreaded; there is a bottleneck between the
    // method calls
    List<Tree> trees = loadTrees();
    trees = fixMultiWordTokens(trees);

    return trees;
  }

  /**
   * Use {@link SpanishXMLTreeReader} to load the trees from the provided files,
   * and begin collecting some statistics to be used in later MWE cleanup.
   *
   * NB: Much of the important cleanup happens implicitly here; the XML tree reader triggers the
   * tree normalization routine.
   */
  private List<Tree> loadTrees() throws
    InterruptedException, IOException, ExecutionException {
    boolean ner = PropertiesUtils.getBool(options, "ner", false);
    final String encoding = new SpanishTreebankLanguagePack().getEncoding();

    final SpanishXMLTreeReaderFactory trf = new SpanishXMLTreeReaderFactory(true, true, ner, false);

    List<Tree> trees = new ArrayList<>();
    for (File file : inputFiles) {
      Pair<TwoDimensionalCounter<String, String>, List<Tree>> ret = processTreeFile(file, trf,
                                                                                    encoding);

      Counters.addInPlace(unigramTagger, ret.first());
      trees.addAll(ret.second());
    }

    return trees;
  }

  /**
   * Processes a single file containing AnCora XML trees. Returns MWE statistics for the trees in
   * the file and the actual parsed trees.
   */
  private static Pair<TwoDimensionalCounter<String, String>, List<Tree>> processTreeFile(
    File file, SpanishXMLTreeReaderFactory trf, String encoding) {

    TwoDimensionalCounter<String, String> tagger = new TwoDimensionalCounter<>();

    try {
      Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                                                           encoding));
      TreeReader tr = trf.newTreeReader(file.getPath(), in);

      List<Tree> trees = new ArrayList<>();
      Tree t, splitPoint;

      while ((t = tr.readTree()) != null) {
        // We may need to split the current tree into multiple parts.
        // (If not, a call to `split` with a `null` split-point is a
        // no-op
        do {
          splitPoint = findSplitPoint(t);
          Pair<Tree, Tree> split = split(t, splitPoint);

          Tree toAdd = split.first();
          t = split.second();

          trees.add(toAdd);
          updateTagger(tagger, toAdd);
        } while (splitPoint != null);
      }

      tr.close();

      return new Pair<>(tagger, trees);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private static void updateTagger(TwoDimensionalCounter<String, String> tagger, Tree t) {
    List<CoreLabel> yield = t.taggedLabeledYield();
    for (CoreLabel label : yield) {
      if (label.tag().equals(SpanishTreeNormalizer.MW_TAG))
        continue;

      tagger.incrementCount(label.word(), label.tag());
    }
  }

  private static TreeNormalizer splittingNormalizer = new SpanishSplitTreeNormalizer();
  private static TreeFactory splittingTreeFactory = new LabeledScoredTreeFactory();

  /**
   * Split the given tree based on a split point such that the
   * terminals leading up to the split point are in the left returned
   * tree and those following the split point are in the left returned
   * tree.
   *
   * AnCora contains a nontrivial amount of trees with multiple
   * sentences in them. This method is used to break apart these
   * sentences into separate trees.
   *
   * @param t Tree from which to extract a subtree. This may be
   *          modified during processing.
   * @param splitPoint Point up to which to extract. If {@code null},
   *                   {@code t} is returned unchanged in the place of
   *                   the right tree.
   * @return A pair where the left tree contains every terminal leading
   *         up to and including {@code splitPoint} and the right tree
   *         contains every terminal following {@code splitPoint}.
   *         Both trees may be normalized before return.
   */
  static Pair<Tree, Tree> split(Tree t, Tree splitPoint) {
    if (splitPoint == null)
      return new Pair<>(t, null);

    Tree left = t.prune(new LeftOfFilter(splitPoint, t));
    Tree right = t.prune(new RightOfExclusiveFilter(splitPoint, t));

    left = splittingNormalizer.normalizeWholeTree(left, splittingTreeFactory);
    right = splittingNormalizer.normalizeWholeTree(right, splittingTreeFactory);

    return new Pair<>(left, right);
  }

  /**
   * Accepts any tree node to the left of the provided node (or the
   * provided node itself).
   */
  private static class LeftOfFilter implements Predicate<Tree>, Serializable {

    private static final long serialVersionUID = -5146948439247427344L;

    private Tree reference;
    private Tree root;

    /**
     * @param reference Node to which nodes provided to this filter
     *                  should be compared
     * @param root Root of the tree which contains the reference node
     *             and all nodes which may be provided to the filter
     */
    private LeftOfFilter(Tree reference, Tree root) {
      this.reference = reference;
      this.root = root;
    }

    @Override
    public boolean test(Tree obj) {
      if (obj == reference || obj.dominates(reference) || reference.dominates(obj))
        return true;

      Tree rightmostDescendant = getRightmostDescendant(obj);
      return Trees.rightEdge(rightmostDescendant, root) <= Trees.leftEdge(reference, root);
    }

    private Tree getRightmostDescendant(Tree t) {
      if (t.isLeaf()) return t;
      else return getRightmostDescendant(t.children()[t.children().length - 1]);
    }
  }

  /**
   * Accepts any tree node to the right of the provided node.
   */
  private static class RightOfExclusiveFilter implements Predicate<Tree>, Serializable {

    private static final long serialVersionUID = 8283161954004080591L;

    private Tree root;

    // This should be the leftmost terminal node of the filtered tree
    private Tree firstToKeep;

    /**
     * @param reference Node to which nodes provided to this filter
     *                  should be compared
     * @param root Root of the tree which contains the reference node
     *             and all nodes which may be provided to the filter
     */
    private RightOfExclusiveFilter(Tree reference, Tree root) {
      this.root = root;

      firstToKeep = getFollowingTerminal(reference, root);
    }

    @Override
    public boolean test(Tree obj) {
      if (obj.dominates(firstToKeep))
        return true;

      Tree leftmostDescendant = getLeftmostDescendant(obj);
      return Trees.rightEdge(leftmostDescendant, root) > Trees.leftEdge(firstToKeep, root);
    }

    /**
     * Get the terminal node which immediately follows the given node.
     */
    private Tree getFollowingTerminal(Tree terminal, Tree root) {
      Tree sibling = getRightSiblingOrRightAncestor(terminal, root);
      if (sibling == null)
        return null;
      return getLeftmostDescendant(sibling);
    }

    /**
     * Get the right sibling of the given node, or some node which is
     * the right sibling of an ancestor of the given node.
     *
     * If no such node can be found, this method returns {@code null}.
     */
    private Tree getRightSiblingOrRightAncestor(Tree t, Tree root) {
      Tree parent = t.parent(root);
      if (parent == null) return null;

      int idxWithinParent = parent.objectIndexOf(t);
      if (idxWithinParent < parent.numChildren() - 1)
        // Easy case: just return the immediate right sibling
        return parent.getChild(idxWithinParent + 1);

      return getRightSiblingOrRightAncestor(parent, root);
    }

    private Tree getLeftmostDescendant(Tree t) {
      if (t.isLeaf()) return t;
      else return getLeftmostDescendant(t.children()[0]);
    }
  }

  /**
   * Matches a point in the AnCora corpus which is the delimiter
   * between two sentences.
   *
   * @see {@link #split(Tree, Tree)}
   */
  private static final TregexPattern pSplitPoint =
    TregexPattern.compile("fp $+ /^[^f]/ > S|sentence");

  /**
   * Find the next point (preterminal) at which the given tree should
   * be split.
   *
   * @param t
   * @return The endpoint of a subtree which should be extracted, or
   *         {@code null} if there are no subtrees which need to be
   *         extracted.
   */
  static Tree findSplitPoint(Tree t) {
    TregexMatcher m = pSplitPoint.matcher(t);
    if (m.find())
      return m.getMatch();
    return null;
  }

  private class MultiWordProcessor implements ThreadsafeProcessor<Collection<Tree>,
    Collection<Tree>> {

    private final TreeNormalizer tn;
    private final Factory<TreeNormalizer> tnf;
    private final TreeFactory tf;

    private final boolean ner;

    // NB: TreeNormalizer is not thread-safe, and so we need to accept + store a
    // TreeNormalizer factory instead
    public MultiWordProcessor(Factory<TreeNormalizer> tnf, TreeFactory tf,
                              boolean ner) {
      this.tnf = tnf;
      this.tn = tnf.create();
      this.tf = tf;
      this.ner = ner;
    }

    @Override
    public Collection<Tree> process(Collection<Tree> coll) {
      List<Tree> ret = new ArrayList<>();

      // Apparently TsurgeonPatterns are not thread safe
      MultiWordTreeExpander expander = new MultiWordTreeExpander();

      for (Tree t : coll) {
        // Begin with basic POS / phrasal category inference
        MultiWordPreprocessor
          .traverseAndFix(t, null, AnCoraProcessor.this.unigramTagger, ner);

        // Now "decompress" further the expanded trees formed by multiword token splitting
        t = expander.expandPhrases(t, tn, tf);

        t = tn.normalizeWholeTree(t, tf);

        ret.add(t);
      }

      return ret;
    }

    @Override
    public ThreadsafeProcessor<Collection<Tree>, Collection<Tree>> newInstance() {
      return new MultiWordProcessor(tnf, tf, ner);
    }
  }

  /**
   * Fix tree structure, phrasal categories and part-of-speech labels in newly expanded
   * multi-word tokens.
   */
  private List<Tree> fixMultiWordTokens(List<Tree> trees)
    throws InterruptedException, ExecutionException {
    boolean ner = PropertiesUtils.getBool(options, "ner", false);

    // Shared resources
    Factory<TreeNormalizer> tnf = new Factory<TreeNormalizer>() {
      @Override public TreeNormalizer create() {
        return new SpanishTreeNormalizer(true, false, false);
      }
    };
    TreeFactory tf = new LabeledScoredTreeFactory();

    ThreadsafeProcessor<Collection<Tree>, Collection<Tree>> processor =
      new MultiWordProcessor(tnf, tf, ner);

    int availableProcessors = Runtime.getRuntime().availableProcessors();
    MulticoreWrapper<Collection<Tree>, Collection<Tree>> wrapper =
            new MulticoreWrapper<>(availableProcessors, processor,
                    false);

    // Chunk our work so that parallelization is actually worth it
    int numChunks = availableProcessors * 20;
    List<List<Tree>> chunked = CollectionUtils.partitionIntoFolds(trees, numChunks);
    List<Tree> ret = new ArrayList<>();

    for (final Collection<Tree> coll : chunked) {
      wrapper.put(coll);

      while (wrapper.peek())
        ret.addAll(wrapper.poll());
    }

    wrapper.join();

    while (wrapper.peek())
      ret.addAll(wrapper.poll());

    return ret;
  }

  private static final String usage =
    String.format("Usage: java %s [OPTIONS] file(s)%n%n", AnCoraProcessor.class.getName()) +
      "Options:\n" +
      "    -unigramTagger <tagger_path>: Path to a serialized `TwoDimensionalCounter` which\n" +
      "        should be used for unigram tagging in multi-word token expansion. If this option\n" +
      "        is not provided, a unigram tagger will be built from the provided corpus data.\n" +
      "        (This option is useful if you are processing splits of the corpus separately but\n" +
      "        want each step to benefit from a complete tagger.)\n" +
      "    -ner: Add NER-specific information to trees\n";

  private static final Map<String, Integer> argOptionDefs = new HashMap<>();
  static {
    argOptionDefs.put("unigramTagger", 1);
    argOptionDefs.put("ner", 0);
  }

  public static void main(String[] args)
    throws InterruptedException, IOException, ExecutionException, ClassNotFoundException {
    if (args.length < 1)
      log.info(usage);

    Properties options = StringUtils.argsToProperties(args, argOptionDefs);
    String[] remainingArgs = options.getProperty("").split(" ");
    List<File> fileList = new ArrayList<>();
    for (String arg : remainingArgs)
      fileList.add(new File(arg));

    AnCoraProcessor processor = new AnCoraProcessor(fileList, options);
    List<Tree> trees = processor.process();

    for (Tree t : trees)
      System.out.println(t);
  }

}
