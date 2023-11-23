package edu.stanford.nlp.international.spanish.pipeline;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeNormalizer;
import edu.stanford.nlp.trees.international.spanish.SpanishTreebankLanguagePack;
import edu.stanford.nlp.trees.international.spanish.SpanishXMLTreeReaderFactory;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;

/**
 * A utility to build unigram part-of-speech tagging data from XML
 * corpus files from the AnCora corpus.
 *
 * The constructed tagger is used to tag the constituent tokens of
 * multi-word expressions, which have no tags in the AnCora corpus.
 *
 * For invocation options, run the program with no arguments.
 *
 * @author Jon Gauthier
 */
public class AnCoraPOSStats  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AnCoraPOSStats.class);

  private final TwoDimensionalCounter<String, String> unigramTagger;
  private static final String ANCORA_ENCODING = "ISO8859_1";

  private List<File> fileList;
  private String outputPath;

  public AnCoraPOSStats(List<File> fileList, String outputPath) {
    this.fileList = fileList;
    this.outputPath = outputPath;

    unigramTagger = new TwoDimensionalCounter<>();
  }

  public void process() throws IOException {
    SpanishXMLTreeReaderFactory trf = new SpanishXMLTreeReaderFactory();

    Tree t;
    for (File file : fileList) {
      Reader in =
        new BufferedReader(new InputStreamReader(new FileInputStream(file), ANCORA_ENCODING));
      TreeReader tr = trf.newTreeReader(in);

      // Tree reading will implicitly perform tree normalization for us
      while ((t = tr.readTree()) != null) {
        // Update tagger with this tree
        for (CoreLabel leafLabel : t.taggedLabeledYield()) {
          if (leafLabel.tag().equals(SpanishTreeNormalizer.MW_TAG))
            continue;

          unigramTagger.incrementCount(leafLabel.word(), leafLabel.tag());
        }
      }
    }
  }

  public TwoDimensionalCounter<String, String> getUnigramTagger() {
    return unigramTagger;
  }

  private static final String usage =
    String.format("Usage: java %s -o <output_path> file(s)%n%n", AnCoraPOSStats.class.getName());

  private static final Map<String, Integer> argOptionDefs = new HashMap<>();
  static {
    argOptionDefs.put("o", 1);
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      log.info(usage);
      System.exit(1);
    }

    Properties options = StringUtils.argsToProperties(args, argOptionDefs);

    String outputPath = options.getProperty("o");
    if (outputPath == null)
      throw new IllegalArgumentException("-o argument (output path for built tagger) is required");

    String[] remainingArgs = options.getProperty("").split(" ");
    List<File> fileList = new ArrayList<>();
    for (String arg : remainingArgs)
      fileList.add(new File(arg));

    AnCoraPOSStats stats = new AnCoraPOSStats(fileList, outputPath);
    stats.process();

    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outputPath));
    TwoDimensionalCounter<String, String> tagger = stats.getUnigramTagger();
    oos.writeObject(tagger);

    System.out.printf("Wrote tagger to %s%n", outputPath);
  }

}
