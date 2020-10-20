package edu.stanford.nlp.international.spanish.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.spanish.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.*;

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

  private final boolean expandElisions;
  private final boolean expandConmigo;

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
    expandElisions = PropertiesUtils.getBool(options, "expandElisions", false);
    expandConmigo = PropertiesUtils.getBool(options, "expandConmigo", false);
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

    final SpanishXMLTreeReaderFactory trf = new SpanishXMLTreeReaderFactory(true, true, ner, false, expandElisions, expandConmigo);

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

    private final SpanishTreeNormalizer tn;
    private final Factory<TreeNormalizer> tnf;
    private final TreeFactory tf;

    private final boolean ner;

    // NB: TreeNormalizer is not thread-safe, and so we need to accept + store a
    // TreeNormalizer factory instead
    public MultiWordProcessor(Factory<TreeNormalizer> tnf, TreeFactory tf,
                              boolean ner) {
      this.tnf = tnf;
      this.tn = (SpanishTreeNormalizer) tnf.create();
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

        t = tn.normalizeWholeTree(t, tf, expandElisions, expandConmigo);

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

  public static HashSet<String> auxTagConversion = new HashSet<>(Arrays.asList("vsip000,es", "vaip000,ha",
      "vaip000,han", "vsis000,fue", "vsn0000,ser","vsip000,son", "vmip000,está", "vaii000,había",
      "vsp0000,sido", "vmip000,puede", "vaip000,hay", "vsii000,era", "vsif000,será", "van0000,haber",
      "vmip000,están", "vsip000,Es", "vsis000,fueron", "vssp000,sea", "vmip000,debe","vmic000,podría",
      "vsic000,sería", "vmii000,estaba", "vasp000,haya", "vaii000,habían", "vaip000,hemos",
      "vaip000,he", "vsii000,eran", "vsg0000,siendo", "vmn0000,poder", "vmip000,deben"));

  public static HashSet<String> potentialAUXWords =
      new HashSet<>(Arrays.asList("es", "ha", "han", "fue", "ser", "son", "está", "había", "sido", "puede", "hay",
          "era", "será", "haber", "están", "Es", "fueron", "sea", "debe", "pueden", "podría",
          "sería", "estaba", "haya", "habían", "hemos", "he", "eran", "siendo", "poder", "deben"));

  public static void convertTreeTagsToUD(Tree tree) {
    for (Tree t : tree.getChildrenAsList()) {
      if (t.isPreTerminal()) {
        if (t.label().value().startsWith("a")) {
          t.setLabel(CoreLabel.wordFromString("ADJ"));
        } else if (t.label().value().startsWith("d")) {
          t.setLabel(CoreLabel.wordFromString("DET"));
        } else if (t.label().value().startsWith("f")) {
          if (t.getChild(0).label().value().matches("[^0-9]+"))
            t.setLabel(CoreLabel.wordFromString("PUNCT"));
          else if (t.getChild(0).label().value().matches("[0-9]+"))
            t.setLabel(CoreLabel.wordFromString("NUM"));
          else
            System.err.println(t.label().value() + "\t" + t.getChild(0).label().value());
        } else if (t.label().value().equals("i")) {
          t.setLabel(CoreLabel.wordFromString("INTJ"));
        } else if (t.label().value().startsWith("n")) {
          if (t.label().value().equals("np00000") &&
              t.getChild(0).label().value().substring(0,1).matches("^[A-Z]"))
            t.setLabel(CoreLabel.wordFromString("PROPN"));
          else
            t.setLabel(CoreLabel.wordFromString("NOUN"));
        } else if (t.label().value().startsWith("p")) {
          t.setLabel(CoreLabel.wordFromString("PRON"));
        } else if (t.label().value().startsWith("r")) {
          t.setLabel(CoreLabel.wordFromString("ADV"));
        } else if (t.label().value().startsWith("s")){
          t.setLabel(CoreLabel.wordFromString("ADP"));
        } else if (t.label().value().startsWith("v")) {
          String ancoraTag = t.label().value();
          String word = t.getChild(0).label().value();
          if (potentialAUXWords.contains(word) && auxTagConversion.contains(String.format("%s,%s", ancoraTag, word))) {
            t.setLabel(CoreLabel.wordFromString("AUX"));
          } else
            t.setLabel(CoreLabel.wordFromString("VERB"));
        } else if (t.label().value().startsWith("z")) {
          if (t.getChild(0).label().value().matches("[A-Z][A-Z0-9]+"))
            t.setLabel(CoreLabel.wordFromString("PROPN"));
          else if (t.getChild(0).label().value().matches("[A-Z0-9]+[A-Z]"))
            t.setLabel(CoreLabel.wordFromString("PROPN"));
          else if (t.getChild(0).label().value().matches("[^0-9]+"))
            t.setLabel(CoreLabel.wordFromString("NOUN"));
          else if (t.getChild(0).label().value().matches("[0-9\\.\\,º:]+"))
            t.setLabel(CoreLabel.wordFromString("NUM"));
          else if (t.getChild(0).label().value().matches("m\\.[0-9]+(\\:)?"))
            t.setLabel(CoreLabel.wordFromString("NUM"));
          else if (t.getChild(0).label().value().matches("[0-9]+cc"))
            t.setLabel(CoreLabel.wordFromString("NUM"));
          else
            System.err.println(t.label().value() + "\t" + t.getChild(0).label().value());
        } else if (t.label().value().equals("cc")) {
          t.setLabel(CoreLabel.wordFromString("CCONJ"));
        } else if (t.label().value().equals("cs")) {
          t.setLabel(CoreLabel.wordFromString("SCONJ"));
        } else if (t.label().value().equals("w")) {
          if (t.getChild(0).label().value().matches("[^0-9]+"))
            t.setLabel(CoreLabel.wordFromString("NOUN"));
          else if (t.getChild(0).label().value().matches("[0-9]{4}|[0-9]+\\'"))
            t.setLabel(CoreLabel.wordFromString("NOUN"));
          else if (t.getChild(0).label().value().matches("[0-9\\.\\,]+"))
            t.setLabel(CoreLabel.wordFromString("NUM"));
          else if (t.getChild(0).label().value().matches("m\\.[0-9]+"))
            t.setLabel(CoreLabel.wordFromString("NOUN"));
          else
            System.err.println(t.label().value() + "\t" + t.getChild(0).label().value());
        } else {
          System.err.println(t.label().value() + "\t" + t.getChild(0).label().value());
        }
      } else {
        convertTreeTagsToUD(t);
      }
    }
  }

  private static final String usage =
    String.format(
        "Usage: java %s [OPTIONS] file(s)%n%n", AnCoraProcessor.class.getName()) +
        "Options:\n" +
        "    -unigramTagger <tagger_path>: Path to a serialized `TwoDimensionalCounter` which\n" +
        "        should be used for unigram tagging in multi-word token expansion. If this option\n" +
        "        is not provided, a unigram tagger will be built from the provided corpus data.\n" +
        "        (This option is useful if you are processing splits of the corpus separately but\n" +
        "        want each step to benefit from a complete tagger.)\n" +
        "    -ner: Add NER-specific information to trees\n" +
        "    -generateTags: build tags with this model\n"+
        "    -expandElisions: MWT expand words like del, al\n"+
        "    -expandConmigo: MWT expand words like conmigo, contigo\n"+
        "    -convertToUD: Convert part-of-speech tags to UD\n";

  private static final Map<String, Integer> argOptionDefs = new HashMap<>();
  static {
    argOptionDefs.put("unigramTagger", 1);
    argOptionDefs.put("ner", 0);
    argOptionDefs.put("convertToUD", 0);
    argOptionDefs.put("generateTags", 0);
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

    // potentially convert tags to UD with rules
    boolean convertToUD = PropertiesUtils.getBool(options, "convertToUD");
    if (convertToUD) {
      for (Tree t : trees)
        convertTreeTagsToUD(t);
    }

    // potentially generate tags
    boolean generateTags = PropertiesUtils.getBool(options, "generateTags");
    String partOfSpeechModel = options.getProperty("generateTagsModel",
        "edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger");
    if (generateTags && partOfSpeechModel != "") {
      TreebankTagUpdater spanishTagger = new TreebankTagUpdater(partOfSpeechModel);
      for (Tree t : trees)
        spanishTagger.tagTree(t);
    }

    // print out final trees
    for (Tree t : trees)
      System.out.println(t);
  }

}
