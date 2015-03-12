package edu.stanford.nlp.pipeline;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.common.NoSuchParseException;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.common.ParserUtils;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.CoreMap;
import java.util.function.Function;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.StringUtils;

/**
 * This class will add parse information to an Annotation.
 * It assumes that the Annotation already contains the tokenized words
 * as a {@code List<CoreLabel>} in the TokensAnnotation under each
 * particular CoreMap in the SentencesAnnotation.
 * If the words have POS tags, they will be used.
 * <br>
 * Parse trees are added to each sentence's CoreMap (get with
 * {@code CoreAnnotations.SentencesAnnotation}) under
 * {@code CoreAnnotations.TreeAnnotation}).
 *
 * @author Jenny Finkel
 */
public class ParserAnnotator extends SentenceAnnotator {

  private final boolean VERBOSE;
  private final boolean BUILD_GRAPHS;
  private final ParserGrammar parser;

  private final Function<Tree, Tree> treeMap;

  /** Do not parse sentences larger than this sentence length */
  private final int maxSentenceLength;

  /**
   * Stop parsing if we exceed this time limit, in milliseconds.
   * Use 0 for no limit.
   */
  private final long maxParseTime;

  private final GrammaticalStructureFactory gsf;

  private final int nThreads;

  private final boolean saveBinaryTrees;

  /** If true, don't re-annotate sentences that already have a tree annotation */
  private final boolean noSquash;

  public ParserAnnotator(boolean verbose, int maxSent) {
    this(System.getProperty("parse.model", LexicalizedParser.DEFAULT_PARSER_LOC), verbose, maxSent, StringUtils.EMPTY_STRING_ARRAY);
  }

  public ParserAnnotator(String parserLoc,
                         boolean verbose,
                         int maxSent,
                         String[] flags) {
    this(loadModel(parserLoc, verbose, flags), verbose, maxSent);
  }

  public ParserAnnotator(ParserGrammar parser, boolean verbose, int maxSent) {
    this(parser, verbose, maxSent, null);
  }

  public ParserAnnotator(ParserGrammar parser, boolean verbose, int maxSent, Function<Tree, Tree> treeMap) {
    VERBOSE = verbose;
    this.BUILD_GRAPHS = parser.getTLPParams().supportsBasicDependencies();
    this.parser = parser;
    this.maxSentenceLength = maxSent;
    this.treeMap = treeMap;
    this.maxParseTime = 0;
    if (this.BUILD_GRAPHS) {
      TreebankLanguagePack tlp = parser.getTLPParams().treebankLanguagePack();
      this.gsf = tlp.grammaticalStructureFactory(tlp.punctuationWordRejectFilter(), parser.getTLPParams().typedDependencyHeadFinder());
    } else {
      this.gsf = null;
    }
    this.nThreads = 1;
    this.saveBinaryTrees = false;
    this.noSquash = false;
  }


  public ParserAnnotator(String annotatorName, Properties props) {
    String model = props.getProperty(annotatorName + ".model", LexicalizedParser.DEFAULT_PARSER_LOC);
    if (model == null) {
      throw new IllegalArgumentException("No model specified for Parser annotator " + annotatorName);
    }
    this.VERBOSE = PropertiesUtils.getBool(props, annotatorName + ".debug", false);

    String[] flags = convertFlagsToArray(props.getProperty(annotatorName + ".flags"));
    this.parser = loadModel(model, VERBOSE, flags);
    this.maxSentenceLength = PropertiesUtils.getInt(props, annotatorName + ".maxlen", -1);

    String treeMapClass = props.getProperty(annotatorName + ".treemap");
    if (treeMapClass == null) {
      this.treeMap = null;
    } else {
      this.treeMap = ReflectionLoading.loadByReflection(treeMapClass, props);
    }

    this.maxParseTime = PropertiesUtils.getLong(props, annotatorName + ".maxtime", 0);

    String buildGraphsProperty = annotatorName + ".buildgraphs";
    if (!this.parser.getTLPParams().supportsBasicDependencies()) {
      if (props.getProperty(buildGraphsProperty) != null && PropertiesUtils.getBool(props, buildGraphsProperty)) {
        System.err.println("WARNING: " + buildGraphsProperty + " set to true, but " + this.parser.getTLPParams().getClass() + " does not support dependencies");
      }
      this.BUILD_GRAPHS = false;
    } else {
      this.BUILD_GRAPHS = PropertiesUtils.getBool(props, buildGraphsProperty, true);
    }

    if (this.BUILD_GRAPHS) {
      TreebankLanguagePack tlp = parser.getTLPParams().treebankLanguagePack();
      // TODO: expose keeping punctuation as an option to the user?
      this.gsf = tlp.grammaticalStructureFactory(tlp.punctuationWordRejectFilter(), parser.getTLPParams().typedDependencyHeadFinder());
    } else {
      this.gsf = null;
    }

    this.nThreads = PropertiesUtils.getInt(props, annotatorName + ".nthreads", PropertiesUtils.getInt(props, "nthreads", 1));
    boolean usesBinary = StanfordCoreNLP.usesBinaryTrees(props);
    this.saveBinaryTrees = PropertiesUtils.getBool(props, annotatorName + ".binaryTrees", usesBinary);
    this.noSquash = PropertiesUtils.getBool(props, annotatorName + ".nosquash", false);
  }

