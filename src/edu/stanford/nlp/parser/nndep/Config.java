package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.ReflectionLoading;

import java.util.List;
import java.util.Properties;
import java.util.function.Function;

/**
 * Defines configuration settings for training and testing the
 * neural-network dependency parser.
 *
 * @see DependencyParser
 *
 * @author Danqi Chen
 * @author Jon Gauthier
 */
public class Config {

  /**
   *   Out-of-vocabulary token string.
   */
  public static final String UNKNOWN = "-UNKNOWN-";

   /**
   *   Root token string.
   */
  public static final String ROOT = "-ROOT-";

   /**
   *   Non-existent token string.
   */
  public static final String NULL = "-NULL-";

   /**
   *   Represent a non-existent token.
   */
  public static final int NONEXIST = -1;

   /**
   *   For printing messages.
   */
  public static final String SEPARATOR = "###################";

  /**
   * The language being parsed.
   */
  public Language language = Language.UniversalEnglish;

  /**
   * Number of threads to use during training. Also indirectly controls
   * how mini-batches are partitioned (more threads => more partitions
   * => smaller partitions).
   */
  public int trainingThreads = 1;

  /**
   * Refuse to train on words which have a corpus frequency less than
   * this number.
   */
  public int wordCutOff = 1;

  /**
   * Model weights will be initialized to random values within the
   * range {@code [-initRange, initRange]}.
   */
  public double initRange = 0.01;

  /**
   * Maximum number of iterations for training
   */
  public int maxIter = 20000;

  /**
   * Size of mini-batch for training. A random subset of training
   * examples of this size will be used to train the classifier on each
   * iteration.
   */
  public int batchSize = 10000;

  /**
   * An epsilon value added to the denominator of the AdaGrad
   * expression for numerical stability
   */
  public double adaEps = 1e-6;

  /**
   * Initial global learning rate for AdaGrad training
   */
  public double adaAlpha = 0.01;

  /**
   * Regularization parameter. All weight updates are scaled by this
   * single parameter.
   */
  public double regParameter = 1e-8;

  /**
   * Dropout probability. For each training example we randomly choose
   * some amount of units to disable in the neural network classifier.
   * This probability controls the proportion of units "dropped out."
   */
  public double dropProb = 0.5;

  /**
   * Size of the neural network hidden layer.
   */
  public int hiddenSize = 200;

  /**
   * Dimensionality of the word embeddings used
   */
  public int embeddingSize = 50;

  /**
   * Total number of tokens provided as input to the classifier. (Each
   * token is provided in word embedding form.)
   */
  // TODO: we can figure this out automatically based on features used.
  // Should remove this option once we make feature templates / dynamic features
  public static final int numTokens = 48;

  /**
   * Number of input tokens for which we should compute hidden-layer
   * unit activations.
   *
   * If zero, the parser will skip the pre-computation step.
   */
  public int numPreComputed = 20000;

  /**
   * Number of hidden layer activations to cache.  Only applies at test time.
   */
  public int numCached = 5000;

  /**
   * During training, run a full UAS evaluation after every
   * {@code evalPerIter} iterations.
   */
  public int evalPerIter = 100;

  /**
   * During training, clear AdaGrad gradient histories after every
   * {@code clearGradientsPerIter} iterations. (If zero, never clear
   * gradients.)
   */
  public int clearGradientsPerIter = 0;

  /**
   * Save an intermediate model file whenever we see an improved UAS
   * evaluation. (The frequency of these evaluations is configurable as
   * well; see {@link #evalPerIter}.)
   */
  public boolean saveIntermediate = true;


  /**
   * Train a labeled parser if labeled = true, and a unlabeled one otherwise.
   */
  public boolean unlabeled = false;

  /**
   * Use coarse POS instead of fine-grained POS if cPOS = true.
   */
  public boolean cPOS = false;

  /**
  *  Exclude punctuations in evaluation if noPunc = true.
  */
  public boolean noPunc = true;

  /**
  *  Update word embeddings when performing gradient descent.
  *  Set to false if you provide embeddings and do not want to finetune.
  */
  public boolean doWordEmbeddingGradUpdate = true;

  /**
   * Describes language-specific properties necessary for training and
   * testing. By default,
   * {@link edu.stanford.nlp.trees.PennTreebankLanguagePack} will be
   * used.
   */
  public TreebankLanguagePack tlp;

  // --- Runtime parsing options

  /**
   * If non-null, when parsing raw text assume sentences have already
   * been split and are separated by the given delimiter.
   *
   * If null, the parser splits sentences automatically.
   */
  public String sentenceDelimiter = null;

  /**
   * Defines a word-escaper to use when parsing raw sentences.
   *
   * As a command-line option, you should provide the fully qualified
   * class name of a valid escaper (that is, a class which implements
   * {@code Function<List<HasWord>, List<HasWord>>}).
   */
  public Function<List<HasWord>, List<HasWord>> escaper = null;

  /**
   * Path to a tagger file compatible with
   * {@link edu.stanford.nlp.tagger.maxent.MaxentTagger}.
   */
  public String tagger = MaxentTagger.DEFAULT_JAR_PATH;

  /**
   * Provided text is tokenized by whitespace.
   */
  public boolean preTokenized = false;


