package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.spanish.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

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
public class AnCoraProcessor {

  private List<File> inputFiles;
  private Properties options;

  private TwoDimensionalCounter<String, String> unigramTagger;
  private List<Tree> trees;

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
      unigramTagger = new TwoDimensionalCounter<String, String>();
    }
  }

  public List<Tree> process() throws
    InterruptedException, IOException, ExecutionException {

    trees = new ArrayList<Tree>();

    // Each of the following subroutines are multithreaded; there is a bottleneck between the
    // method calls
    trees = loadTrees();
    trees = fixMultiWordTokens();

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
    ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    List<Future<Pair<TwoDimensionalCounter<String, String>, List<Tree>>>> readFutures = new
      ArrayList<Future<Pair<TwoDimensionalCounter<String, String>, List<Tree>>>>();

    // Set up processing futures
    for (final File file : inputFiles)
      readFutures.add(pool.submit(new XMLTreeProcessor(trf, file, encoding)));

    // OK, now merge results from each thread
    for (Future<Pair<TwoDimensionalCounter<String, String>, List<Tree>>> future : readFutures) {
      Pair<TwoDimensionalCounter<String, String>, List<Tree>> result = future.get();

      Counters.addInPlace(unigramTagger, result.first());
      trees.addAll(result.second());
    }

    pool.shutdown();
    return trees;
  }

  /**
   * Processes a single file containing AnCora XML trees. Returns MWE statistics for the trees in
   * the file and the actual parsed trees.
   */
  private class XMLTreeProcessor implements Callable<Pair<TwoDimensionalCounter<String, String>,
    List<Tree>>> {
    private SpanishXMLTreeReaderFactory trf;
    private File file;
    private String encoding;

    /**
     * Collects unigram tag counts which will be aggregated for use in tag inference later
     */
    private TwoDimensionalCounter<String, String> unigramTagger = new TwoDimensionalCounter<String,
      String>();

    private XMLTreeProcessor(SpanishXMLTreeReaderFactory trf, File file, String encoding) {
      this.trf = trf;
      this.file = file;
      this.encoding = encoding;
    }

    @Override
    public Pair<TwoDimensionalCounter<String, String>, List<Tree>> call() {
      try {
        Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                                                             encoding));
        TreeReader tr = trf.newTreeReader(file.getPath(), in);

        List<Tree> trees = new ArrayList<Tree>();
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
            updateTagger(toAdd);
          } while (splitPoint != null);
        }

        tr.close();

        return new Pair<TwoDimensionalCounter<String, String>, List<Tree>>(unigramTagger, trees);
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }

    private void updateTagger(Tree t) {
      List<CoreLabel> yield = t.taggedLabeledYield();
      for (CoreLabel label : yield) {
        if (label.tag().equals(SpanishTreeNormalizer.MW_TAG))
          continue;

        unigramTagger.incrementCount(label.word(), label.tag());
      }
    }
  }

  private static TreeNormalizer splittingNormalizer = new SpanishSplitTreeNormalizer();
  private static TreeFactory splittingTreeFactory = new LabeledScoredTreeFactory();

  /**
   * Split the given tree based on a split point such that the
   * terminals leading up to the split point are in the left returned
   * tree and those following the are in the left returned tree.
   *
   * @param t Tree from which to extract a subtree. This may be
   *          modified during processing.
   * @param splitPoint Point up to which to extract. If {@code null},
   *                   {@code t} is returned unchanged in the place of
   *                   the right tree.
   * @return A pair where the left tree contains every terminal leading
   *         up to and including {@code extractPoint} and the right tree
   *         contains every terminal following {@code extractPoint}.
   *         Both trees may be normalized before return.
   */
  static Pair<Tree, Tree> split(Tree t, Tree splitPoint) {
    if (splitPoint == null)
      return new Pair<Tree, Tree>(t, null);

    Tree left = t.prune(new LeftOfFilter(splitPoint, t));
    Tree right = t.prune(new RightOfExclusiveFilter(splitPoint, t));

    left = splittingNormalizer.normalizeWholeTree(left, splittingTreeFactory);
    right = splittingNormalizer.normalizeWholeTree(right, splittingTreeFactory);

    return new Pair<Tree, Tree>(left, right);
  }

  /**
   * Accepts any tree node to the left of the provided node (or the
   * provided node itself).
   */
  private static class LeftOfFilter implements Filter<Tree> {

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
    public boolean accept(Tree obj) {
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
  private static class RightOfExclusiveFilter implements Filter<Tree> {

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
    public boolean accept(Tree obj) {
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

  /**
   * Fix tree structure, phrasal categories and part-of-speech labels in newly expanded
   * multi-word tokens.
   */
  private List<Tree> fixMultiWordTokens() throws InterruptedException, ExecutionException {
    final boolean ner = PropertiesUtils.getBool(options, "ner", false);

    // Shared resources
    final TreeNormalizer tn = new SpanishTreeNormalizer(true, false, false);
    final TreeFactory tf = new LabeledScoredTreeFactory();

    int availableProcessors = Runtime.getRuntime().availableProcessors();
    ExecutorService pool = Executors.newFixedThreadPool(availableProcessors);

    // Chunk our work so that parallelization is actually worth it
    int numChunks = availableProcessors * 20;
    List<Collection<Tree>> chunked = CollectionUtils.partitionIntoFolds(trees, numChunks);
    List<Future<List<Tree>>> futures = new ArrayList<Future<List<Tree>>>();

    for (final Collection<Tree> coll : chunked) {
      futures.add(pool.submit(new Callable<List<Tree>>() {
        @Override
        public List<Tree> call() {
          List<Tree> ret = new ArrayList<Tree>();

          // Apparently TsurgeonPatterns are not thread safe
          MultiWordTreeExpander expander = new MultiWordTreeExpander();

          for (Tree t : coll) {
            // Begin with basic POS / phrasal category inference
            MultiWordPreprocessor
              .traverseAndFix(t, null, unigramTagger, ner);

            // Now "decompress" further the expanded trees formed by multiword token splitting
            t = expander.expandPhrases(t, tn, tf);

            t = tn.normalizeWholeTree(t, tf);

            ret.add(t);
          }

          return ret;
        }
      }));
    }

    List<Tree> ret = new ArrayList<Tree>();
    for (Future<List<Tree>> future : futures)
      ret.addAll(future.get());

    pool.shutdown();

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

  private static final Map<String, Integer> argOptionDefs = new HashMap<String, Integer>() {{
    put("unigramTagger", 1);
    put("ner", 0);
  }};

  public static void main(String[] args)
    throws InterruptedException, IOException, ExecutionException, ClassNotFoundException {
    if (args.length < 1)
      System.err.println(usage);

    Properties options = StringUtils.argsToProperties(args, argOptionDefs);
    String[] remainingArgs = options.getProperty("").split(" ");
    List<File> fileList = new ArrayList<File>();
    for (String arg : remainingArgs)
      fileList.add(new File(arg));

    AnCoraProcessor processor = new AnCoraProcessor(fileList, options);
    List<Tree> trees = processor.process();

    for (Tree t : trees)
      System.out.println(t);
  }

}
