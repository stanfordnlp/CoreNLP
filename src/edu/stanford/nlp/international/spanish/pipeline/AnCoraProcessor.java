package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeNormalizer;
import edu.stanford.nlp.trees.international.spanish.SpanishTreebankLanguagePack;
import edu.stanford.nlp.trees.international.spanish.SpanishXMLTreeReader;
import edu.stanford.nlp.trees.international.spanish.SpanishXMLTreeReaderFactory;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

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
 * For invocation options, see {@link #main(String[])}.
 *
 * @author Jon Gauthier
 */
public class AnCoraProcessor {

  private List<File> inputFiles;
  private Properties options;

  private final TwoDimensionalCounter<String, String> unigramTagger =
    new TwoDimensionalCounter<String, String>();
  private List<Tree> trees;

  public AnCoraProcessor(List<File> inputFiles, Properties options) {
    this.inputFiles = inputFiles;
    this.options = options;
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
        Tree t;

        while ((t = tr.readTree()) != null) {
          trees.add(t);
          updateTagger(t);
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
      "    -ner: Add NER-specific information to trees\n";

  private static final Map<String, Integer> argOptionDefs = new HashMap<String, Integer>() {{
    put("ner", 0);
  }};

  /**
   * TODO document invocation options
   */
  public static void main(String[] args)
    throws InterruptedException, IOException, ExecutionException {
    if (args.length < 1) {
      System.err.println(usage);
    }

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