  public static String signature(String annotatorName, Properties props) {
    StringBuilder os = new StringBuilder();
    os.append(annotatorName + ".model:" +
            props.getProperty(annotatorName + ".model",
                    LexicalizedParser.DEFAULT_PARSER_LOC));
    os.append(annotatorName + ".debug:" +
            props.getProperty(annotatorName + ".debug", "false"));
    os.append(annotatorName + ".flags:" +
            props.getProperty(annotatorName + ".flags", ""));
    os.append(annotatorName + ".maxlen:" +
            props.getProperty(annotatorName + ".maxlen", "-1"));
    os.append(annotatorName + ".treemap:" +
            props.getProperty(annotatorName + ".treemap", ""));
    os.append(annotatorName + ".maxtime:" +
            props.getProperty(annotatorName + ".maxtime", "0"));
    os.append(annotatorName + ".buildgraphs:" +
            props.getProperty(annotatorName + ".buildgraphs", "true"));
    os.append(annotatorName + ".nthreads:" +
              props.getProperty(annotatorName + ".nthreads", props.getProperty("nthreads", "")));
    boolean usesBinary = StanfordCoreNLP.usesBinaryTrees(props);
    boolean saveBinaryTrees = PropertiesUtils.getBool(props, annotatorName + ".binaryTrees", usesBinary);
    os.append(annotatorName + ".binaryTrees:" + saveBinaryTrees);

    return os.toString();
  }

  public static String[] convertFlagsToArray(String parserFlags) {
    if (parserFlags == null || parserFlags.trim().equals("")) {
      return StringUtils.EMPTY_STRING_ARRAY;
    } else {
      return parserFlags.trim().split("\\s+");
    }
  }

  private static ParserGrammar loadModel(String parserLoc,
                                         boolean verbose,
                                         String[] flags) {
    if (verbose) {
      System.err.println("Loading Parser Model [" + parserLoc + "] ...");
      System.err.print("  Flags:");
      for (String flag : flags) {
        System.err.print("  " + flag);
      }
      System.err.println();
    }
    ParserGrammar result = ParserGrammar.loadModel(parserLoc);
    result.setOptionFlags(result.defaultCoreNLPFlags());
    result.setOptionFlags(flags);

    return result;
  }

  @Override
  protected int nThreads() {
    return nThreads;
  }

  @Override
  protected long maxTime() {
    return maxParseTime;
  };  

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    // If "noSquash" is set, don't re-annotate sentences which already have a tree annotation
    if (noSquash &&
        sentence.get(TreeCoreAnnotations.TreeAnnotation.class) != null &&
        !"X".equalsIgnoreCase(sentence.get(TreeCoreAnnotations.TreeAnnotation.class).label().value())) {
      return;
    }

    final List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (VERBOSE) {
      System.err.println("Parsing: " + words);
    }
    Tree tree = null;
    // generate the constituent tree
    if (maxSentenceLength <= 0 || words.size() <= maxSentenceLength) {
      try {
        final List<ParserConstraint> constraints = sentence.get(ParserAnnotations.ConstraintAnnotation.class);
        tree = doOneSentence(constraints, words);
      } catch (RuntimeInterruptedException e) {
        if (VERBOSE) {
          System.err.println("Took too long parsing: " + words);
        }
        tree = null;
      }
    }
    // tree == null may happen if the parser takes too long or if
    // the sentence is longer than the max length
    if (tree == null) {
      tree = ParserUtils.xTree(words);
      for (CoreLabel word : words) {
        if (word.tag() == null) {
          word.setTag("X");
        }
      }
    }

    if (treeMap != null) {
      tree = treeMap.apply(tree);
    }

    ParserAnnotatorUtils.fillInParseAnnotations(VERBOSE, BUILD_GRAPHS, gsf, sentence, tree);

    if (saveBinaryTrees) {
      TreeBinarizer binarizer = new TreeBinarizer(parser.getTLPParams().headFinder(), parser.treebankLanguagePack(),
                                                  false, false, 0, false, false, 0.0, false, true, true);
      Tree binarized = binarizer.transformTree(tree);
      Trees.convertToCoreLabels(binarized);
      sentence.set(TreeCoreAnnotations.BinarizedTreeAnnotation.class, binarized);
    }
  }

  private Tree doOneSentence(List<ParserConstraint> constraints,
                             List<CoreLabel> words) {
    ParserQuery pq = parser.parserQuery();
    pq.setConstraints(constraints);
    pq.parse(words);
    Tree tree = null;
    try {
      tree = pq.getBestParse();
      // -10000 denotes unknown words
      tree.setScore(pq.getPCFGScore() % -10000.0);
    } catch (OutOfMemoryError e) {
      System.err.println("WARNING: Parsing of sentence ran out of memory.  " +
                         "Will ignore and continue: " +
                         Sentence.listToString(words));
    } catch (NoSuchParseException e) {
      System.err.println("WARNING: Parsing of sentence failed, possibly because of out of memory.  " +
                         "Will ignore and continue: " +
                         Sentence.listToString(words));
    }
    return tree;
  }

  @Override
  public Set<Requirement> requires() {
    return parser.requiresTags() ? TOKENIZE_SSPLIT_POS : TOKENIZE_AND_SSPLIT;
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    if (this.saveBinaryTrees) {
      return PARSE_TAG_BINARIZED_TREES;
    } else {
      return PARSE_AND_TAG;
    }
  }
}