  public Config(Properties properties) {
    setProperties(properties);
  }

  private void setProperties(Properties props) {
    trainingThreads = PropertiesUtils.getInt(props, "trainingThreads", trainingThreads);
    wordCutOff = PropertiesUtils.getInt(props, "wordCutOff", wordCutOff);
    initRange = PropertiesUtils.getDouble(props, "initRange", initRange);
    maxIter = PropertiesUtils.getInt(props, "maxIter", maxIter);
    batchSize = PropertiesUtils.getInt(props, "batchSize", batchSize);
    adaEps = PropertiesUtils.getDouble(props, "adaEps", adaEps);
    adaAlpha = PropertiesUtils.getDouble(props, "adaAlpha", adaAlpha);
    regParameter = PropertiesUtils.getDouble(props, "regParameter", regParameter);
    dropProb = PropertiesUtils.getDouble(props, "dropProb", dropProb);
    hiddenSize = PropertiesUtils.getInt(props, "hiddenSize", hiddenSize);
    embeddingSize = PropertiesUtils.getInt(props, "embeddingSize", embeddingSize);
    numPreComputed = PropertiesUtils.getInt(props, "numPreComputed", numPreComputed);
    numCached = PropertiesUtils.getInt(props, "numCached", numCached);
    evalPerIter = PropertiesUtils.getInt(props, "evalPerIter", evalPerIter);
    clearGradientsPerIter = PropertiesUtils.getInt(props, "clearGradientsPerIter", clearGradientsPerIter);
    saveIntermediate = PropertiesUtils.getBool(props, "saveIntermediate", saveIntermediate);
    unlabeled = PropertiesUtils.getBool(props, "unlabeled", unlabeled);
    cPOS = PropertiesUtils.getBool(props, "cPOS", cPOS);
    noPunc = PropertiesUtils.getBool(props, "noPunc", noPunc);
    doWordEmbeddingGradUpdate = PropertiesUtils.getBool(props, "doWordEmbeddingGradUpdate", doWordEmbeddingGradUpdate);

    // Runtime parsing options
    sentenceDelimiter = PropertiesUtils.getString(props, "sentenceDelimiter", sentenceDelimiter);
    tagger = PropertiesUtils.getString(props, "tagger.model", tagger);

    String escaperClass = props.getProperty("escaper");
    escaper = escaperClass != null ? ReflectionLoading.loadByReflection(escaperClass) : null;

    // Language options
    language = props.containsKey("language")
               ? getLanguage(props.getProperty("language"))
               : language;
    if (language == null) {
      throw new IllegalArgumentException("Unknown language " + props.containsKey("language"));
    }
    if (language.params == null) {
      throw new IllegalArgumentException("Language " + language + " does not support parsing!");
    }
    tlp = language.params.treebankLanguagePack();
    preTokenized = PropertiesUtils.getBool(props, "tokenized", preTokenized);

    // if a tlp was specified go with that
    String tlpCanonicalName = props.getProperty("tlp");
    if (tlpCanonicalName != null) {
      try {
        tlp = ReflectionLoading.loadByReflection(tlpCanonicalName);
        System.err.println("Loaded TreebankLanguagePack: "+tlpCanonicalName);
      } catch (Exception e) {
        System.err.println("Error: Failed to load TreebankLanguagePack: "+tlpCanonicalName);
      }
    }
  }

  /**
   * Get the {@link edu.stanford.nlp.international.Language}
   * object corresponding to the given language string.
   *
   * @return A {@link edu.stanford.nlp.international.Language}
   *         or {@code null} if no instance matches the given string.
   */
  public static Language getLanguage(String languageStr) {
    for (Language l : Language.values()) {
      if (l.name().equalsIgnoreCase(languageStr))
        return l;
    }
    return null;
  }

  public void printParameters() {
    System.err.printf("language = %s%n", language);
    System.err.printf("trainingThreads = %d%n", trainingThreads);
    System.err.printf("wordCutOff = %d%n", wordCutOff);
    System.err.printf("initRange = %.2g%n", initRange);
    System.err.printf("maxIter = %d%n", maxIter);
    System.err.printf("batchSize = %d%n", batchSize);
    System.err.printf("adaEps = %.2g%n", adaEps);
    System.err.printf("adaAlpha = %.2g%n", adaAlpha);
    System.err.printf("regParameter = %.2g%n", regParameter);
    System.err.printf("dropProb = %.2g%n", dropProb);
    System.err.printf("hiddenSize = %d%n", hiddenSize);
    System.err.printf("embeddingSize = %d%n", embeddingSize);
    System.err.printf("numPreComputed = %d%n", numPreComputed);
    System.err.printf("numCached = %d%n", numCached);
    System.err.printf("evalPerIter = %d%n", evalPerIter);
    System.err.printf("clearGradientsPerIter = %d%n", clearGradientsPerIter);
    System.err.printf("saveItermediate = %b%n", saveIntermediate);
    System.err.printf("unlabeled = %b%n", unlabeled);
    System.err.printf("cPOS = %b%n", cPOS);
    System.err.printf("noPunc = %b%n", noPunc);
    System.err.printf("doWordEmbeddingGradUpdate = %b%n", doWordEmbeddingGradUpdate);
  }

}
