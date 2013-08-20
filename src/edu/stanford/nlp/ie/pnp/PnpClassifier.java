package edu.stanford.nlp.ie.pnp;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.ie.AnswerChecker;
import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.BasicDataCollection;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.io.*;
import java.util.*;

/**
 * Statistical classifier of unseen proper noun phrases. Supports training and testing
 * on data files. Uses an n-gram word-length model, and n-gram character model, and
 * a word model.
 * <p/>
 * <b>Standard usage:</b>
 * <ul>
 * <li>To train a new PnpClassifier, call {@link #PnpClassifier(String,Properties)}.
 * <li>To get the probability of generating a given string for a given category,
 * call {@link #getLogProb(String line,int category)}.
 * <li>To find the most probable category for a given string, call
 * {@link #getBestCategory(String line)}.
 * <li>To generate a novel string for a given category, call {@link #generateLine(int category)}
 * </ul>
 * <p/>
 * For more information on the design and implementation of this classifier, as well
 * as experimental results, see the following tech report:
 * <p/>
 * Joseph Smarr and Christopher D. Manning. 2002.
 * <a href="http://dbpubs.stanford.edu/pub/2002-46">Classifying unknown proper noun phrases without context.</a>
 * Technical Report dbpubs/2002-46, Stanford University, Stanford, CA.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class PnpClassifier implements Classifier, Serializable {
  // runtime debug flags
  private boolean DEBUG = false; // whether to print debugging output
  private boolean PRINT_SCORES = false; // whether to print scores for each category for each line or just the best-guess category (normal mode)
  private boolean PRINT_RUNNING_SCORES = false; // whether to print detailed scores for each test item (length score, char score, etc)

  // defaults for all properties (created the first time it's called)
  private static Properties defaultProperties = null;

  // tunable n-gram parameters
  public final int ln; // length of n-grams for word lengths
  public final int cn; // length of n-grams for char sequences

  // global constants
  public final char startSymbol; // dummy start char to pad n-grams
  public final char endSymbol;   // special "end of string" symbol
  public final int[] charBinCutoffs; // count-bins for char n-gram EM interplation params (each number is the upper-end of a bin, plus there's more more bin for everything bigger than the biggest bin)
  public final int[] lengthBinCutoffs; // count-bins for length n-gram EM interpolation params (each number is the upper-end of a bin, plus there's more more bin for everything bigger than the biggest bin)
  public static final Random rand = new Random(); // used for n-gram generation
  public static final double char0GramProb = 1.0 / 256; // uniform char n-gram backoff
  public static final double length0GramProb = 0.01; // uniform length n-gram backoff (arbitrarily assumes no line has more than 100 words)

  // global variables
  private int numCategories; // total number of different categories
  private int numExamples;   // total number of training examples
  private double priorBoost; // log-multiplier boost for prior probability
  private double lengthNormalization; // learned constant for normalizing word probabilities based on their length

  // cross-validation storage and parameters
  private List[] heldOutExamples; // held-out examples for parameter estimation (for each category)
  private List[] heldOutWeights; // Double weights for each held-out example (for each category)
  private boolean parametersTuned; // has tuneParameters been called
  private final int heldOutPercent;  // percent of training examples to hold out for parameter estimation
  private final double charConvergenceMargin; // stop char n-gram's EM when each param changes by less than this amount
  private final double lengthConvergenceMargin; // stop length n-gram's EM when each param changes by less than this amount
  private final int numCharWordSteps; // number of steps (i.e. resolution) in which to look for optimal char-word interpolation
  private final int maxPriorBoost; // largest log-prior multiplier to evaluate on held-out data

  // model feature flags
  private final boolean useCharModel; // whether to include the char n-gram in the model
  private final boolean useLengthModel; // whether to include the length n-gram in the model
  private final boolean useWordModel; // whether to include the common-word model
  private final boolean useLengthNormalization; // whether to normalize word probs based on their length
  private final boolean usePriorBoost; // whether to boost the category prior in the model

  // mapping from Object labels to internal category numbers (used for Classify API)
  private final Index labelIndex;

  //////////////////////////////////
  // variables for each category ///
  //////////////////////////////////

  // category priors
  private int[] categoryCounts; // total number of training examples for each category

  // length n-gram
  private ClassicCounter[] lengthSequenceCounts; // List of Integers (length n-gram) -> count
  private double[][][] lengthInterpolationConstants; // deleted-interpolation weights for length n-gram (category x context-length x bin)
  private int[] wordTotalCounts; // total number of words seen for each category (used to normalize unigram counts)

  // char n-gram
  private ClassicCounter<String>[] charSequenceCounts; // String (char n-gram) -> count
  private double[][][] charInterpolationConstants; // deleted-interpolation weights for char n-gram (category x context-length x bin)
  private ClassicCounter<Integer>[] charSequenceTotalsByLength; // Integer (word length) -> total prob of words this long made by char n-grams
  private int[] charTotalCounts; // total number of chars seen for each category (used to normalize unigram counts)
  private ClassicCounter<String>[] cachedCharSequenceInterpolatedProbs; // String (char n-gram) -> cached interpolated prob

  // words by length
  private ClassicCounter[] wordCounts; // String (word) -> count
  private ClassicCounter[] wordTotalsByLength; // Integer (word length) -> total # words

  // char-word interpolation
  private ClassicCounter[] charWordInterpolationConstants; // Integer (word length) -> weight [0-1] on n-gram-vs.-word for this length

  // word-length normalization
  private double[] lengthNormalizations; // learned constant in word-legth normalizations for each category

  /**
   * Constructs a new PnpClassifier which is trained on the given file.
   * Number of categories is inferred from reading the training file. The first
   * line of the training file should just be an integer, indicating the total
   * number of categories in the training set. Each subsequent line should be of
   * the format "# rest of example" (excluding quotes) where # is the category
   * (1-n, don't use category 0) and "rest of example" is the full example line.
   * Training is first performed on all but a held out set of data. Then various
   * parameters are set on the held out data. Finally, the held out data is also
   * trained on.
   *
   * @param trainingFilename file with num categories and training examples
   * @param props            properties to customize the behavior of PnpClassifier (see
   *                         other constructor comment) - if null, {@link #getDefaultProperties} is used -
   *                         NOTE: numCategories property is overridden by the value in the training file.
   */
  public PnpClassifier(String trainingFilename, Properties props) {
    this(props);

    try {
      if (DEBUG) {
        System.err.println(new Date() + " reading and counting training file");
      }
      BufferedReader br = new BufferedReader(new FileReader(trainingFilename));

      // reads the number of categories off of the first line
      numCategories = Integer.parseInt(br.readLine());
      initCounts(); // do it again with the new numCategories

      String line;
      while ((line = br.readLine()) != null) {
        // pulls out the category number and counts the example
        // lines look like: "# rest of example" (# is category num)
        int firstSpace = line.indexOf(' ');
        int category = Integer.parseInt(line.substring(0, firstSpace));

        // computes and updates all relevant feature counts
        addCounts(line.substring(firstSpace + 1), category, true);
      }

      // learns parameters on held-out data and then retrains on all data
      tuneParameters();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Collection labels() {
    return labelIndex.objectsList();
  }

  /**
   * Initializes the PnpClassifier with the given properties and zeroes
   * all internal counts. If <tt>props</tt> is null,
   * {@link #getDefaultProperties} is used instead.
   * <p/>
   * The following properties are currently supported:
   * <p/>
   * <table border=1 cellpadding=3 cellspacing=0 style="font: 8pt monospace">
   * <tr bgcolor="#ff9fff"><th>property</th><th>values</th><th>default</th></tr>
   * <p/>
   * <tr><td colspan=3 bgcolor="#9fffff">Model parameters</td></tr>
   * <p/>
   * <tr><td>numCategories</td><td>number of different categories to model</td><td>1</td></tr>
   * <tr><td>cn</td><td>max length of char n-gram</td></td><td>6</td></tr>
   * <tr><td>ln</td><td>max length of length n-gram</td></td><td>4</td></tr>
   * <p/>
   * <tr><td>startSymbol</td><td>dummy start char to pad n-grams</td></td><td> [space]</td></tr>
   * <tr><td>endSymbol</td><td>special "end of string" char</td></td><td>^</td></tr>
   * <p/>
   * <tr><td>charBinCutoffs</td><td>count-bins for char n-gram EM interplation params
   * (each number is the upper-end of a bin, plus there's more more bin for everything bigger
   * than the biggest bin) - separate values by space or comma</td></td><td>0 5 50 500 5000</td></tr>
   * <tr><td>lengthBinCutoffs</td><td>count-bins for length n-gram EM interplation params
   * (each number is the upper-end of a bin, plus there's more more bin for everything bigger
   * than the biggest bin) - separate values by space or comma</td></td><td>0 5 50 500 5000</td></tr>
   * <p/>
   * <tr><td>heldOutPercent</td><td>percent of training examples to hold out for parameter estimation</td></td><td>20</td></tr>
   * <p/>
   * <tr><td>charConvergenceMargin</td><td>stop char n-gram's EM when each param changes by less than this amount</td></td><td>0.0001</td></tr>
   * <tr><td>lengthConvergenceMargin</td><td>stop length n-gram's EM when each param changes by less than this amount</td></td><td>0.0001</td></tr>
   * <p/>
   * <tr><td>numCharWordSteps</td><td>number of steps (i.e. resolution) in which to look for optimal char-word interpolation</td></td><td>10</td></tr>
   * <p/>
   * <tr><td>maxPriorBoost</td><td>largest log-prior multiplier to evaluate on held-out data</td><td>10</td></tr>
   * <p/>
   * <tr><td colspan=3 bgcolor="#9fffff">Model features</td></tr>
   * <tr><td>useCharModel</td><td>whether to include the char n-gram in the model</td><td>true</td></tr>
   * <tr><td>useLengthModel</td><td>whether to include the length n-gram in the model</td><td>true</td></tr>
   * <tr><td>useWordModel</td><td>whether to include the common-word model</td><td>true</td></tr>
   * <tr><td>useLengthNormalization</td><td>whether to normalize word probs based on their length</td><td>true</td></tr>
   * <tr><td>usePriorBoost</td><td>whether to boost the category prior in the model</td><td>true</td></tr>
   * </table>
   */
  public PnpClassifier(Properties props) {
    if (props == null) {
      props = getDefaultProperties();
    }

    DEBUG = Boolean.parseBoolean(props.getProperty("DEBUG"));

    if (DEBUG) {
      props.list(System.err);
    }

    numCategories = Integer.parseInt(props.getProperty("numCategories"));

    cn = Integer.parseInt(props.getProperty("cn"));
    ln = Integer.parseInt(props.getProperty("ln"));

    startSymbol = props.getProperty("startSymbol").charAt(0);
    endSymbol = props.getProperty("endSymbol").charAt(0);

    charBinCutoffs = parseIntList(props.getProperty("charBinCutoffs"));
    lengthBinCutoffs = parseIntList(props.getProperty("lengthBinCutoffs"));

    heldOutPercent = Integer.parseInt(props.getProperty("heldOutPercent"));

    charConvergenceMargin = Double.parseDouble(props.getProperty("charConvergenceMargin"));
    lengthConvergenceMargin = Double.parseDouble(props.getProperty("lengthConvergenceMargin"));

    numCharWordSteps = Integer.parseInt(props.getProperty("numCharWordSteps"));

    maxPriorBoost = Integer.parseInt(props.getProperty("maxPriorBoost"));

    useCharModel = Boolean.parseBoolean(props.getProperty("useCharModel"));
    useLengthModel = Boolean.parseBoolean(props.getProperty("useLengthModel"));
    useWordModel = Boolean.parseBoolean(props.getProperty("useWordModel"));
    useLengthNormalization = Boolean.parseBoolean(props.getProperty("useLengthNormalization"));
    usePriorBoost = Boolean.parseBoolean(props.getProperty("usePriorBoost"));

    // initializes and zeroes all counts
    labelIndex = new HashIndex();
    initCounts();
  }

  /**
   * Constructs a new PnpClassifier with default properties.
   */
  public PnpClassifier() {
    this(null);
  }

  /**
   * Parses the given string of ints delimited by spaces or commas. For example,
   * <tt>parseIntList("0 10, 50")</tt> returns <tt>new int[]{0,10,50}</tt>.
   */
  private static int[] parseIntList(String intList) {
    if (intList.length() == 0) {
      return (new int[0]); // split doesn't handle this case correctly
    }
    String[] values = intList.split("[, ]+");
    int[] ints = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      ints[i] = Integer.parseInt(values[i]);
    }
    return (ints);
  }

  /**
   * Uses the held-out examples to learn interpolation parameters and such,
   * then retrains on the held-out examples so the full training set is
   * eventually used.
   * <p/>
   * NOTE: This method can only be called once per PnpClassifier. If it's
   * called a second time, it will throw an IllegalStateException.
   */
  public void tuneParameters() {
    if (parametersTuned) {
      throw(new IllegalStateException("can't call tuneParameters twice"));
    }

    // prunes all n-gram counts that were not seen at least 2 times
    for (int c = 1; c <= numCategories; c++) {
      pruneCounts(charSequenceCounts[c], 0); // disabled right now
    }

    // collapses duplicate held out examples to reduce unnecessary computation
    consolidateHeldOutExamples();

    // sets various parameters using cross-validation on held-out training data
    if (useLengthModel) {
      learnLengthInterpolationConstants();
    }
    if (useCharModel) {
      learnCharInterpolationConstants();
    }
    if (useLengthModel) {
      computeCharSequenceTotals();
    }
    if (useCharModel && useWordModel) {
      learnCharWordInterpolationConstants();
    }
    if (useLengthNormalization) {
      learnLengthNormalizations();
    }
    if (usePriorBoost) {
      learnPriorBoost();
    }
    parametersTuned = true; // don't hold anything more out and don't tune again

    // trains on held-out examples
    for (int c = 1; c <= numCategories; c++) {
      for (int i = 0; i < heldOutExamples[c].size(); i++) {
        addCounts((String) heldOutExamples[c].get(i), c, false, ((Double) heldOutWeights[c].get(i)).doubleValue());
      }
    }
  }

  /**
   * Returns suitable default properties for general use of PnpClassifier.
   * See the class comment for details on which defaults have been chosen.
   * A shared Properties is returned for all calls of this method, so don't
   * change what you get (just use this as the default in the Properties
   * constructor). See {@link #PnpClassifier(Properties)} for a list of
   * supported properties and their defaults.
   */
  public static Properties getDefaultProperties() {
    if (defaultProperties == null) {
      defaultProperties = new Properties();

      defaultProperties.setProperty("numCategories", "1");

      defaultProperties.setProperty("cn", "6");
      defaultProperties.setProperty("ln", "4");

      defaultProperties.setProperty("startSymbol", " ");
      defaultProperties.setProperty("endSymbol", "^");

      defaultProperties.setProperty("charBinCutoffs", "0 5 50 500 5000");
      defaultProperties.setProperty("lengthBinCutoffs", "0 5 50 500 5000");

      defaultProperties.setProperty("heldOutPercent", "20");

      defaultProperties.setProperty("charConvergenceMargin", "0.0001");
      defaultProperties.setProperty("lengthConvergenceMargin", "0.0001");

      defaultProperties.setProperty("numCharWordSteps", "10");

      defaultProperties.setProperty("maxPriorBoost", "10");

      defaultProperties.setProperty("useCharModel", "true");
      defaultProperties.setProperty("useLengthModel", "true");
      defaultProperties.setProperty("useWordModel", "true");
      defaultProperties.setProperty("useLengthNormalization", "true");
      defaultProperties.setProperty("usePriorBoost", "true");

      defaultProperties.setProperty("DEBUG", "false");
    }
    return (defaultProperties);
  }

  /**
   * Loads properties from the given file and returns them.
   * Returned properties will have defaults from {@link #getDefaultProperties}.
   * If there is an error loading the properties, the returned properties
   * will only contain the defaults (the error is silently caught).
   */
  public static Properties loadProperties(String propertiesFilename) {
    Properties props = new Properties(getDefaultProperties());
    try {
      props.load(new FileInputStream(propertiesFilename));
    } catch (Exception e) {
      e.printStackTrace();
    }

    return (props);
  }

  /**
   * Initializes and zeroes all variables and counts before training.
   */
  private void initCounts() {
    numExamples = 0;
    categoryCounts = new int[numCategories + 1];
    lengthSequenceCounts = new ClassicCounter[numCategories + 1];
    lengthInterpolationConstants = new double[numCategories + 1][ln][getLengthBinCount()];
    wordTotalCounts = new int[numCategories + 1];
    charSequenceCounts = new ClassicCounter[numCategories + 1];
    charInterpolationConstants = new double[numCategories + 1][cn][getCharBinCount()];
    charSequenceTotalsByLength = new ClassicCounter[numCategories + 1];
    charTotalCounts = new int[numCategories + 1];
    cachedCharSequenceInterpolatedProbs = new ClassicCounter[numCategories + 1];
    wordCounts = new ClassicCounter[numCategories + 1];
    wordTotalsByLength = new ClassicCounter[numCategories + 1];
    charWordInterpolationConstants = new ClassicCounter[numCategories + 1];
    lengthNormalizations = new double[numCategories + 1];
    heldOutExamples = new List[numCategories + 1];
    heldOutWeights = new List[numCategories + 1];

    for (int c = 1; c <= numCategories; c++) {
      categoryCounts[c] = 0;
      lengthSequenceCounts[c] = new ClassicCounter();
      for (int i = 0; i < ln; i++) {
        for (int b = 0; b < getLengthBinCount(); b++) {
          lengthInterpolationConstants[c][i][b] = 0.5;
        }
      }
      wordTotalCounts[c] = 0;
      charSequenceCounts[c] = new ClassicCounter();
      for (int i = 0; i < cn; i++) {
        for (int b = 0; b < getCharBinCount(); b++) {
          charInterpolationConstants[c][i][b] = 0.5;
        }
      }
      charSequenceTotalsByLength[c] = new ClassicCounter();
      charTotalCounts[c] = 0;
      cachedCharSequenceInterpolatedProbs[c] = new ClassicCounter();
      wordCounts[c] = new ClassicCounter();
      wordTotalsByLength[c] = new ClassicCounter();
      charWordInterpolationConstants[c] = new ClassicCounter();
      lengthNormalizations[c] = 0;
      heldOutExamples[c] = new ArrayList();
      heldOutWeights[c] = new ArrayList();
    }

    priorBoost = 1;
    parametersTuned = false;
  }

  /**
   * Calls <tt>addCounts(line,1)</tt>. Convinience for single-class use.
   */
  public void addCounts(String line) {
    addCounts(line, 1);
  }

  /**
   * Calls <tt>addCounts(line,1,padBeginning)</tt>. Convinience for single-class use.
   */
  public void addCounts(String line, boolean padBeginning) {
    addCounts(line, 1, padBeginning);
  }

  /**
   * Calls <tt>addCounts(line,1,padBeginning,weight)</tt>. Convinience for single-class use.
   */
  public void addCounts(String line, boolean padBeginning, double weight) {
    addCounts(line, 1, padBeginning, weight);
  }

  /**
   * Calls <tt>addCounts(line,category,true)</tt>.
   */
  public void addCounts(String line, int category) {
    addCounts(line, category, true);
  }

  /**
   * Calls <tt>addCounts(line,category,padBeginning,1.0)</tt>.
   * Convinience for normal behavior of adding full counts.
   */
  public void addCounts(String line, int category, boolean padBeginning) {
    addCounts(line, category, padBeginning, 1.0);
  }

  /**
   * Counts relevant statistics for the given example in its given category.
   * If {@link #tuneParameters} has not yet been called, occasionally adds the
   * given line to the held out examples (proportional to heldOutFraction) so it
   * can be used later for tuning parameters. Categories are numbered 1-n (don't
   * use category 0). If <tt>padBeginning</tt> is true, <tt>line</tt> is prepended
   * with <tt>(cn-1)</tt> startSymbols so that n-grams can be counted starting
   * at the beginning. Otherwise the first n-gram is the first cn chars of line.
   * All counts use the given weight (normally 1.0, but can be less for partial
   * examples).
   */
  public void addCounts(String line, int category, boolean padBeginning, double weight) {
    //System.err.println("addCounts("+line+","+category+","+padBeginning+")");
    // adds dummy start and end symbols for n-gram counting
    if (padBeginning) {
      line = getEndMarkedString(line);
    } else if (!line.endsWith("" + endSymbol)) {
      line += endSymbol;
    }
    //System.err.println(" -> using: "+line);
    // holds the example heldOutPercent of the time
    if (!parametersTuned) {
      if (++numExamples % (Math.round(100.0 / heldOutPercent)) == 0) {
        //System.err.println("Holding out: "+line+" ("+weight+")");
        heldOutExamples[category].add(line);
        heldOutWeights[category].add(new Double(weight));
        return;
      }
      //else System.err.println("Using: "+line);
    }

    // category prior
    categoryCounts[category]++;

    // counts each word in the line (ignoring context)
    if (useWordModel) {
      List<String> words = getWordsWithContext(line);
      for (int i = 0; i < words.size(); i++) {
        String wordWithContext = words.get(i);
        String pureWord = getPureString(wordWithContext);
        int wordLength = pureWord.length();
        wordCounts[category].incrementCount(pureWord, weight);
        wordTotalsByLength[category].incrementCount(Integer.valueOf(wordLength), weight);
      }
    }

    // counts all length sequences up to length ln
    if (useLengthModel) {
      String wordLengths = getWordLengthsString(line); // packed representation of word lengths
      for (int i = ln - 1; i <= wordLengths.length(); i++) {
        wordTotalCounts[category]++;
        int max = Math.min(ln, i); // don't run off the left side early on
        for (int j = 1; j <= max; j++) {
          lengthSequenceCounts[category].incrementCount(wordLengths.substring(i - j, i), weight);
        }
      }
    }

    // pulls out all n-grams and counts them
    if (useCharModel) {
      for (int i = cn - 1; i <= line.length(); i++) {
        charTotalCounts[category]++;
        int max = Math.min(cn, i); // don't run off the left side early on
        for (int j = 1; j <= max; j++) {
          //System.err.println('['+line.substring(i-j,i)+']');
          charSequenceCounts[category].incrementCount(line.substring(i - j, i), weight);
        }
      }
    }
  }

  /**
   * Removes all entries in the given map with counts at or less than the given cutoff.
   * Note, need to do something better here, because this messes up the probability distributions.
   * Specifically, if you prune the n-gram abcd, you need to remove counts from abc and so on.
   */
  private void pruneCounts(ClassicCounter counter, double cutoff) {
    counter.removeAll(Counters.keysBelow(counter, cutoff));
  }

  /**
   * Aggregates the weight for duplicate held out examples. This way each unique
   * held out example only needs to be handled once and the net effect is the
   * same but more efficient.
   */
  private void consolidateHeldOutExamples() {
    for (int c = 1; c <= numCategories; c++) {
      Map<Object, Integer> examplesByIndex = new HashMap<Object, Integer>(); // examples seen so far -> index (Integer)
      int i = 0; // index
      for (Iterator iter = heldOutExamples[c].iterator(); iter.hasNext();) {
        Object example = iter.next();
        Integer index = examplesByIndex.get(example);
        if (index == null) {
          examplesByIndex.put(example, Integer.valueOf(i++));
        } else {
          // collapse weight into original index
          Double oldWeight = (Double) heldOutWeights[c].get(index.intValue());
          Double curWeight = (Double) heldOutWeights[c].get(i);
          Double combinedWeight = new Double(oldWeight.doubleValue() + curWeight.doubleValue());
          heldOutWeights[c].set(index.intValue(), combinedWeight);
          heldOutWeights[c].remove(i);
          iter.remove(); // remove this example

        }
      }
    }
  }

  /**
   * Learns good weights for deleted interpolation in the length n-gram model via EM.
   * Learns separate weights based on the counts of the conditioning contexts.
   * Starts by mixing a 1-gram and 0-gram, then mixes the 2-gram with the 1/0-mixture,
   * and so on all the way up to the full n-gram.
   */
  private void learnLengthInterpolationConstants() {
    double eE, eI; // overall expectations for the empirical and interpolated (i.e. mix of lower) models
    double tE, tI; // transition probs for each model (evaluated on each held-out n-gram)

    for (int c = 1; c <= numCategories; c++) {
      ClassicCounter<String> oldCachedInterpolatedProbs = null; // interpolated probs from previous model
      for (int n = 0; n < ln; n++) {
        // pulls out all held-out ngrams for EM
        List[] ngrams = new List[getLengthBinCount()]; // separate held-out lists for each bin
        ClassicCounter<String> cachedEmpiricalProbs = new ClassicCounter<String>(); // empirical probs of n-grams
        ClassicCounter<String> cachedInterpolatedProbs = new ClassicCounter<String>(); // interpolated probs of n-1-grams

        for (int i = 0; i < ngrams.length; i++) {
          ngrams[i] = new ArrayList();
        }
        for (int i = 0; i < heldOutExamples[c].size(); i++) {
          String line = (String) heldOutExamples[c].get(i);
          String wordLengths = getWordLengthsString(line);
          for (int j = ln; j <= wordLengths.length(); j++) {
            // adds the n-gram to the appropriate bin, looking at the appropriate history length
            String ngram = wordLengths.substring(j - n - 1, j);
            ngrams[getLengthBin(ngram, c)].add(ngram);
            cachedEmpiricalProbs.setCount(ngram, getEmpiricalLengthProb(ngram, c));
          }
        }
        if (DEBUG) {
          System.err.print("length bin counts for mixing " + (n + 1) + "-gram and " + n + "-gram in category " + c + ": ");
          for (int i = 0; i < ngrams.length; i++) {
            System.err.print((i > 0 ? "," : "") + ngrams[i].size());
          }
          System.err.println();
        }

        // runs EM separately for each bin
        for (int b = 0; b < getLengthBinCount(); b++) {
          int numIterations = 0; // number of EM iterations (for internal purposes only)
          while (true) {
            // E-step
            eE = 0.001; // avoids problems when expectations are 0
            eI = 0.001; // ensures weights for bins with no examples stay at 0.5
            for (int i = 0; i < ngrams[b].size(); i++) {
              // computes expectations for each lambda
              String ngram = (String) ngrams[b].get(i);
              tE = lengthInterpolationConstants[c][n][b] * cachedEmpiricalProbs.getCount(ngram);
              double interpolatedProb;
              if (oldCachedInterpolatedProbs == null) {
                interpolatedProb = length0GramProb; // 0-gram (never cached)
              } else {
                interpolatedProb = oldCachedInterpolatedProbs.getCount(ngram.substring(1));
              }
              tI = (1 - lengthInterpolationConstants[c][n][b]) * interpolatedProb;

              // normalizes the transitions probs and adds them to the expectations
              double totalProb = tE + tI;
              cachedInterpolatedProbs.setCount(ngram, totalProb); // cache for next n
              eE += tE / totalProb;
              eI += tI / totalProb;
            }

            // M-step

            // tests for convergence: breaks or updates parameters
            double weight = eE / (eE + eI);
            if (Math.abs(lengthInterpolationConstants[c][n][b] - weight) < lengthConvergenceMargin) {
              if (DEBUG) {
                System.err.println("weight for mixing length " + (n + 1) + "-gram and " + n + "-gram using bin " + b + " in category " + c + " converged after " + numIterations + " iterations: " + weight);
              }
              break;
            } else {
              lengthInterpolationConstants[c][n][b] = weight;
            }
            numIterations++;
          }
        }
        oldCachedInterpolatedProbs = cachedInterpolatedProbs;
      }
    }
  }

  /**
   * Learns good weights for deleted interpolation in the char n-gram model via EM.
   * Learns separate weights based on the counts of the conditioning contexts.
   * Starts by mixing a 1-gram and 0-gram, then mixes the 2-gram with the 1/0-mixture,
   * and so on all the way up to the full n-gram.
   */
  private void learnCharInterpolationConstants() {
    double eE, eI; // overall expectations for the empirical and interpolated (i.e. mix of lower) models
    double tE, tI; // transition probs for each model (evaluated on each held-out n-gram)
    for (int c = 1; c <= numCategories; c++) {
      ClassicCounter<String> oldCachedInterpolatedProbs = null; // interpolated probs from previous model
      for (int n = 0; n < cn; n++) {
        // pulls out all held-out ngrams for EM
        List[] ngrams = new List[getCharBinCount()]; // separate held-out lists for each bin
        ClassicCounter<String> cachedEmpiricalProbs = new ClassicCounter<String>(); // empirical probs of n-grams
        ClassicCounter<String> cachedInterpolatedProbs = new ClassicCounter<String>(); // interpolated probs of n-1-grams
        for (int i = 0; i < ngrams.length; i++) {
          ngrams[i] = new ArrayList();
        }
        for (int i = 0; i < heldOutExamples[c].size(); i++) {
          String line = (String) heldOutExamples[c].get(i);
          for (int j = cn; j <= line.length(); j++) {
            // adds the n-gram to the appropriate bin, looking at the appropriate history length
            String ngram = line.substring(j - n - 1, j);
            ngrams[getCharBin(ngram, c)].add(ngram);
            cachedEmpiricalProbs.setCount(ngram, getEmpiricalCharProb(ngram, c));
          }
        }
        if (DEBUG) {
          System.err.print("char bin counts for mixing " + (n + 1) + "-gram and " + n + "-gram in category " + c + ": ");
          for (int i = 0; i < ngrams.length; i++) {
            System.err.print((i > 0 ? "," : "") + ngrams[i].size());
          }
          System.err.println();
        }

        // runs EM separately for each bin
        for (int b = 0; b < getCharBinCount(); b++) {
          int numIterations = 0; // number of EM iterations (for internal purposes only)
          while (true) {
            // E-step
            eE = 0.001; // avoids problems when expectations are 0
            eI = 0.001; // ensures weights for bins with no examples stay at 0.5
            for (int i = 0; i < ngrams[b].size(); i++) {
              // computes expectations for each lambda
              String ngram = (String) ngrams[b].get(i);
              tE = charInterpolationConstants[c][n][b] * cachedEmpiricalProbs.getCount(ngram);
              double interpolatedProb;
              if (oldCachedInterpolatedProbs == null) {
                interpolatedProb = length0GramProb; // 0-gram (never cached)
              } else {
                interpolatedProb = oldCachedInterpolatedProbs.getCount(ngram.substring(1));
              }
              tI = (1 - charInterpolationConstants[c][n][b]) * interpolatedProb;

              // normalizes the transitions probs and adds them to the expectations
              double totalProb = tE + tI;
              cachedInterpolatedProbs.setCount(ngram, totalProb); // cache for next n
              eE += tE / totalProb;
              eI += tI / totalProb;
            }

            // M-step

            // tests for convergence: breaks or updates parameters
            double weight = eE / (eE + eI);
            if (Math.abs(charInterpolationConstants[c][n][b] - weight) < charConvergenceMargin) {
              if (DEBUG) {
                System.err.println("weight for mixing " + (n + 1) + "-gram and " + n + "-gram using bin " + b + " in category " + c + " converged after " + numIterations + " iterations: " + weight);
              }
              break;
            } else {
              charInterpolationConstants[c][n][b] = weight;
            }
            numIterations++;
          }
        }
        oldCachedInterpolatedProbs = cachedInterpolatedProbs;
      }
    }
  }

  /**
   * Computes the best interpolation weights for the char n-gram vs word model with a line search.
   */
  private void learnCharWordInterpolationConstants() {
    // sets interpolation constants separately for each category
    for (int c = 1; c <= numCategories; c++) {
      if (DEBUG) {
        System.err.println(new Date() + " learning char-word weights for category " + c);
      }

      // pulls out each word with context from the held out data and sorts the words by length
      HashMap<Integer, List> wordsWithContextByLength = new HashMap<Integer, List>(); // Integer (word length) -> List of Strings (words with context)
      for (int i = 0; i < heldOutExamples[c].size(); i++) {
        String line = (String) heldOutExamples[c].get(i);
        List<String> wordsWithContext = getWordsWithContext(line);
        for (int j = 0; j < wordsWithContext.size(); j++) {
          // pulls out the word with context
          String wordWithContext = wordsWithContext.get(j);
          int wordLength = wordWithContext.length() - cn;

          // sticks the word in the hashmap by its length
          List<String> words = wordsWithContextByLength.get(Integer.valueOf(wordLength));
          if (words == null) {
            words = new ArrayList<String>();
            wordsWithContextByLength.put(Integer.valueOf(wordLength), words);
          }
          words.add(wordWithContext);
        }
      }

      // runs through each word length and sets a weight for it
      Iterator<Integer> iter = wordsWithContextByLength.keySet().iterator();
      while (iter.hasNext()) {
        Integer length = iter.next(); // word length
        List wordsWithContext = wordsWithContextByLength.get(length);

        // gets the normalizing factor for the n-gram model for this length
        double totalCharProb = charSequenceTotalsByLength[c].getCount(length);
        if (totalCharProb == 0) {
          totalCharProb = 0.0001; // probably too long
        }

        // n-gram and word scores (used for each weight setting, computed first)
        double[] ngramProbs = new double[wordsWithContext.size()];
        double[] wordProbs = new double[wordsWithContext.size()];

        // pre-computes the ngram and word probs for each word
        for (int i = 0; i < wordsWithContext.size(); i++) {
          String wordWithContext = (String) wordsWithContext.get(i);
          String pureWord = getPureString(wordWithContext);

          // computes the probability of generating this word with an n-gram char model
          ngramProbs[i] = 1.0;
          for (int j = cn; j <= wordWithContext.length(); j++) {
            String ngram = wordWithContext.substring(j - cn, j);
            ngramProbs[i] *= getInterpolatedCharProb(ngram, c);
          }
          ngramProbs[i] /= totalCharProb;

          // computes the probability of seeing this word in each category
          wordProbs[i] = getEmpiricalWordProb(pureWord, c);
        }

        double bestWeight = 0.0; // best interpolation value
        double bestScore = -Double.MAX_VALUE; // score from best value

        // performs a line search through all possible weight values
        for (int w = 1; w <= numCharWordSteps; w++) // starts at 1 to avoid a 0-probability error
        {
          double weight = ((double) w) / numCharWordSteps;
          double totalScore = 0; // total score given this lambda

          // scores each word with this lambda
          for (int i = 0; i < wordsWithContext.size(); i++) {
            double mixedProb = weight * ngramProbs[i] + (1.0 - weight) * wordProbs[i];
            totalScore += Math.log(mixedProb);
          }

          // keeps the best scoring lambda
          if (totalScore > bestScore) {
            bestScore = totalScore;
            bestWeight = weight;
          }
        }
        // officially sets the interpolation constant with the best choice
        charWordInterpolationConstants[c].setCount(length, bestWeight);
      }
      if (DEBUG) {
        System.err.println(charWordInterpolationConstants[c]);
      }
    }
  }

  /**
   * Computes the total probability of generating all words of a given length.
   * Simply looks at all unigram length counts, and normalizes them into a probability distribution.
   */
  private void computeCharSequenceTotals() {
    for (int c = 1; c <= numCategories; c++) {
      if (DEBUG) {
        System.err.println(new Date() + " computing word-length probs for category " + c);
      }

      // pulls out all unigram length sequences (i.e. counts for a given length)
      Iterator iter = lengthSequenceCounts[c].keySet().iterator();
      while (iter.hasNext()) {
        String lengthSequence = (String) iter.next();
        if (lengthSequence.length() == 1) {
          charSequenceTotalsByLength[c].setCount(Integer.valueOf((int) lengthSequence.charAt(0)), lengthSequenceCounts[c].getCount(lengthSequence));
        }
      }
      Counters.normalize(charSequenceTotalsByLength[c]);
      if (DEBUG) {
        System.err.println(charSequenceTotalsByLength[c]);
      }
    }
  }

  /**
   * Learns a constant for each category to normalize word probabilities by length.
   * Since word probabilities are calculated with an n-gram, longer words get unfair influence
   * over short words. Thus we normalize the probabilities by taking the (k/l)'th root,
   * where l is the word length (# chars) and k is a constant learned here for each category.
   */
  private void learnLengthNormalizations() {
    // pre-computes all the score components except length normalization
    String[][] endMarkedHeldOutExamples = new String[numCategories + 1][];
    double[][][] cachedLengthScores = new double[numCategories + 1][][];
    String[][][] cachedWordsWithContext = new String[numCategories + 1][][];
    double[][][][] cachedCharWordScores = new double[numCategories + 1][][][];
    for (int c = 1; c <= numCategories; c++) {
      endMarkedHeldOutExamples[c] = new String[heldOutExamples[c].size()];
      cachedLengthScores[c] = new double[heldOutExamples[c].size()][numCategories + 1];
      cachedWordsWithContext[c] = new String[heldOutExamples[c].size()][];
      cachedCharWordScores[c] = new double[heldOutExamples[c].size()][][];
      for (int i = 0; i < heldOutExamples[c].size(); i++) {
        String line = getEndMarkedString((String) heldOutExamples[c].get(i));
        endMarkedHeldOutExamples[c][i] = line;
        List<String> wordsWithContext = getWordsWithContext(line);
        cachedWordsWithContext[c][i] = wordsWithContext.toArray(new String[0]);
        cachedCharWordScores[c][i] = new double[wordsWithContext.size()][numCategories + 1];
        for (int cat = 1; cat <= numCategories; cat++) {
          cachedLengthScores[c][i][cat] = getLengthScore(line, cat);
          for (int w = 0; w < cachedWordsWithContext[c][i].length; w++) {
            cachedCharWordScores[c][i][w][cat] = getCharWordScore(cachedWordsWithContext[c][i][w], cat);
          }
        }
      }
    }

    // pre-computes log priors for each category
    double[] cachedLogPriors = new double[numCategories + 1];
    for (int c = 1; c <= numCategories; c++) {
      cachedLogPriors[c] = Math.log(getPriorProb(c));
    }

    ClassicCounter<Double> normalizationScores = new ClassicCounter<Double>(); // held out scores for each normalization

    double score;        // current score for current boost
    for (double n = 0.0; n <= 10.0; n += 0.25) {
      if (n > 0 && n < 1) {
        continue; // worthless to check here, just want 0 (off) and 1+
      }

      score = 0; // total weight of correctly classified held-out examples
      for (int c = 1; c <= numCategories; c++) {
        for (int i = 0; i < heldOutExamples[c].size(); i++) {
          double weight = ((Double) heldOutWeights[c].get(i)).doubleValue();
          ClassicCounter<Integer> catScores = new ClassicCounter<Integer>(); // pnp score of this example for each category
          // classifies the current example and sees if it matches the correct category
          for (int cat = 1; cat <= numCategories; cat++) {
            double catScore = cachedLogPriors[cat];
            catScore += cachedLengthScores[c][i][cat];
            for (int w = 0; w < cachedWordsWithContext[c][i].length; w++) {
              String wordWithContext = cachedWordsWithContext[c][i][w];
              double charWordLogProb = cachedCharWordScores[c][i][w][cat];
              int wordLength = getPureString(wordWithContext).length();
              catScore += lengthNormalize(charWordLogProb, wordLength, n);
            }
            catScores.setCount(Integer.valueOf(cat), catScore);
          }
          int bestCategory = Counters.argmax(catScores).intValue();
          if (bestCategory == c) {
            score += weight; // sums weight of correctly classified examples
          }
        }
      }
      normalizationScores.setCount(new Double(n), score);
    }
    double bestNormalization = Counters.argmax(normalizationScores).doubleValue();
    lengthNormalization = bestNormalization; // sets the length normalization to the best value
    if (DEBUG) {
      System.err.println(new Date() + " best length normalization = " + lengthNormalization);
    }
  }

  /**
   * Sets the log-prior multiplier (priorBoost) to the best value on the held-out set.
   * Specifically, classifies the held-out examples using different values,
   * and keeps the one that leads to the best score.
   */
  private void learnPriorBoost() {
    // pre-computes log probs in each category for each held out example
    double[][][] cachedLogProbs = new double[numCategories + 1][][];
    for (int c = 1; c <= numCategories; c++) {
      cachedLogProbs[c] = new double[heldOutExamples[c].size()][numCategories + 1];
      for (int i = 0; i < heldOutExamples[c].size(); i++) {
        for (int cat = 1; cat <= numCategories; cat++) {
          cachedLogProbs[c][i][cat] = getLogProb((String) heldOutExamples[c].get(i), cat);
        }
      }
    }

    // pre-computes log priors for each category
    double[] cachedLogPriors = new double[numCategories + 1];
    for (int c = 1; c <= numCategories; c++) {
      cachedLogPriors[c] = Math.log(getPriorProb(c));
    }

    ClassicCounter<Integer> boostScores = new ClassicCounter<Integer>(); // held out score for each boost value
    double score; // aggregated score for current boost
    for (int b = 0; b <= maxPriorBoost; b++) {
      score = 0; // total weight of correctly classified held-out examples
      for (int c = 1; c <= numCategories; c++) {
        for (int i = 0; i < heldOutExamples[c].size(); i++) {
          double weight = ((Double) heldOutWeights[c].get(i)).doubleValue();
          ClassicCounter<Integer> catScores = new ClassicCounter<Integer>(); // pnp score of this example for each category

          // classifies the current example and sees if it matches the correct category
          for (int cat = 1; cat <= numCategories; cat++) {
            catScores.setCount(Integer.valueOf(cat), cachedLogPriors[cat] * b + cachedLogProbs[c][i][cat]);
          }
          int bestCategory = Counters.argmax(catScores).intValue();
          if (bestCategory == c) {
            score += weight; // sums weight of correctly classified examples
          }
        }
      }
      boostScores.setCount(Integer.valueOf(b), score);
    }
    int bestBoost = Counters.argmax(boostScores).intValue();
    priorBoost = bestBoost; // sets best alpha to be used during testing
    if (DEBUG) {
      System.err.println(new Date() + " best prior boost = " + priorBoost);
    }
  }

  /**
   * Runs the classifier on the held-out examples and returns the number of correctly classified examples.
   * Useful for setting various category-neutral parameters of the model and seeing how they do.
   * For example, used to set log-prior boost.
   */
  private double getHeldOutScore() {
    double score = 0; // total weight of correctly classified held-out examples

    for (int cat = 1; cat <= numCategories; cat++) {
      for (int i = 0; i < heldOutExamples[cat].size(); i++) {
        // gets the current held-out example (should already be end-marked)
        String line = (String) heldOutExamples[cat].get(i);
        double weight = ((Double) heldOutWeights[cat].get(i)).doubleValue();
        if (getBestCategory(line) == cat) {
          score += weight; // counts weight of correct guesses
        }
      }
    }
    return (score);
  }

  /**
   * Returns the category that generates the given line with the highest probability.
   */
  public int getBestCategory(String line) {
    // classifies the current example and sees if it matches the correct category
    ClassicCounter<Integer> categoryScores = new ClassicCounter<Integer>();
    for (int c = 1; c <= numCategories; c++) {
      categoryScores.setCount(Integer.valueOf(c), getScore(line, c));
    }
    Integer bestCategory = Counters.argmax(categoryScores);
    return (bestCategory.intValue());
  }

  /**
   * Returns the score for the given example as scored in the given category.
   * Essentially computes Log[P(line|category)*P(category)]. Higher scores mean
   * the line is more likely to be generated from this category. Categories are
   * numbered 1-n (don't use category 0).
   */
  public double getScore(String line, int category) {
    if (PRINT_RUNNING_SCORES) {
      System.err.println("getScore(" + line + "," + category + ")");
    }
    // starts with the weighted priors for each category
    double score = Math.log(getPriorProb(category));
    if (usePriorBoost) {
      score *= priorBoost;
    }
    if (PRINT_RUNNING_SCORES) {
      System.err.println("prior: " + score);
    }

    return (score + getLogProb(line, category));
  }

  /**
   * Returns <tt>getLogProb(line,1)</tt>. Convinience for single-class use.
   */
  public double getLogProb(String line) {
    return (getLogProb(line, 1));
  }

  /**
   * Computes and returns Log[P(line|category)]. This is the probability of the
   * given category generating the given line. Categories are numbered 1-n (don't
   * use category 0).
   */
  public double getLogProb(String line, int category) {
    double score = 0.0;

    // adds dummy start and end symbols for n-gram counting
    line = getEndMarkedString(line);

    // scores the length n-gram for each category
    score += getLengthScore(line, category);

    if (PRINT_RUNNING_SCORES) {
      System.err.println("(cumulative) char-word scores: ");
    }
    // scores each word via mixture of naive-bayes and char n-gram models
    List<String> wordsWithContext = getWordsWithContext(line); // each word with enough context to do both ngram and word stats
    for (int i = 0; i < wordsWithContext.size(); i++) {
      String wordWithContext = wordsWithContext.get(i);
      double charWordLogProb = getCharWordScore(wordWithContext, category);
      int wordLength = getPureString(wordWithContext).length();
      score += lengthNormalize(charWordLogProb, wordLength, lengthNormalization);
      if (PRINT_RUNNING_SCORES) {
        System.err.println("score after \"" + wordWithContext + "\": " + score);
      }
    }

    if (PRINT_RUNNING_SCORES) {
      System.err.println();
      System.err.println("final score (category=" + category + "): " + score);
      System.err.println("--------------------");
      System.err.println();
    }
    return (score);
  }

  /**
   * Returns the score (log prob) of the given line according to the length
   * n-gram model of the given category. This assumes that line has already
   * been end-marked. If useLengthModel is false, returns 0.
   */
  private double getLengthScore(String line, int category) {
    double score = 0;
    if (useLengthModel) {
      String wordLengths = getWordLengthsString(line); // lengths of all words in this line
      if (PRINT_RUNNING_SCORES) {
        System.err.println("(cumulative) length n-gram scores: ");
      }
      for (int i = ln; i <= wordLengths.length(); i++) {
        String lengthSequence = wordLengths.substring(i - ln, i);
        score += Math.log(getInterpolatedLengthProb(lengthSequence, category));
        if (PRINT_RUNNING_SCORES) {
          System.err.println("\t" + lengthSequence + ": " + score);
        }
      }
    }
    return (score);
  }

  /**
   * Returns the score (log prob) of the given word according
   * to the character n-gram model and word model of the given category.
   * If either or both of the char or word models is not enabled, it is skipped.
   */
  private double getCharWordScore(String wordWithContext, int category) {
    String pureWord = getPureString(wordWithContext);
    int wordLength = pureWord.length();

    if (PRINT_RUNNING_SCORES) {
      System.err.println("\t(cumulative) char n-gram prob:");
    }
    // computes the probability of generating this word with an n-gram char model
    double ngramProb = 1.0;
    if (useCharModel) {
      for (int j = cn; j <= wordWithContext.length(); j++) {
        String ngram = wordWithContext.substring(j - cn, j);
        ngramProb *= getInterpolatedCharProb(ngram, category);
        if (PRINT_RUNNING_SCORES) {
          System.err.println("\t\t[" + ngram.charAt(ngram.length() - 1) + "|" + ngram.substring(0, ngram.length() - 1) + "]: " + ngramProb);
        }
      }

      // normalizes the ngram scores by the word length
      if (useLengthModel) {
        double totalCharProb = charSequenceTotalsByLength[category].getCount(Integer.valueOf(wordLength));
        if (totalCharProb == 0) {
          totalCharProb = 0.0001; // probably long length -> give tail prob.
        }
        ngramProb /= totalCharProb;
        if (PRINT_RUNNING_SCORES) {
          System.err.println("\tnormalizing factor (length=" + wordLength + "): " + totalCharProb);
        }
        if (PRINT_RUNNING_SCORES) {
          System.err.println("\tfinal char n-gram prob: " + ngramProb);
        }
      }
    }

    double mixedProb = ngramProb; // char-word interpolation

    // computes the probability of seeing this word in each category
    if (useWordModel) {
      double wordProb = getEmpiricalWordProb(pureWord, category);
      if (PRINT_RUNNING_SCORES) {
        System.err.println("\tword prob: " + wordProb);
      }

      // mixes the char-ngram and word probabilities
      double weight = charWordInterpolationConstants[category].getCount(Integer.valueOf(wordLength));
      if (weight == 0 && useCharModel) {
        weight = 1.0; // if this word length hasn't been seen before, there can't be any stored words, so give all the weight to the n-gram (unless we're not using a char model)
      }
      mixedProb = weight * mixedProb + (1 - weight) * wordProb;
      mixedProb += .00001; // JS: HACK TO PREVENT -INFINITY - TAKE ME OUT
      if (PRINT_RUNNING_SCORES) {
        System.err.println("\tchar-word mixing weight (length=" + wordLength + "): " + weight);
      }
      if (PRINT_RUNNING_SCORES) {
        System.err.println("\tmixed char-word prob: " + mixedProb);
      }
    }
    return (Math.log(mixedProb));
  }

  /**
   * Normalizes the given mixed char-word log prob using the given length normalization.
   * Does nothing is useLengthNormalization is false.
   */
  private double lengthNormalize(double charWordLogProb, int wordLength, double lengthNormalization) {
    // takes the (k/length)-th root of each word to normalize for length (e.g. so longer words aren't more powerful than shorter ones, k is learned above)
    // if lengthNormalization==0, skips this step (that's how you turn it off)
    if (useLengthNormalization && lengthNormalization != 0) {
      charWordLogProb *= lengthNormalization / wordLength;
    }
    return (charWordLogProb);
  }

  /**
   * Returns a linearly interpolated estimate of the last char in the sequence given the rest of it.
   * This function is called recursively in conjunction with getEmpiricalProb to build up the full equation:<br>
   * <tt>gIP(n) = w_n*gEP(n) + (1-w_n)*gIP(n-1)<br>
   * gIP(0) = 1/256</tt>
   * Calls to this method are cached for efficiency so each unique char sequence should only be calculated once.
   */
  public double getInterpolatedCharProb(String charSequence, int category) {
    //System.err.println("gIP: "+charSequence);
    if (parametersTuned) {
      // checks for previously calculates value (only after parameters have been tuned)
      if (cachedCharSequenceInterpolatedProbs[category].containsKey(charSequence)) {
        double cachedProb = cachedCharSequenceInterpolatedProbs[category].getCount(charSequence);
        //System.err.println(" - cached: "+cachedProb);
        return (cachedProb);

      }
    }

    // base case: uniform "0-gram" estimate (one of 256 possible ascii chars)
    if (charSequence.length() == 0) {
      //System.err.println(" - uniform: "+char0GramProb);
      return (char0GramProb);
    }

    // recursive case: w*gEP(n)+(1-w)*gIP(n-1)
    double weight = charInterpolationConstants[category][charSequence.length() - 1][getCharBin(charSequence, category)];
    //System.err.println(" - weight: "+weight);
    //System.err.println(" - gEp: "+getEmpiricalCharProb(charSequence,category));
    double interpolatedProb = weight * getEmpiricalCharProb(charSequence, category) + (1 - weight) * getInterpolatedCharProb(charSequence.substring(1), category);
    //System.err.println(" -> interpolated: "+interpolatedProb);
    if (parametersTuned) {
      cachedCharSequenceInterpolatedProbs[category].setCount(charSequence, interpolatedProb); // record value
    }
    return (interpolatedProb);
  }

  /**
   * Returns <tt>getInterpolatedProb(charSequence,1)</tt>. Convinience for
   * single-class use.
   */
  public double getInterpolatedCharProb(String charSequence) {
    return (getInterpolatedCharProb(charSequence, 1));
  }

  /**
   * Returns the empirical estimate of the probability of the last char in the
   * sequence given the sequence excluding that char, as observed within the given
   * category. For example, gEP("Inc.",2) returns P(.|I,n,c) as observed in category 2.
   */
  public double getEmpiricalCharProb(String charSequence, int category) {
    // gets the number of times the sequence was observed
    double charSequenceCount = charSequenceCounts[category].getCount(charSequence);
    if (charSequenceCount == 0) {
      return (0); // never saw this sequence before
    }

    // gets the history count (or just the total number of letters seen for a unigram model)
    double historyCount;
    if (charSequence.length() == 1) {
      historyCount = charTotalCounts[category];
    } else {
      // gets the number of times the history (sequence minus the final char) was observed
      String history = charSequence.substring(0, charSequence.length() - 1);
      historyCount = charSequenceCounts[category].getCount(history);
      if (historyCount == 0) {
        if (DEBUG) {
          System.err.println("*** NULL HISTORY COUNT: [" + charSequence + "] #c=" + charSequenceCount);
        }
        return (0); // never saw this history before -- this should never happen, since the 0-count sequence above should return 0 first
        // exception: when you use some external context to the left of the pnp and try to ask about its prob (since it only has history counts)
      }
    }

    // returns the empirical ML estimate
    return (charSequenceCount / historyCount);
  }

  /**
   * Returns <tt>getEmpiricalProb(charSequence,1)</tt>. Convinience for
   * single-class use.
   */
  public double getEmpiricalCharProb(String charSequence) {
    return (getEmpiricalCharProb(charSequence, 1));
  }

  /**
   * Returns a linearly interpolated estimate of the last length in the sequence given the rest of it.
   * This function is called recursively in conjunction with getEmpiricalProb to build up the full equation:
   * gIP(n) = w_n*gEP(n) + (1-w_n)*gIP(n-1)
   * gIP(0) = 1/256
   */
  public double getInterpolatedLengthProb(String lengthSequence, int category) {
    // base case: uniform "0-gram" estimate (one of 256 possible ascii chars)
    if (lengthSequence.length() == 0) {
      return (length0GramProb);
    }

    // recursive case: w*gEP(n)+(1-w)*gIP(n-1)
    double weight = lengthInterpolationConstants[category][lengthSequence.length() - 1][getLengthBin(lengthSequence, category)];
    return (weight * getEmpiricalLengthProb(lengthSequence, category) + (1 - weight) * getInterpolatedLengthProb(lengthSequence.substring(1), category));
  }

  /**
   * Returns the empirical estimate of the probability of the last word length in
   * the sequence given the sequence excluding that length, as observed within
   * the given category. For example, gEP([0,2,5],2) returns P(5|0,2) as observed
   * in category 2.
   */
  public double getEmpiricalLengthProb(String lengthSequence, int category) {
    // gets the number of times the sequence was observed
    double lengthSequenceCount = lengthSequenceCounts[category].getCount(lengthSequence);
    if (lengthSequenceCount == 0) {
      return (0); // never saw this sequence before
    }

    // gets the history count, which is just the total number of words seen for a unigram model
    double historyCount;
    if (lengthSequence.length() == 1) {
      historyCount = wordTotalCounts[category];
    } else {
      // gets the number of times the history (sequence minus the final length) was observed
      String history = lengthSequence.substring(0, lengthSequence.length() - 1);
      historyCount = lengthSequenceCounts[category].getCount(history);
      if (historyCount == 0) {
        return (0); // never saw this history before -- this should never happen, since the 0-count sequence above should return 0 first
      }
    }

    // returns the empirical ML estimate
    return (lengthSequenceCount / historyCount);
  }

  /**
   * Returns the empirical estimate of the probability of the given word given
   * the word's length and the given category. For example, gEP("dog",3,2)
   * returns P(word="dog"|length=3,category=2). If no words of the given length
   * have been seen, returns prob=0.0. This is because the word model is mixed
   * with an n-gram model, so it's important to know when the word model has
   * nothing to contribute.
   */
  public double getEmpiricalWordProb(String word, int category) {
    // gets sum-count of all words with given length
    double lengthCount = wordTotalsByLength[category].getCount(Integer.valueOf(word.length()));
    if (lengthCount == 0) {
      return (0); // never saw this length before
    }

    // gets count of word (implicitly C(w) = C(w,l) - length is fixed)
    double wordCount = wordCounts[category].getCount(word);
    if (wordCount == 0) {
      return (0); // never saw this word before
    }

    // returns C(word,l)/C(l) = P(word|l)
    return (wordCount / lengthCount);
  }

  /**
   * Returns the empirical a piori probability of each category, as observed in
   * the training data (fraction of each category in the whole training data).
   */
  public double getPriorProb(int category) {
    int count = categoryCounts[category];
    return (((double) count) / numExamples);
  }

  /**
   * Returns the number of different categories represented in this classifier.
   */
  public int getNumCategories() {
    return (numCategories);
  }

  /**
   * Returns the given line prepended with enough '<tt>&nbsp;</tt>' symbols to allow n-gram
   * parsing. Also adds a '<tt>^</tt>' to the end so a terminal ngram can be counted
   * For example, if n=4, "<tt>Proper Noun</tt>" would be returned as
   * "<tt>&nbsp;&nbsp;&nbsp;Proper Noun^</tt>". Before applying end-marking, trims
   * whitespace from both ends of line. Normally this method should only be needed
   * internally (all relevant methods end-mark strings on their own).
   */
  public String getEndMarkedString(String line) {
    String prefix = "";
    for (int i = 0; i < cn - 1; i++) {
      prefix += startSymbol;
    }
    return (prefix + line.trim() + endSymbol);
  }

  /**
   * Prunes the first (cn-1) chars from the beginning of the word as well as the final char.
   * Inverse of {@link #getEndMarkedString}.
   */
  public String getPureString(String word) {
    return (word.substring(cn - 1, word.length() - 1));
  }

  /**
   * Encodes the lengths of each word in the given line in a special String.
   * This allows length n-grams to be computed just like character n-grams.
   * Each character in the returned String represents one word-length by its
   * ascii value. Note this assumes no word is more than 255 chars. The word
   * is padded with ln-1 starting 0s and one final 0. In most cases the String
   * will consist of unprintable control chars (under ascii 32) so to print
   * the String for debugging, use {@link #printableWordLengthsString}.
   */
  public String getWordLengthsString(String line) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < ln - 1; i++) {
      sb.append((char) 0);
    }
    for (int start = cn - 1, end = line.indexOf(' ', start); start < line.length(); start = end + 1, end = line.indexOf(' ', start)) {
      if (end == -1) {
        end = line.length() - 1; // finishes at the end when it runs out of spaces
      }
      sb.append((char) (end - start)); // encode word-length as ascii char (assumes no word has >255 chars)
    }
    sb.append((char) 0);

    return (sb.toString());
  }

  /**
   * Returns a printable version of a packed word-lengths String made by
   * {@link #getWordLengthsString}. Each word length is printed as number
   * and all the numbers are hyphenated (e.g. 0-0-0-6-5-3-0).
   */
  public static String printableWordLengthsString(String wordLengthsString) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < wordLengthsString.length(); i++) {
      if (i > 0) {
        sb.append('-');
      }
      sb.append((int) wordLengthsString.charAt(i));
    }
    return (sb.toString());
  }

  /**
   * Takes an end-marked string and returns a List of strings, one for each word
   * in the line. Each word has (cn-1) prefix chars and one suffix char (either a space or '^')
   * for context. Thus each word is sort of "end-marked". For example, the string
   * "<tt>&nbsp;&nbsp;&nbsp;Proper Noun^</tt>" would yield
   * {"<tt>&nbsp;&nbsp;&nbsp;Proper&nbsp;</tt>","<tt>er&nbsp;Noun^</tt>"}.
   */
  public List<String> getWordsWithContext(String line) {
    List<String> wordsWithContext = new ArrayList<String>(); // each word with context in the line
    for (int start = cn - 1, end = line.indexOf(' ', start); start < line.length(); start = end + 1, end = line.indexOf(' ', start)) {
      if (end == -1) {
        end = line.length() - 1; // finishs at the end when it runs out of spaces
      }
      wordsWithContext.add(line.substring(start - cn + 1, end + 1));
    }

    return (wordsWithContext);
  }

  /**
   * Returns the index of the appropriate EM interpolation parameter bin for the given char ngram.
   * Specifically, looks at the count of the conditioning context (i.e. all but the last char)
   * and returns a separate bin index depending on the size of the count, using charBinCutoffs (see top)
   */
  private int getCharBin(String charSequence, int category) {
    // gets the number of times the history (sequence minus the final char) was observed
    String history = charSequence.substring(0, charSequence.length() - 1);
    double historyCount = charSequenceCounts[category].getCount(history);

    // returns the appropriate bin
    for (int i = 0; i < charBinCutoffs.length; i++) {
      if (historyCount <= charBinCutoffs[i]) {
        return (i);
      }
    }
    return (charBinCutoffs.length); // above max bin
  }

  /**
   * Returns the number of bins used for char EM interpolation.
   */
  private int getCharBinCount() {
    return (charBinCutoffs.length + 1);
  }

  /**
   * Returns the index of the appropriate EM interpolation parameter bin for the given length ngram.
   * Specifically, looks at the count of the conditioning context (i.e. all but the last char)
   * and returns a separate bin index depending on the size of the count, using lengthBinCutoffs (see top)
   */
  private int getLengthBin(String lengthSequence, int category) {
    // gets the number of times the history (sequence minus the final char) was observed
    String history = lengthSequence.substring(0, lengthSequence.length() - 1);
    double historyCount = lengthSequenceCounts[category].getCount(history);

    // returns the appropriate bin
    for (int i = 0; i < lengthBinCutoffs.length; i++) {
      if (historyCount <= lengthBinCutoffs[i]) {
        return (i);
      }
    }
    return (lengthBinCutoffs.length); // above max bin
  }

  /**
   * Returns the number of bins used for char EM interpolation.
   */
  private int getLengthBinCount() {
    return (lengthBinCutoffs.length + 1);
  }

  /**
   * Returns a map from char sequences (String) to training counts (Double).
   * Don't modify the map, but you can use it to print out char sequences
   * (e.g. sort by getEmpiricalProb).
   */
  public ClassicCounter getCharSequenceCounts(int category) {
    return (charSequenceCounts[category]);
  }

  /**
   * Returns <tt>getCharSequenceCounts(1)</tt>. Convinience for single-class use.
   */
  public ClassicCounter getCharSequenceCounts() {
    return (getCharSequenceCounts(1));
  }

  /**
   * Randomly generates a word of the given length, starting with the given intial context,
   * and ending with the given final char by sampling from the char n-gram model of the given category.
   * Since it's unfair to force early termination, this method generates words of the given length until
   * one naturally occurs with the final char. word length is not including inital context or final char.
   * Returns the generated word without the inital context, but with the final char.
   */
  public String generateWord(int wordLength, String initialContext, char finalChar, int category) {
    StringBuffer word;
    while (true) {
      word = new StringBuffer(initialContext);
      //double wordProb=1.0;
      for (int i = 0; i < wordLength + 1; i++) {
        double r = rand.nextDouble();
        double totalMass = 0;
        StringBuffer ngram = new StringBuffer(word.substring(word.length() - cn + 1));
        ngram.append('X'); // dummy char that will be repeatedly overwritten by the next char to test
        for (char curChar = 0; curChar < 256; curChar++) {
          ngram.setCharAt(cn - 1, curChar);
          double ngramProb = getInterpolatedCharProb(ngram.toString(), category);
          totalMass += ngramProb;
          if (totalMass >= r) {
            word.append(curChar);
            //wordProb*=ngramProb;
            break;
          }
        }
        // give up early and try again if you already generated a space or end-symbol
        if (i < wordLength && (word.charAt(word.length() - 1) == ' ' || word.charAt(word.length() - 1) == endSymbol)) {
          break;
        }
      }
      if (word.length() < initialContext.length() + wordLength + 1) {
        continue; // stopped early, so try again
      }
      if (word.charAt(word.length() - 1) == finalChar) {
        break; // successfully generated a word
      }
    }
    return (word.substring(initialContext.length()));
  }

  /**
   * Generates a novel example of the given category, starting with (cn-1) start symbols
   * and ending with an end symbol. First generates a word-lengths list, then generates
   * a word for each length.
   * <p/>
   * NOTE: This won't work if you've turned off learning the length model. One could
   * imagine just generating from the n-gram model until you generate an end symbol,
   * but right now it will break mysteriously.
   */
  public String generateLine(int category) {
    // generates a packed word lengths list
    StringBuffer wordLengths = new StringBuffer();
    for (int i = 0; i < ln - 1; i++) {
      wordLengths.append((char) 0);
    }

    //double wordProb=1.0;
    while (true) {
      double r = rand.nextDouble();
      double totalMass = 0;
      wordLengths.append((char) 0); // dummy element that will be repeatedly replaced with the extention Integer
      Iterator iter = charSequenceTotalsByLength[category].keySet().iterator();
      while (iter.hasNext()) {
        Integer curLength = (Integer) iter.next();
        wordLengths.setCharAt(wordLengths.length() - 1, (char) curLength.intValue()); // append current word length
        double ngramProb = getInterpolatedLengthProb(wordLengths.substring(wordLengths.length() - ln), category);
        totalMass += ngramProb;
        if (totalMass >= r) {
          //wordProb*=ngramProb;
          break; // no need to add winning word length, since we're directly manipilating the StringBuffer
        }
      }
      // stops when it's generated the end of the length n-gram (i.e. a 0-length word)
      if ((int) wordLengths.charAt(wordLengths.length() - 1) == 0) {
        break;
      }
    }

    // fills in words for each word length
    StringBuffer line = new StringBuffer();
    for (int i = 0; i < cn - 1; i++) {
      line.append(startSymbol);
    }
    for (int i = ln - 1; i < wordLengths.length() - 1; i++) {
      line.append(generateWord((int) wordLengths.charAt(i), line.substring(line.length() - cn + 1), (i < wordLengths.length() - 2 ? ' ' : endSymbol), category));
    }
    return (line.toString());
  }

  /**
   * Runs the classifier on each line in the given test file and prints out the
   * category with the highest score.
   */
  protected void test(String testFilename) throws FileNotFoundException, IOException {
    //PRINT_RUNNING_SCORES=true; // Uncomment this line to get a detailed printout of the scoring for test items
    BufferedReader br = new BufferedReader(new FileReader(testFilename));
    String line;
    while ((line = br.readLine()) != null) {
      int bestSense = 0;
      double bestScore = -Double.MAX_VALUE; // worst possible score

      // finds best score and prints it out
      double[] scores = new double[numCategories + 1];

      for (int c = 1; c <= numCategories; c++) {
        scores[c] = getScore(line, c);
        if (scores[c] > bestScore) {
          bestSense = c;
          bestScore = scores[c];
        }
      }

      // prints the scores for each category on each line, or just
      // the best guess category if PRINT_SCORES is false.
      if (PRINT_SCORES) {
        double total = 0;
        for (int c = 1; c <= numCategories; c++) {
          total += Math.exp(scores[c]);
        }
        for (int c = 1; c <= numCategories; c++) {
          scores[c] = Math.exp(scores[c]) / total;
        }
        for (int c = 1; c <= numCategories; c++) {
          System.out.print(+scores[c] + ";");
        }
        System.out.print(bestSense + ";");
        System.out.println(line);
      } else {
        System.out.println(bestSense);
      }
    }
  }

  // --- CLASSIFY API METHODS --- //

  /**
   * Returns a Datum for a PnpClassifier based on the given String (pnp) and
   * category. The String becomes the sole feature of the Datum and the
   * category is its label. PnpClassifier maintains an internal Index from
   * labels to category numbers and most functions are implemented using these
   * category numbers for efficiency. To manually map from a label to a
   * category number (e.g. to use a function not associated with the
   * Classifier API), call {@link #getCategory}. Using <tt>null</tt> as a
   * label if the Datum is just being tested on.
   */
  public static Datum<Object, String> makeDatum(String line, Object category) {
    return (new BasicDatum<Object, String>(Collections.singletonList(line), category));
  }

  /**
   * Makes datums for each tagged field in the given corpus.
   * Specifically, goes through each TypedTaggedDocument and pulls out all
   * the (unique) target fields. Each target field becomes a Datum with its
   * (String) targetField as the label. This is useful when evaluating NE
   * classification accuracy on an IE document collection.
   */
  public static BasicDataCollection makeDatums(Corpus taggedDocs) {
    BasicDataCollection datums = new BasicDataCollection();
    for (int i = 0, sz = taggedDocs.size(); i < sz; i++) {
      // for each doc...
      TypedTaggedDocument ttd = (TypedTaggedDocument) taggedDocs.get(i);
      Map<Integer,Set<List<String>>> answerWordSequencesByType = new AnswerChecker(ttd).getAnswerWordSequences(false);
      for (Integer type : answerWordSequencesByType.keySet()) {
        // for each answer (list of Strings)...
        Set<List<String>> answerWordSequences = answerWordSequencesByType.get(type);
        for (List<String> wordSequence : answerWordSequences) {
          // make datum for this answer (flattened string with target field label)
          String pnp = PTBTokenizer.ptb2Text(wordSequence);
          datums.add(makeDatum(ttd.getTargetField(type.intValue()), pnp));
        }
      }
    }
    return (datums);
  }

  /**
   * Pulls the PNP line (sole String feature) from the given Datum.
   * This only works for Datums made with {@link #makeDatum}.
   */
  public static String getLine(Datum example) {
    return ((String) new ArrayList(example.asFeatures()).get(0));
  }

  /**
   * Returns the internal category number used for the label of the given
   * Datum. Returns 0 if this label hasn't ever been added to this
   * PnpClassifier (categories numbers start at 1).
   * Don't try to call other methods with 0 for a category!
   */
  public int getCategory(Datum example) {
    return (labelIndex.indexOf(example.label()) + 1);
  }

  /**
   * Adds counts for the given PNP wrapped as a Datum by <tt>makeDatum</tt>.
   * If this is the first time this category (label) has been seen, it is added
   * to the internal label->category# index. A PnpClassifier is constructed with
   * a pre-defined number of categories, so adding too many categories will
   * cause errors.
   */
  public void addCounts(Datum example) {
    labelIndex.add(example.label());
    addCounts(getLine(example), getCategory(example));
  }

  /**
   * Returns the most likely label for the given example PNP.
   * Create Datums from Strings with {@link #makeDatum} (using <tt>null</tt>
   * for label is fine since it's not used here.
   */
  public Object classOf(Datum example) {
    return (labelIndex.get(getBestCategory(getLine(example)) - 1)); // category nums start at 1
  }

  /**
   * Returns the scores in each category for the given example PNP.
   * Create Datums from Strings with {@link #makeDatum} (using <tt>null</tt>
   * for label is fine since it's not used here.
   *
   * @see #getScore
   */
  public ClassicCounter scoresOf(Datum example) {
    ClassicCounter scores = new ClassicCounter();
    String line = getLine(example);
    for (int i = 0; i < labelIndex.size(); i++) {
      scores.setCount(labelIndex.get(i), getScore(line, i + 1)); // category nums start at 1
    }
    return (scores);
  }

  /**
   * Trains and tests a PnpClassifier on the passed-in files.
   * <p/>
   * Usage: <code>java PnpClassifier trainingFilename testFilename</code>.
   *
   * @see #PnpClassifier(String trainingFilename,Properties props)
   * @see #test(String testFilename)
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Doing internal rudimentary testing....");
      // rudimentary testing
      Properties props=getDefaultProperties();
      props.setProperty("DEBUG","true");
      props.setProperty("cn","2");
      props.setProperty("charBinCutoffs","");
      props.setProperty("useWordModel","false");
      props.setProperty("useLengthModel","false");
      props.setProperty("startSymbol","^");
      props.setProperty("endSymbol","$");
      PnpClassifier pnpc=new PnpClassifier(props);

      for(int i=0;i<20;i++) pnpc.addCounts("abc");
      for(int i=0;i<10;i++) pnpc.addCounts("xyz");
      pnpc.tuneParameters();
      pnpc.PRINT_RUNNING_SCORES=true;

      System.err.println("P[abc] = "+Math.exp(pnpc.getLogProb("abc")));
      System.err.println("P[xyz] = "+Math.exp(pnpc.getLogProb("xyz")));
      System.err.println("P[abz] = "+Math.exp(pnpc.getLogProb("abz")));
      System.err.println("P[abcxyz] = "+Math.exp(pnpc.getLogProb("abcxyz")));

      /* --
      try {
        String[] targetFields=new String[]{"other_name","protein_molecule"};
        String genia="c:\\documents and settings\\joseph smarr\\my documents\\nlp work\\GENIA-3.01\\GENIAcorpus3.01.xml";
        Corpus docs=new Corpus(targetFields);
        docs.addAll(new GeniaDocumentIterator(DocumentReader.getReader(new File(genia)),targetFields));
        System.err.println(makeDatums(docs));
      } catch(Exception e) { e.printStackTrace(); }
      -- */
    } else if(args.length < 2) {
      System.err.println("Usage: java PnpClassifier trainingFilename testFilename [propertiesFilename]");
    } else {
      try {
        Properties props=(args.length==3 ? loadProperties(args[2]):null);
        PnpClassifier lc = new PnpClassifier(args[0], props);
        lc.test(args[1]);
      }
      catch(Exception e) {
        e.printStackTrace();
      }
    }
  } // end main()

  private final static long serialVersionUID = -7885637978140633381L;

} // end class PnpClassifier
