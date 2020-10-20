package edu.stanford.nlp.pipeline;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.common.NoSuchParseException;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.common.ParserUtils;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;


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
public class ParserAnnotator extends SentenceAnnotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ParserAnnotator.class);

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

  /** Whether to include punctuation dependencies in the output. Starting in 2015, the default is true. */
  private final boolean keepPunct;

  /** If true, don't re-annotate sentences that already have a tree annotation */
  private final boolean noSquash;
  private final GrammaticalStructure.Extras extraDependencies;

  // around this height, protobuf might potentially barf
  private final static int DEFAULT_MAX_HEIGHT = 80;
  private final int maxHeight;
  
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
    this.VERBOSE = verbose;
    this.BUILD_GRAPHS = parser.getTLPParams().supportsBasicDependencies();
    this.parser = parser;
    this.maxSentenceLength = maxSent;
    this.treeMap = treeMap;
    this.maxParseTime = 0;
    this.kBest = 1;
    this.keepPunct = true;
    if (this.BUILD_GRAPHS) {
      TreebankLanguagePack tlp = parser.getTLPParams().treebankLanguagePack();
      this.gsf = tlp.grammaticalStructureFactory(tlp.punctuationWordRejectFilter(), parser.getTLPParams().typedDependencyHeadFinder());
    } else {
      this.gsf = null;
    }

    this.nThreads = 1;
    this.saveBinaryTrees = false;
    this.noSquash = false;
    this.extraDependencies = GrammaticalStructure.Extras.NONE;
    this.maxHeight = DEFAULT_MAX_HEIGHT;
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
    this.maxHeight = PropertiesUtils.getInt(props, annotatorName + ".maxheight", DEFAULT_MAX_HEIGHT);

    String treeMapClass = props.getProperty(annotatorName + ".treemap");
    if (treeMapClass == null) {
      this.treeMap = null;
    } else {
      this.treeMap = ReflectionLoading.loadByReflection(treeMapClass, props);
    }

    this.maxParseTime = PropertiesUtils.getLong(props, annotatorName + ".maxtime", -1);

    this.kBest = PropertiesUtils.getInt(props, annotatorName + ".kbest", 1);

    this.keepPunct = PropertiesUtils.getBool(props, annotatorName + ".keepPunct", true);


    String buildGraphsProperty = annotatorName + ".buildgraphs";
    if ( ! this.parser.getTLPParams().supportsBasicDependencies()) {
      if (PropertiesUtils.getBool(props, buildGraphsProperty)) {
        log.info("WARNING: " + buildGraphsProperty + " set to true, but " + this.parser.getTLPParams().getClass() + " does not support dependencies");
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
    } else {
      this.gsf = null;
    }

    this.nThreads = PropertiesUtils.getInt(props, annotatorName + ".nthreads", PropertiesUtils.getInt(props, "nthreads", 1));
    boolean usesBinary = StanfordCoreNLP.usesBinaryTrees(props);
    this.saveBinaryTrees = PropertiesUtils.getBool(props, annotatorName + ".binaryTrees", usesBinary);
    this.noSquash = PropertiesUtils.getBool(props, annotatorName + ".nosquash", false);
    this.extraDependencies = MetaClass.cast(props.getProperty(annotatorName + ".extradependencies", "NONE"), GrammaticalStructure.Extras.class);
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
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
    os.append(annotatorName + ".maxheight:" +
            props.getProperty(annotatorName + ".maxheight", Integer.toString(DEFAULT_MAX_HEIGHT)));
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
    os.append(annotatorName + ".keepPunct:" +
      props.getProperty(annotatorName + ".keepPunct", "true"));
    os.append(annotatorName + ".extradependencies:" +
        props.getProperty(annotatorName + ".extradependencies", "NONE").toLowerCase());
    boolean usesBinary = StanfordCoreNLP.usesBinaryTrees(props);
    boolean saveBinaryTrees = PropertiesUtils.getBool(props, annotatorName + ".binaryTrees", usesBinary);
    os.append(annotatorName + ".binaryTrees:" + saveBinaryTrees);

    return os.toString();
  }

  private static String[] convertFlagsToArray(String parserFlags) {
    if (parserFlags == null || parserFlags.trim().isEmpty()) {
      return StringUtils.EMPTY_STRING_ARRAY;
    } else {
      return parserFlags.trim().split("\\s+");
    }
  }

  private static ParserGrammar loadModel(String parserLoc,
                                         boolean verbose,
                                         String[] flags) {
    if (verbose) {
      log.info("Loading Parser Model [" + parserLoc + "] ...");
      log.info("  Flags:");
      for (String flag : flags) {
        log.info("  " + flag);
      }
      log.info();
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
  }

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
      log.info("Parsing: " + words);
    }
    List<Tree> trees = null;
    // generate the constituent tree
    if (maxSentenceLength <= 0 || words.size() <= maxSentenceLength) {
      try {
        final List<ParserConstraint> constraints = sentence.get(ParserAnnotations.ConstraintAnnotation.class);
        trees = doOneSentence(constraints, words);
      } catch (RuntimeInterruptedException e) {
        if (VERBOSE) {
          log.info("Took too long parsing: " + words);
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

    List<Tree> trees = new ArrayList<>();
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

    if (maxHeight > 0) {
      trees = ParserUtils.flattenTallTrees(maxHeight, trees);
    }

    ParserAnnotatorUtils.fillInParseAnnotations(VERBOSE, BUILD_GRAPHS, gsf, sentence, trees, extraDependencies);

    if (saveBinaryTrees) {
      TreeBinarizer binarizer = TreeBinarizer.simpleTreeBinarizer(parser.getTLPParams().headFinder(), parser.treebankLanguagePack());
      Tree binarized = binarizer.transformTree(trees.get(0));
      Trees.convertToCoreLabels(binarized);
      sentence.set(TreeCoreAnnotations.BinarizedTreeAnnotation.class, binarized);
    }

    // for some reason in some corner cases nodes aren't having sentenceIndex set
    // do a pass and make sure all nodes have sentenceIndex set
    SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    if (sg != null) {
      for (IndexedWord iw : sg.vertexSet()) {
        if (iw.get(CoreAnnotations.SentenceIndexAnnotation.class) == null
                && sentence.get(CoreAnnotations.SentenceIndexAnnotation.class) != null) {
          iw.setSentIndex(sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
        }
      }
    }
  }

  private List<Tree> doOneSentence(List<ParserConstraint> constraints,
                             List<CoreLabel> words) {
    ParserQuery pq = parser.parserQuery();
    pq.setConstraints(constraints);
    pq.parse(words);
    List<Tree> trees = Generics.newLinkedList();
    try {
      // Use bestParse if kBest is set to 1.
      if (this.kBest == 1) {
        Tree t = pq.getBestParse();
        if (t == null) {
          log.warn("Parsing of sentence failed.  " +
              "Will ignore and continue: " +
              SentenceUtils.listToString(words));
        } else {
          double score = pq.getBestScore();
          t.setScore(score % -10000.0);
          trees.add(t);
        }
      } else {
        List<ScoredObject<Tree>> scoredObjects = pq.getKBestParses(this.kBest);
        if (scoredObjects == null || scoredObjects.size() < 1) {
          log.warn("Parsing of sentence failed.  " +
              "Will ignore and continue: " +
              SentenceUtils.listToString(words));
        } else {
          for (ScoredObject<Tree> so : scoredObjects) {
            // -10000 denotes unknown words
            Tree tree = so.object();
            tree.setScore(so.score() % -10000.0);
            trees.add(tree);
          }
        }
      }
    } catch (OutOfMemoryError e) {
      log.error(e); // Beware that we can now get an OOM in logging, too.
      log.warn("Parsing of sentence ran out of memory (length=" + words.size() + ").  " +
              "Will ignore and try to continue.");
    } catch (NoSuchParseException e) {
      log.warn("Parsing of sentence failed, possibly because of out of memory.  " +
              "Will ignore and continue: " +
              SentenceUtils.listToString(words));
    }
    return trees;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    if (parser.requiresTags()) {
      return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
          CoreAnnotations.TextAnnotation.class,
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.ValueAnnotation.class,
          CoreAnnotations.OriginalTextAnnotation.class,
          CoreAnnotations.CharacterOffsetBeginAnnotation.class,
          CoreAnnotations.CharacterOffsetEndAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class,
          CoreAnnotations.PartOfSpeechAnnotation.class
      )));
    } else {
      return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
          CoreAnnotations.TextAnnotation.class,
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.ValueAnnotation.class,
          CoreAnnotations.OriginalTextAnnotation.class,
          CoreAnnotations.CharacterOffsetBeginAnnotation.class,
          CoreAnnotations.CharacterOffsetEndAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class
      )));
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    if (this.BUILD_GRAPHS) {
      if (this.saveBinaryTrees) {
        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
            CoreAnnotations.PartOfSpeechAnnotation.class,
            TreeCoreAnnotations.TreeAnnotation.class,
            TreeCoreAnnotations.BinarizedTreeAnnotation.class,
            SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class,
            CoreAnnotations.BeginIndexAnnotation.class,
            CoreAnnotations.EndIndexAnnotation.class,
            CoreAnnotations.CategoryAnnotation.class
        )));
      } else {
        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
            CoreAnnotations.PartOfSpeechAnnotation.class,
            TreeCoreAnnotations.TreeAnnotation.class,
            SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class,
            SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class,
            CoreAnnotations.BeginIndexAnnotation.class,
            CoreAnnotations.EndIndexAnnotation.class,
            CoreAnnotations.CategoryAnnotation.class
        )));
      }
    } else {
      if (this.saveBinaryTrees) {
        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
            CoreAnnotations.PartOfSpeechAnnotation.class,
            TreeCoreAnnotations.TreeAnnotation.class,
            TreeCoreAnnotations.BinarizedTreeAnnotation.class,
            CoreAnnotations.CategoryAnnotation.class
        )));
      } else {
        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
            CoreAnnotations.PartOfSpeechAnnotation.class,
            TreeCoreAnnotations.TreeAnnotation.class,
            CoreAnnotations.CategoryAnnotation.class
        )));
      }
    }
  }

}
