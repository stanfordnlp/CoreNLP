package edu.stanford.nlp.pipeline;

import java.io.IOException;
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
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.ud.UniversalDependenciesFeatureAnnotator;
import edu.stanford.nlp.util.*;

import java.util.function.Function;
import java.util.function.Predicate;

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

  private final int kBest;

  private final GrammaticalStructureFactory gsf;

  private final int nThreads;

  private final boolean saveBinaryTrees;

  private final boolean keepPunct;

  private UniversalDependenciesFeatureAnnotator featureAnnotator = null;

  /** If true, don't re-annotate sentences that already have a tree annotation */
  private final boolean noSquash;
  private final GrammaticalStructure.Extras extraDependencies;

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
    this.kBest = 1;
    this.keepPunct = false;
    if (this.BUILD_GRAPHS) {
      TreebankLanguagePack tlp = parser.getTLPParams().treebankLanguagePack();
      this.gsf = tlp.grammaticalStructureFactory(tlp.punctuationWordRejectFilter(), parser.getTLPParams().typedDependencyHeadFinder());
      if (this.gsf instanceof UniversalEnglishGrammaticalStructureFactory) {
        try {
          this.featureAnnotator = new UniversalDependenciesFeatureAnnotator();
        } catch (IOException e) {
          //do nothing
        }
      }
    } else {
      this.gsf = null;
    }
    
    this.nThreads = 1;
    this.saveBinaryTrees = false;
    this.noSquash = false;
    this.extraDependencies = GrammaticalStructure.Extras.NONE;
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

    this.maxParseTime = PropertiesUtils.getLong(props, annotatorName + ".maxtime", -1);

    this.kBest = PropertiesUtils.getInt(props, annotatorName + ".kbest", 1);

    this.keepPunct = PropertiesUtils.getBool(props, annotatorName + ".keepPunct", false);


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
      boolean generateOriginalDependencies = PropertiesUtils.getBool(props, annotatorName + ".originalDependencies", false);
      parser.getTLPParams().setGenerateOriginalDependencies(generateOriginalDependencies);
      TreebankLanguagePack tlp = parser.getTLPParams().treebankLanguagePack();
      Predicate<String> punctFilter = this.keepPunct ? Filters.acceptFilter() : tlp.punctuationWordRejectFilter();
      this.gsf = tlp.grammaticalStructureFactory(punctFilter, parser.getTLPParams().typedDependencyHeadFinder());
      if (this.gsf instanceof UniversalEnglishGrammaticalStructureFactory) {
        try {
          this.featureAnnotator = new UniversalDependenciesFeatureAnnotator();
        } catch (IOException e) {
          //do nothing
        }
      }
    } else {
      this.gsf = null;
    }

    this.nThreads = PropertiesUtils.getInt(props, annotatorName + ".nthreads", PropertiesUtils.getInt(props, "nthreads", 1));
    boolean usesBinary = StanfordCoreNLP.usesBinaryTrees(props);
    this.saveBinaryTrees = PropertiesUtils.getBool(props, annotatorName + ".binaryTrees", usesBinary);
    this.noSquash = PropertiesUtils.getBool(props, annotatorName + ".nosquash", false);
    this.extraDependencies = MetaClass.cast(props.getProperty(annotatorName + ".extradependencies", "NONE"), GrammaticalStructure.Extras.class);
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
            props.getProperty(annotatorName + ".maxtime", "-1"));
    os.append(annotatorName + ".originalDependencies:" +
            props.getProperty(annotatorName + ".originalDependencies", "false"));
    os.append(annotatorName + ".buildgraphs:" +
      props.getProperty(annotatorName + ".buildgraphs", "true"));
    os.append(annotatorName + ".nthreads:" +
              props.getProperty(annotatorName + ".nthreads", props.getProperty("nthreads", "")));
    os.append(annotatorName + ".nosquash:" +
      props.getProperty(annotatorName + ".nosquash", "false"));
    os.append(annotatorName + ".extradependencies:" +
        props.getProperty(annotatorName + ".extradependences", "NONE").toLowerCase());
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
    List<Tree> trees = null;
    // generate the constituent tree
    if (maxSentenceLength <= 0 || words.size() <= maxSentenceLength) {
      try {
        final List<ParserConstraint> constraints = sentence.get(ParserAnnotations.ConstraintAnnotation.class);
        trees = doOneSentence(constraints, words);
      } catch (RuntimeInterruptedException e) {
        if (VERBOSE) {
          System.err.println("Took too long parsing: " + words);
        }
        trees = null;
      }
    }
    // tree == null may happen if the parser takes too long or if
    // the sentence is longer than the max length
    if (trees == null || trees.size() < 1) {
      doOneFailedSentence(annotation, sentence);
    } else {
      finishSentence(sentence, trees);
    }
  }

  @Override
  public void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    final List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
    Tree tree = ParserUtils.xTree(words);
    for (CoreLabel word : words) {
      if (word.tag() == null) {
        word.setTag("XX");
      }
    }

    List<Tree> trees = Generics.newArrayList(1);
    trees.add(tree);
    finishSentence(sentence, trees);
  }

  private void finishSentence(CoreMap sentence, List<Tree> trees) {

    if (treeMap != null) {
      List<Tree> mappedTrees = Generics.newLinkedList();
      for (Tree tree : trees) {
        Tree mappedTree = treeMap.apply(tree);
        mappedTrees.add(mappedTree);
      }
      trees = mappedTrees;
    }
    
    ParserAnnotatorUtils.fillInParseAnnotations(VERBOSE, BUILD_GRAPHS, gsf, sentence, trees, extraDependencies, featureAnnotator);

    if (saveBinaryTrees) {
      TreeBinarizer binarizer = TreeBinarizer.simpleTreeBinarizer(parser.getTLPParams().headFinder(), parser.treebankLanguagePack());
      Tree binarized = binarizer.transformTree(trees.get(0));
      Trees.convertToCoreLabels(binarized);
      sentence.set(TreeCoreAnnotations.BinarizedTreeAnnotation.class, binarized);
    }
  }

  private List<Tree> doOneSentence(List<ParserConstraint> constraints,
                             List<CoreLabel> words) {
    ParserQuery pq = parser.parserQuery();
    pq.setConstraints(constraints);
    pq.parse(words);
    List<ScoredObject<Tree>> scoredObjects = null;
    List<Tree> trees = Generics.newLinkedList();
    try {
      scoredObjects = pq.getKBestPCFGParses(this.kBest);
      if (scoredObjects == null || scoredObjects.size() < 1) {
        System.err.println("WARNING: Parsing of sentence failed.  " +
                "Will ignore and continue: " +
                Sentence.listToString(words));
      } else {
        for (ScoredObject<Tree> so : scoredObjects) {
          // -10000 denotes unknown words
          Tree tree = so.object();
          tree.setScore(so.score() % - 10000.0);
          trees.add(tree);
        }
      }
    } catch (OutOfMemoryError e) {
      System.err.println("WARNING: Parsing of sentence ran out of memory.  " +
              "Will ignore and continue: " +
              Sentence.listToString(words));
    } catch (NoSuchParseException e) {
      System.err.println("WARNING: Parsing of sentence failed, possibly because of out of memory.  " +
              "Will ignore and continue: " +
              Sentence.listToString(words));
    }
    return trees;
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
