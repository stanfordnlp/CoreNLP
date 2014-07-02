package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.optimization.StochasticCalculateMethods;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.ReflectionLoading;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Flags for sequence classifiers. Documentation for general flags and
 * flags for NER can be found in the Javadoc of
 * {@link edu.stanford.nlp.ie.NERFeatureFactory}. Documentation for the flags
 * for Chinese word segmentation can be found in the Javadoc of
 * {@link edu.stanford.nlp.wordseg.ChineseSegmenterFeatureFactory}.
 * <br>
 *
 * <i>IMPORTANT NOTE IF CHANGING THIS FILE:</i> <b>MAKE SURE</b> TO
 * ONLY ADD NEW VARIABLES AT THE END OF THE LIST OF VARIABLES (and not
 * to change existing variables)! Otherwise you usually break all
 * currently serialized classifiers!!! Search for "ADD VARIABLES ABOVE
 * HERE" below.
 * <br>
 * Some general flags are described here
 * <table border="1">
 * <tr>
 * <td><b>Property Name</b></td>
 * <td><b>Type</b></td>
 * <td><b>Default Value</b></td>
 * <td><b>Description</b></td>
 * </tr>
 * <tr>
 * <td>useQN</td>
 * <td>boolean</td>
 * <td>true</td>
 * <td>Use Quasi-Newton (L-BFGS) optimization to find minimum. NOTE: Need to set this to
 * false if using other minimizers such as SGD.</td>
 * </tr>
 * <tr>
 * <td>QNsize</td>
 * <td>int</td>
 * <td>25</td>
 * <td>Number of previous iterations of Quasi-Newton to store (this increases
 * memory use, but speeds convergence by letting the Quasi-Newton optimization
 * more effectively approximate the second derivative).</td>
 * </tr>
 * <tr>
 * <td>QNsize2</td>
 * <td>int</td>
 * <td>25</td>
 * <td>Number of previous iterations of Quasi-Newton to store (used when pruning
 * features, after the first iteration - the first iteration is with QNSize).</td>
 * </tr>
 * <tr>
 * <td>useInPlaceSGD</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>Use SGD (tweaking weights in place) to find minimum (more efficient than
 * the old SGD, faster to converge than Quasi-Newtown if there are very large of
 * samples). Implemented for CRFClassifier. NOTE: Remember to set useQN to false
 * </td>
 * </tr>
 * <tr>
 * <td>tuneSampleSize</td>
 * <td>int</td>
 * <td>-1</td>
 * <td>If this number is greater than 0, specifies the number of samples to use
 * for tuning (default is 1000).</td>
 * </tr>
 * <tr>
 * <td>SGDPasses</td>
 * <td>int</td>
 * <td>-1</td>
 * <td>If this number is greater than 0, specifies the number of SGD passes over
 * entire training set) to do before giving up (default is 50). Can be smaller
 * if sample size is very large.</td>
 * </tr>
 * <tr>
 * <td>useSGD</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>Use SGD to find minimum (can be slow). NOTE: Remember to set useQN to
 * false</td>
 * </tr>
 * <tr>
 * <td>useSGDtoQN</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>Use SGD (SGD version selected by useInPlaceSGD or useSGD) for a certain
 * number of passes (SGDPasses) and then switches to QN. Gives the quick initial
 * convergence of SGD, with the desired convergence criterion of QN (there is
 * some rampup time for QN). NOTE: Remember to set useQN to false</td>
 * </tr>
 * <tr>
 * <td>evaluateIters</td>
 * <td>int</td>
 * <td>0</td>
 * <td>If this number is greater than 0, evaluates on the test set every so
 * often while minimizing. Implemented for CRFClassifier.</td>
 * </tr>
 * <tr>
 * <td>evalCmd</td>
 * <td>String</td>
 * <td></td>
 * <td>If specified (and evaluateIters is set), runs the specified cmdline
 * command during evaluation (instead of default CONLL-like NER evaluation)</td>
 * </tr>
 * <tr>
 * <td>evaluateTrain</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>If specified (and evaluateIters is set), also evaluate on training set
 * (can be expensive)</td>
 * </tr>
 * <tr>
 * <td>tokenizerOptions</td></td>String</td>
 * <td>(null)</td>
 * <td>Extra options to supply to the tokenizer when creating it.</td>
 * </tr>
 * <tr>
 * <td>tokenizerFactory</td></td>String</td>
 * <td>(null)</td>
 * <td>A different tokenizer factory to use if the ReaderAndWriter in question uses tokenizers.</td>
 * </tr>
 * </table>
 *
 * @author Jenny Finkel
 */
public class SeqClassifierFlags implements Serializable {

  private static final long serialVersionUID = -7076671761070232567L;

  public static final String DEFAULT_BACKGROUND_SYMBOL = "O";

  private String stringRep = "";

  public boolean useNGrams = false;
  public boolean conjoinShapeNGrams = false;
  public boolean lowercaseNGrams = false;
  public boolean dehyphenateNGrams = false;
  public boolean usePrev = false;
  public boolean useNext = false;
  public boolean useTags = false;
  public boolean useWordPairs = false;
  public boolean useGazettes = false;
  public boolean useSequences = true;
  public boolean usePrevSequences = false;
  public boolean useNextSequences = false;
  public boolean useLongSequences = false;
  public boolean useBoundarySequences = false;
  public boolean useTaggySequences = false;
  public boolean useExtraTaggySequences = false;
  public boolean dontExtendTaggy = false;
  public boolean useTaggySequencesShapeInteraction = false;
  public boolean strictlyZeroethOrder = false;
  public boolean strictlyFirstOrder = false;
  public boolean strictlySecondOrder = false;
  public boolean strictlyThirdOrder = false;
  public String entitySubclassification = "IO";
  public boolean retainEntitySubclassification = false;
  public boolean useGazettePhrases = false;
  public boolean makeConsistent = false;
  public boolean useViterbi = true;

  public int[] binnedLengths = null;

  public boolean verboseMode = false;

  public boolean useSum = false;
  public double tolerance = 1e-4;
  // Turned on if non-null. Becomes part of the filename features are printed to
  public String printFeatures = null;

  public boolean useSymTags = false;
  /**
   * useSymWordPairs Has a small negative effect.
   */
  public boolean useSymWordPairs = false;

  public String printClassifier = "WeightHistogram";
  public int printClassifierParam = 100;

  public boolean intern = false;
  public boolean intern2 = false;
  public boolean selfTest = false;

  public boolean sloppyGazette = false;
  public boolean cleanGazette = false;

  public boolean noMidNGrams = false;
  public int maxNGramLeng = -1;
  public boolean useReverse = false;

  public boolean greekifyNGrams = false;

  public boolean useParenMatching = false;

  public boolean useLemmas = false;
  public boolean usePrevNextLemmas = false;
  public boolean normalizeTerms = false;
  public boolean normalizeTimex = false;

  public boolean useNB = false;
  public boolean useQN = true;
  public boolean useFloat = false;

  public int QNsize = 25;
  public int QNsize2 = 25;
  public int maxIterations = -1;

  public int wordShape = WordShapeClassifier.NOWORDSHAPE;
  public boolean useShapeStrings = false;
  public boolean useTypeSeqs = false;
  public boolean useTypeSeqs2 = false;
  public boolean useTypeSeqs3 = false;
  public boolean useDisjunctive = false;
  public int disjunctionWidth = 4;
  public boolean useDisjunctiveShapeInteraction = false;
  public boolean useDisjShape = false;

  public boolean useWord = true; // ON by default
  public boolean useClassFeature = false;
  public boolean useShapeConjunctions = false;
  public boolean useWordTag = false;
  public boolean useNPHead = false;
  public boolean useNPGovernor = false;
  public boolean useHeadGov = false;

  public boolean useLastRealWord = false;
  public boolean useNextRealWord = false;
  public boolean useOccurrencePatterns = false;
  public boolean useTypeySequences = false;

  public boolean justify = false;

  public boolean normalize = false;

  public String priorType = "QUADRATIC";
  public double sigma = 1.0;
  public double epsilon = 0.01;

  public int beamSize = 30;

  public int maxLeft = 2;
  public int maxRight = 0;

  public boolean usePosition = false;
  public boolean useBeginSent = false;
  public boolean useGazFeatures = false;
  public boolean useMoreGazFeatures = false;
  public boolean useAbbr = false;
  public boolean useMinimalAbbr = false;
  public boolean useAbbr1 = false;
  public boolean useMinimalAbbr1 = false;
  public boolean useMoreAbbr = false;

  public boolean deleteBlankLines = false;

  public boolean useGENIA = false;
  public boolean useTOK = false;
  public boolean useABSTR = false;
  public boolean useABSTRFreqDict = false;
  public boolean useABSTRFreq = false;
  public boolean useFREQ = false;
  public boolean useABGENE = false;
  public boolean useWEB = false;
  public boolean useWEBFreqDict = false;
  public boolean useIsURL = false;
  public boolean useURLSequences = false;
  public boolean useIsDateRange = false;
  public boolean useEntityTypes = false;
  public boolean useEntityTypeSequences = false;
  public boolean useEntityRule = false;
  public boolean useOrdinal = false;
  public boolean useACR = false;
  public boolean useANTE = false;

  public boolean useMoreTags = false;

  public boolean useChunks = false;
  public boolean useChunkySequences = false;

  public boolean usePrevVB = false;
  public boolean useNextVB = false;
  public boolean useVB = false;
  public boolean subCWGaz = false;

  public String documentReader = "ColumnDocumentReader"; // TODO OBSOLETE:
  // delete when breaking
  // serialization
  // sometime.

  // public String trainMap = "word=0,tag=1,answer=2";
  // public String testMap = "word=0,tag=1,answer=2";
  public String map = "word=0,tag=1,answer=2";

  public boolean useWideDisjunctive = false;
  public int wideDisjunctionWidth = 10;

  // chinese word-segmenter features
  public boolean useRadical = false;
  public boolean useBigramInTwoClique = false;
  public String morphFeatureFile = null;
  public boolean useReverseAffix = false;
  public int charHalfWindow = 3;
  public boolean useWord1 = false;
  public boolean useWord2 = false;
  public boolean useWord3 = false;
  public boolean useWord4 = false;
  public boolean useRad1 = false;
  public boolean useRad2 = false;
  public boolean useWordn = false;
  public boolean useCTBPre1 = false;
  public boolean useCTBSuf1 = false;
  public boolean useASBCPre1 = false;
  public boolean useASBCSuf1 = false;
  public boolean usePKPre1 = false;
  public boolean usePKSuf1 = false;
  public boolean useHKPre1 = false;
  public boolean useHKSuf1 = false;
  public boolean useCTBChar2 = false;
  public boolean useASBCChar2 = false;
  public boolean useHKChar2 = false;
  public boolean usePKChar2 = false;
  public boolean useRule2 = false;
  public boolean useDict2 = false;
  public boolean useOutDict2 = false;
  public String outDict2 = "/u/htseng/scr/chunking/segmentation/out.lexicon";
  public boolean useDictleng = false;
  public boolean useDictCTB2 = false;
  public boolean useDictASBC2 = false;
  public boolean useDictPK2 = false;
  public boolean useDictHK2 = false;
  public boolean useBig5 = false;
  public boolean useNegDict2 = false;
  public boolean useNegDict3 = false;
  public boolean useNegDict4 = false;
  public boolean useNegCTBDict2 = false;
  public boolean useNegCTBDict3 = false;
  public boolean useNegCTBDict4 = false;
  public boolean useNegASBCDict2 = false;
  public boolean useNegASBCDict3 = false;
  public boolean useNegASBCDict4 = false;
  public boolean useNegHKDict2 = false;
  public boolean useNegHKDict3 = false;
  public boolean useNegHKDict4 = false;
  public boolean useNegPKDict2 = false;
  public boolean useNegPKDict3 = false;
  public boolean useNegPKDict4 = false;
  public boolean usePre = false;
  public boolean useSuf = false;
  public boolean useRule = false;
  public boolean useHk = false;
  public boolean useMsr = false;
  public boolean useMSRChar2 = false;
  public boolean usePk = false;
  public boolean useAs = false;
  public boolean useFilter = false; // TODO this flag is used for nothing;
  // delete when breaking serialization
  public boolean largeChSegFile = false; // TODO this flag is used for nothing;
  // delete when breaking serialization
  public boolean useRad2b = false;

  /**
   * Keep the whitespace between English words in testFile when printing out
   * answers. Doesn't really change the content of the CoreLabels. (For Chinese
   * segmentation.)
   */
  public boolean keepEnglishWhitespaces = false;

  /**
   * Keep all the whitespace words in testFile when printing out answers.
   * Doesn't really change the content of the CoreLabels. (For Chinese
   * segmentation.)
   */
  public boolean keepAllWhitespaces = false;

  public boolean sighanPostProcessing = false;

  /**
   * use POS information (an "open" feature for Chinese segmentation)
   */
  public boolean useChPos = false;

  // CTBSegDocumentReader normalization table
  // A value of null means that a default algorithmic normalization
  // is done in which ASCII characters get mapped to their fullwidth
  // equivalents in the Unihan range
  public String normalizationTable; // = null;
  public String dictionary; // = null;
  public String serializedDictionary; // = null;
  public String dictionary2; // = null;
  public String normTableEncoding = "GB18030";

  /**
   * for Sighan bakeoff 2005, the path to the dictionary of bigrams appeared in
   * corpus
   */
  public String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";

  // end Sighan 20005 chinese word-segmenter features/properties

  public boolean useWordShapeGaz = false;
  public String wordShapeGaz = null;

  // TODO: This should be removed in favor of suppressing splitting when
  // maxDocSize <= 0, when next breaking serialization
  // this now controls nothing
  public boolean splitDocuments = true;

  public boolean printXML = false;

  public boolean useSeenFeaturesOnly = false;

  public String lastNameList = "/u/nlp/data/dist.all.last";
  public String maleNameList = "/u/nlp/data/dist.male.first";
  public String femaleNameList = "/u/nlp/data/dist.female.first";

  // don't want these serialized
  public transient String trainFile = null;
  /** NER adaptation (Gaussian prior) parameters. */
  public transient String adaptFile = null;
  public transient String devFile = null;
  public transient String testFile = null;
  public transient String textFile = null;
  public transient String textFiles = null;
  public transient boolean readStdin = false;
  public transient String outputFile = null;
  public transient String loadClassifier = null;
  public transient String loadTextClassifier = null;
  public transient String loadJarClassifier = null;
  public transient String loadAuxClassifier = null;
  public transient String serializeTo = null;
  public transient String serializeToText = null;
  public transient int interimOutputFreq = 0;
  public transient String initialWeights = null;
  public transient List<String> gazettes = new ArrayList<String>();
  public transient String selfTrainFile = null;

  public String inputEncoding = "UTF-8"; // used for CTBSegDocumentReader as well

  public boolean bioSubmitOutput = false;
  public int numRuns = 1;
  public String answerFile = null;
  public String altAnswerFile = null;
  public String dropGaz;
  public String printGazFeatures = null;
  public int numStartLayers = 1;
  public boolean dump = false;

  // whether to merge B- and I- tags in an input file and to tag with IO tags
  // (lacking a prefix). E.g., "I-PERS" goes to "PERS"
  public boolean mergeTags;

  public boolean splitOnHead;

  // threshold
  public int featureCountThreshold = 0;
  public double featureWeightThreshold = 0.0;

  // feature factory
  public String featureFactory = "edu.stanford.nlp.ie.NERFeatureFactory";
  public Object[] featureFactoryArgs = new Object[0];

  public String backgroundSymbol = DEFAULT_BACKGROUND_SYMBOL;
  // use
  public boolean useObservedSequencesOnly = false;

  public int maxDocSize = 0;
  public boolean printProbs = false;
  public boolean printFirstOrderProbs = false;

  public boolean saveFeatureIndexToDisk = false;
  public boolean removeBackgroundSingletonFeatures = false;
  public boolean doGibbs = false;
  public int numSamples = 100;
  public boolean useNERPrior = false;
  public boolean useAcqPrior = false;
  /**
   * If true and doGibbs also true, will do generic Gibbs inference without any
   * priors
   */
  public boolean useUniformPrior = false;
  public boolean useMUCFeatures = false;
  public double annealingRate = 0.0;
  public String annealingType = null;
  public String loadProcessedData = null;

  public boolean initViterbi = true;

  public boolean useUnknown = false;

  public boolean checkNameList = false;

  public boolean useSemPrior = false;
  public boolean useFirstWord = false;

  public boolean useNumberFeature = false;

  public int ocrFold = 0;
  public transient boolean ocrTrain = false;

  public String classifierType = "MaxEnt";
  public String svmModelFile = null;

  public String inferenceType = "Viterbi";

  public boolean useLemmaAsWord = false;

  public String type = "cmm";

  public String readerAndWriter = "edu.stanford.nlp.sequences.ColumnDocumentReaderAndWriter";

  public List<String> comboProps = new ArrayList<String>();

  public boolean usePrediction = false;

  public boolean useAltGazFeatures = false;

  public String gazFilesFile = null;

  public boolean usePrediction2 = false;
  public String baseTrainDir = ".";
  public String baseTestDir = ".";
  public String trainFiles = null;
  public String trainFileList = null;
  public String testFiles = null;
  public String trainDirs = null; // cdm 2009: this is currently unsupported,
  // but one user wanted something like this....
  public String testDirs = null;

  public boolean useOnlySeenWeights = false;

  public String predProp = null;

  public CoreLabel pad = new CoreLabel();

  public boolean useObservedFeaturesOnly = false;

  public String distSimLexicon = null;
  public boolean useDistSim = false;

  public int removeTopN = 0;
  public int numTimesRemoveTopN = 1;
  public double randomizedRatio = 1.0;

  public double removeTopNPercent = 0.0;
  public int purgeFeatures = -1;

  public boolean booleanFeatures = false;

  // This flag is only used for the sequences Type 2 CRF, not for ie.crf.CRFClassifier
  public boolean iobWrapper = false;

  public boolean iobTags = false;
  public boolean useSegmentation = false; /*
                                           * binary segmentation feature for
                                           * character-based Chinese NER
                                           */

  public boolean memoryThrift = false;
  public boolean timitDatum = false;

  public String serializeDatasetsDir = null;
  public String loadDatasetsDir = null;
  public String pushDir = null;
  public boolean purgeDatasets = false;
  public boolean keepOBInMemory = true;
  public boolean fakeDataset = false;
  public boolean restrictTransitionsTimit = false;
  public int numDatasetsPerFile = 1;
  public boolean useTitle = false;

  // these are for the old stuff
  public boolean lowerNewgeneThreshold = false;
  public boolean useEitherSideWord = false;
  public boolean useEitherSideDisjunctive = false;
  public boolean twoStage = false;
  public String crfType = "MaxEnt";
  public int featureThreshold = 1;
  public String featThreshFile = null;
  public double featureDiffThresh = 0.0;
  public int numTimesPruneFeatures = 0;
  public double newgeneThreshold = 0.0;
  public boolean doAdaptation = false;
  public boolean useInternal = true;
  public boolean useExternal = true;
  public double selfTrainConfidenceThreshold = 0.9;
  public int selfTrainIterations = 1;
  public int selfTrainWindowSize = 1; // Unigram
  public boolean useHuber = false;
  public boolean useQuartic = false;
  public double adaptSigma = 1.0;
  public int numFolds = 1;
  public int startFold = 1;
  public int endFold = 1;

  public boolean cacheNGrams = false;

  public String outputFormat;

  public boolean useSMD = false;
  public boolean useSGDtoQN = false;
  public boolean useStochasticQN = false;
  public boolean useScaledSGD = false;
  public int scaledSGDMethod = 0;
  public int SGDPasses = -1;
  public int QNPasses = -1;
  public boolean tuneSGD = false;
  public StochasticCalculateMethods stochasticMethod = StochasticCalculateMethods.NoneSpecified;
  public double initialGain = 0.1;
  public int stochasticBatchSize = 15;
  public boolean useSGD = false;
  public double gainSGD = 0.1;
  public boolean useHybrid = false;
  public int hybridCutoffIteration = 0;
  public boolean outputIterationsToFile = false;
  public boolean testObjFunction = false;
  public boolean testVariance = false;
  public int SGD2QNhessSamples = 50;
  public boolean testHessSamples = false;
  public int CRForder = 1;  // TODO remove this when breaking serialization; this is unused; really maxLeft/maxRight control order
  public int CRFwindow = 2;  // TODO remove this when breaking serialization; this is unused; really maxLeft/maxRight control clique size
  public boolean estimateInitial = false;

  public transient String biasedTrainFile = null;
  public transient String confusionMatrix = null;

  public String outputEncoding = null;

  public boolean useKBest = false;
  public String searchGraphPrefix = null;
  public double searchGraphPrune = Double.POSITIVE_INFINITY;
  public int kBest = 1;

  // more chinese segmenter features for GALE 2007
  public boolean useFeaturesC4gram;
  public boolean useFeaturesC5gram;
  public boolean useFeaturesC6gram;
  public boolean useFeaturesCpC4gram;
  public boolean useFeaturesCpC5gram;
  public boolean useFeaturesCpC6gram;
  public boolean useUnicodeType;
  public boolean useUnicodeType4gram;
  public boolean useUnicodeType5gram;
  public boolean use4Clique;
  public boolean useUnicodeBlock;
  public boolean useShapeStrings1;
  public boolean useShapeStrings3;
  public boolean useShapeStrings4;
  public boolean useShapeStrings5;
  public boolean useGoodForNamesCpC;
  public boolean useDictionaryConjunctions;
  public boolean expandMidDot;

  public int printFeaturesUpto; // = 0;

  public boolean useDictionaryConjunctions3;
  public boolean useWordUTypeConjunctions2;
  public boolean useWordUTypeConjunctions3;
  public boolean useWordShapeConjunctions2;
  public boolean useWordShapeConjunctions3;
  public boolean useMidDotShape;
  public boolean augmentedDateChars;
  public boolean suppressMidDotPostprocessing;

  public boolean printNR; // a flag for WordAndTagDocumentReaderAndWriter

  public String classBias = null;

  public boolean printLabelValue; // Old printErrorStuff

  public boolean useRobustQN = false;
  public boolean combo = false;

  public boolean useGenericFeatures = false;

  public boolean verboseForTrueCasing = false;

  public String trainHierarchical = null;
  public String domain = null;
  public boolean baseline = false;
  public String transferSigmas = null;
  public boolean doFE = false;
  public boolean restrictLabels = true;

  // whether to print a line saying each ObjectBank entry (usually a filename)
  public boolean announceObjectBankEntries = false;

  // This is for use with the OWLQNMinimizer. To use it, set useQN=false, and this to a positive number.
  // A smaller number means more features are retained. Depending on the problem, a good value might be
  // between 0.75 (POS tagger) down to 0.01 (Chinese word segmentation)
  public double l1reg = 0.0;

  // truecaser flags:
  public String mixedCaseMapFile = "";
  public String auxTrueCaseModels = "";

  // more flags inspired by Zhang and Johnson 2003
  public boolean use2W = false;
  public boolean useLC = false;
  public boolean useYetMoreCpCShapes = false;

  // added for the NFL domain
  public boolean useIfInteger = false;

  public String exportFeatures = null;
  public boolean useInPlaceSGD = false;
  public boolean useTopics = false;

  // Number of iterations before evaluating weights (0 = don't evaluate)
  public int evaluateIters = 0;
  // Command to use for evaluation
  public String evalCmd = "";
  // Evaluate on training set or not
  public boolean evaluateTrain = false;
  public int tuneSampleSize = -1;

  public boolean usePhraseFeatures = false;
  public boolean usePhraseWords = false;
  public boolean usePhraseWordTags = false;
  public boolean usePhraseWordSpecialTags = false;
  public boolean useCommonWordsFeature = false;
  public boolean useProtoFeatures = false;
  public boolean useWordnetFeatures = false;
  public String tokenFactory = "edu.stanford.nlp.process.CoreLabelTokenFactory";
  public Object[] tokenFactoryArgs = new Object[0];
  public String tokensAnnotationClassName = "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation";

  public transient String tokenizerOptions = null;
  public transient String tokenizerFactory = null;

  public boolean useCorefFeatures = false;
  public String wikiFeatureDbFile = null;
  // for combining 2 CRFs - one trained from noisy data and another trained from
  // non-noisy
  public boolean useNoisyNonNoisyFeature = false;
  // year annotation of the document
  public boolean useYear = false;

  public boolean useSentenceNumber = false;
  // to know source of the label. Currently, used to know which pattern is used
  // to label the token
  public boolean useLabelSource = false;

  /**
   * Whether to (not) lowercase tokens before looking them up in distsim
   * lexicon. By default lowercasing was done, but now it doesn't have to be
   * true :-).
   */
  public boolean casedDistSim = false;

  /**
   * The format of the distsim file. Known values are: alexClark = TSV file.
   * word TAB clusterNumber [optional other content] terryKoo = TSV file.
   * clusterBitString TAB word TAB frequency
   */
  public String distSimFileFormat = "alexClark";

  /**
   * If this number is greater than 0, the distSim class is assume to be a bit
   * string and is truncated at this many characters. Normal distSim features
   * will then use this amount of resolution. Extra, special distsim features
   * may work at a coarser level of resolution. Since the lexicon only stores
   * this length of bit string, there is then no way to have finer-grained
   * clusters.
   */
  public int distSimMaxBits = 8;

  /**
   * If this is set to true, all digit characters get mapped to '9' in a distsim
   * lexicon and for lookup. This is a simple word shaping that can shrink
   * distsim lexicons and improve their performance.
   */
  public boolean numberEquivalenceDistSim = false;

  /**
   * What class to assign to words not found in the dist sim lexicon. You might
   * want to make it a known class, if one is the "default class.
   */
  public String unknownWordDistSimClass = "null";

  /**
   * Use prefixes and suffixes from the previous and next word.
   */
  public boolean useNeighborNGrams = false;

  /**
   * This function maps words in the training or test data to new
   * words.  They are used at the feature extractor level, ie in the
   * FeatureFactory.  For now, only the NERFeatureFactory uses this.
   */
  public Function<String, String> wordFunction = null;

  public static final String DEFAULT_PLAIN_TEXT_READER = "edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter";
  public String plainTextDocumentReaderAndWriter = DEFAULT_PLAIN_TEXT_READER;

  /**
   * Use a bag of all words as a feature.  Perhaps this will find some
   * words that indicate certain types of entities are present.
   */
  public boolean useBagOfWords = false;

  /**
   * When scoring, count the background symbol stats too.  Useful for
   * things where the background symbol is particularly meaningful,
   * such as truecase.
   */
  public boolean evaluateBackground = false;

  /**
   * Number of experts to be used in Logarithmic Opinion Pool (product of experts) training
   * default value is 1
   */
  public int numLopExpert = 1;
  public transient String initialLopScales = null;
  public transient String initialLopWeights = null;
  public boolean includeFullCRFInLOP = false;
  public boolean backpropLopTraining = false;
  public boolean randomLopWeights = false;
  public boolean randomLopFeatureSplit = false;
  public boolean nonLinearCRF = false;
  public boolean secondOrderNonLinear = false;
  public int numHiddenUnits = -1;
  public boolean useOutputLayer = true;
  public boolean useHiddenLayer = true;
  public boolean gradientDebug = false;
  public boolean checkGradient = false;
  public boolean useSigmoid = false;
  public boolean skipOutputRegularization = false;
  public boolean sparseOutputLayer = false;
  public boolean tieOutputLayer = false;
  public boolean blockInitialize = false;
  public boolean softmaxOutputLayer = false;


  /**
   * Bisequence CRF parameters
   */
  public String loadBisequenceClassifierEn = null;
  public String loadBisequenceClassifierCh = null;
  public String bisequenceClassifierPropEn = null;
  public String bisequenceClassifierPropCh = null;
  public String bisequenceTestFileEn = null;
  public String bisequenceTestFileCh = null;
  public String bisequenceTestOutputEn = null;
  public String bisequenceTestOutputCh = null;
  public String bisequenceTestAlignmentFile = null;
  public String bisequenceAlignmentTestOutput = null;
  public int bisequencePriorType = 1;
  public String bisequenceAlignmentPriorPenaltyCh = null;
  public String bisequenceAlignmentPriorPenaltyEn = null;
  public double alignmentPruneThreshold = 0.0;
  public double alignmentDecodeThreshold = 0.5;
  public boolean factorInAlignmentProb = false;
  public boolean useChromaticSampling = false;
  public boolean useSequentialScanSampling = false;
  public int maxAllowedChromaticSize = 8;

  /**
   * Whether or not to keep blank sentences when processing.  Useful
   * for systems such as the segmenter if you want to line up each
   * line exactly, including blank lines.
   */
  public boolean keepEmptySentences = false;
  public boolean useBilingualNERPrior = false;

  public int samplingSpeedUpThreshold = -1;
  public String entityMatrixCh = null;
  public String entityMatrixEn = null;

  public int multiThreadGibbs = 0;
  public boolean matchNERIncentive = false;

  public boolean useEmbedding = false;
  public boolean prependEmbedding = false;
  public String embeddingWords = null;
  public String embeddingVectors = null;
  public boolean transitionEdgeOnly = false;
  // L1-prior used in OWLQN
  public double priorLambda = 0;
  public boolean addCapitalFeatures = false;
  public int arbitraryInputLayerSize = -1;
  public boolean noEdgeFeature = false;
  public boolean terminateOnEvalImprovement = false;
  public int terminateOnEvalImprovementNumOfEpoch = 1;
  public boolean useMemoryEvaluator = true;
  public boolean suppressTestDebug = false;
  public boolean useOWLQN = false;
  public boolean printWeights = false;
  public int totalDataSlice = 10;
  public int numOfSlices = 0;
  public boolean regularizeSoftmaxTieParam = false;
  public double softmaxTieLambda = 0;
  public int totalFeatureSlice = 10;
  public int numOfFeatureSlices = 0;
  public boolean addBiasToEmbedding = false;
  public boolean hardcodeSoftmaxOutputWeights = false;

  public boolean useNERPriorBIO = false;
  public String entityMatrix = null;
  public int multiThreadClassifier = 0;
  public boolean useDualDecomp = false;
  public boolean biAlignmentPriorIsPMI = true;
  public boolean dampDDStepSizeWithAlignmentProb = false;
  public boolean dualDecompAlignment = false;
  public double dualDecompInitialStepSizeAlignment = 0.1;
  public boolean dualDecompNotBIO = false;
  public String berkeleyAlignerLoadPath = null;
  public boolean useBerkeleyAlignerForViterbi = false;
  public boolean useBerkeleyCompetitivePosterior = false;
  public boolean useDenero = true;
  public double alignDDAlpha = 1;
  public boolean factorInBiEdgePotential = false;
  public boolean noNeighborConstraints = false;
  public boolean includeC2EViterbi = true;
  public boolean initWithPosterior = true;
  public int nerSkipFirstK = 0;
  public int nerSlowerTimes = 1;
  public boolean powerAlignProb = false;
  public boolean powerAlignProbAsAddition = false;
  public boolean initWithNERPosterior = false;
  public boolean applyNERPenalty = true;
  public boolean printFactorTable = false;
  public boolean useAdaGradFOBOS = false;
  public double initRate = 0.1;
  public boolean groupByFeatureTemplate = false;
  public boolean groupByOutputClass = false;
  public double priorAlpha = 0;

  public String splitWordRegex = null;
  public boolean groupByInput = false;
  public boolean groupByHiddenUnit = false;

  public String unigramLM = null;
  public String bigramLM = null;
  public int wordSegBeamSize = 1000;
  public String vocabFile = null;
  public String normalizedFile = null;
  public boolean averagePerceptron = true;
  public String loadCRFSegmenterPath = null;
  public String loadPCTSegmenterPath = null;
  public String crfSegmenterProp = null;
  public String pctSegmenterProp = null;
  public String intermediateSegmenterOut = null;
  public String intermediateSegmenterModel = null;

  public int dualDecompMaxItr = 0;
  public double dualDecompInitialStepSize = 0.1;
  public boolean dualDecompDebug = false;
  public boolean useCWSWordFeatures = false;
  public boolean useCWSWordFeaturesAll = false;
  public boolean useCWSWordFeaturesBigram = false;
  public boolean pctSegmenterLenAdjust = false;
  public boolean useTrainLexicon = false;
  public boolean useCWSFeatures = true;
  public boolean appendLC = false;
  public boolean perceptronDebug = false;
  public boolean pctSegmenterScaleByCRF = false;
  public double pctSegmenterScale = 0.0;
  public boolean separateASCIIandRange = true;
  public double dropoutRate = 0.0;
  public double dropoutScale = 1.0;
  public int multiThreadGrad = 1;
  public int maxQNItr = 0;
  public boolean dropoutApprox = false;
  public String unsupDropoutFile = null;
  public double unsupDropoutScale = 1.0;
  public int startEvaluateIters = 0;
  public int multiThreadPerceptron = 1;
  public boolean lazyUpdate = false;
  public int featureCountThresh = 0;
  public transient String serializeWeightsTo = null;
  public boolean geDebug = false;
  public boolean doFeatureDiscovery = false;
  public transient String loadWeightsFrom = null;
  public transient String loadClassIndexFrom = null;
  public transient String serializeClassIndexTo = null;
  public boolean learnCHBasedOnEN = true;
  public boolean learnENBasedOnCH = false;
  public String loadWeightsFromEN = null;
  public String loadWeightsFromCH = null;
  public String serializeToEN = null;
  public String serializeToCH = null;
  public String testFileEN = null;
  public String testFileCH = null;
  public String unsupFileEN = null;
  public String unsupFileCH = null;
  public String unsupAlignFile = null;
  public String supFileEN = null;
  public String supFileCH = null;
  public transient String serializeFeatureIndexTo = null;
  public String loadFeatureIndexFromEN = null;
  public String loadFeatureIndexFromCH = null;
  public double lambdaEN = 1.0;
  public double lambdaCH = 1.0;
  public boolean alternateTraining = false;
  public boolean weightByEntropy = false;
  public boolean useKL = false;
  public boolean useHardGE = false;
  public boolean useCRFforUnsup = false;
  public boolean useGEforSup = false;
  public boolean useKnownLCWords = true;
  // allow for multiple feature factories.
  public String[] featureFactories = null;
  public List<Object[]> featureFactoriesArgs = null;
  public boolean useNoisyLabel = false;
  public String errorMatrix = null;
  public boolean printTrainLabels = false;

  // Inference label dictionary cutoff
  public int labelDictionaryCutoff = -1;

  public boolean useAdaDelta = false;
  public boolean useAdaDiff = false;
  public double adaGradEps = 1e-3;
  public double adaDeltaRho = 0.95;

  public boolean useRandomSeed = false;
  public boolean terminateOnAvgImprovement = false;
  // "ADD VARIABLES ABOVE HERE"

  public transient List<String> phraseGazettes = null;
  public transient Properties props = null;

  public SeqClassifierFlags() {
  }

  /**
   * Create a new SeqClassifierFlags object and initialize it using values in
   * the Properties object. The properties are printed to stderr as it works.
   *
   * @param props The properties object used for initialization
   */
  public SeqClassifierFlags(Properties props) {
    setProperties(props, true);
  }

  /**
   * Initialize this object using values in Properties object. The properties
   * are printed to stderr as it works.
   *
   * @param props
   *          The properties object used for initialization
   */
  public final void setProperties(Properties props) {
    setProperties(props, true);
  }

  /**
   * Initialize using values in Properties file.
   *
   * @param props
   *          The properties object used for initialization
   * @param printProps
   *          Whether to print the properties to stderr as it works.
   */
  public void setProperties(Properties props, boolean printProps) {
    this.props = props;
    StringBuilder sb = new StringBuilder(stringRep);
    for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
      String key = (String) e.nextElement();
      String val = props.getProperty(key);
      if (!(key.length() == 0 && val.length() == 0)) {
        if (printProps) {
          System.err.println(key + '=' + val);
        }
        sb.append(key).append('=').append(val).append('\n');
      }
      if (key.equalsIgnoreCase("macro")) {
        if (Boolean.parseBoolean(val)) {
          useObservedSequencesOnly = true;
          readerAndWriter = "edu.stanford.nlp.sequences.CoNLLDocumentReaderAndWriter";
          // useClassFeature = true;
          // submit
          useLongSequences = true;
          useTaggySequences = true;
          useNGrams = true;
          usePrev = true;
          useNext = true;
          useTags = true;
          useWordPairs = true;
          useSequences = true;
          usePrevSequences = true;
          // noMidNGrams
          noMidNGrams = true;
          // reverse
          useReverse = true;
          // typeseqs3
          useTypeSeqs = true;
          useTypeSeqs2 = true;
          useTypeySequences = true;
          // wordtypes2 && known
          wordShape = WordShapeClassifier.WORDSHAPEDAN2USELC;
          // occurrence
          useOccurrencePatterns = true;
          // realword
          useLastRealWord = true;
          useNextRealWord = true;
          // smooth
          sigma = 3.0;
          // normalize
          normalize = true;
          normalizeTimex = true;
        }
      } else if (key.equalsIgnoreCase("goodCoNLL")) {
        if (Boolean.parseBoolean(val)) {
          // featureFactory = "edu.stanford.nlp.ie.NERFeatureFactory";
          readerAndWriter = "edu.stanford.nlp.sequences.CoNLLDocumentReaderAndWriter";
          useObservedSequencesOnly = true;
          // useClassFeature = true;
          useLongSequences = true;
          useTaggySequences = true;
          useNGrams = true;
          usePrev = true;
          useNext = true;
          useTags = true;
          useWordPairs = true;
          useSequences = true;
          usePrevSequences = true;
          // noMidNGrams
          noMidNGrams = true;
          // should this be set?? maxNGramLeng = 6; No (to get best score).
          // reverse
          useReverse = false;
          // typeseqs3
          useTypeSeqs = true;
          useTypeSeqs2 = true;
          useTypeySequences = true;
          // wordtypes2 && known
          wordShape = WordShapeClassifier.WORDSHAPEDAN2USELC;
          // occurrence
          useOccurrencePatterns = true;
          // realword
          useLastRealWord = true;
          useNextRealWord = true;
          // smooth
          sigma = 50.0; // increased Aug 2006 from 20; helpful with less feats
          // normalize
          normalize = true;
          normalizeTimex = true;
          maxLeft = 2;
          useDisjunctive = true;
          disjunctionWidth = 4; // clearly optimal for CoNLL
          useBoundarySequences = true;
          useLemmas = true; // no-op except for German
          usePrevNextLemmas = true; // no-op except for German
          inputEncoding = "iso-8859-1"; // needed for CoNLL German files
          // opt
          useQN = true;
          QNsize = 15;
        }
      } else if (key.equalsIgnoreCase("conllNoTags")) {
        if (Boolean.parseBoolean(val)) {
          readerAndWriter = "edu.stanford.nlp.sequences.ColumnDocumentReaderAndWriter";
          // trainMap=testMap="word=0,answer=1";
          map = "word=0,answer=1";
          useObservedSequencesOnly = true;
          // useClassFeature = true;
          useLongSequences = true;
          // useTaggySequences = true;
          useNGrams = true;
          usePrev = true;
          useNext = true;
          // useTags = true;
          useWordPairs = true;
          useSequences = true;
          usePrevSequences = true;
          // noMidNGrams
          noMidNGrams = true;
          // reverse
          useReverse = false;
          // typeseqs3
          useTypeSeqs = true;
          useTypeSeqs2 = true;
          useTypeySequences = true;
          // wordtypes2 && known
          wordShape = WordShapeClassifier.WORDSHAPEDAN2USELC;
          // occurrence
          // useOccurrencePatterns = true;
          // realword
          useLastRealWord = true;
          useNextRealWord = true;
          // smooth
          sigma = 20.0;
          adaptSigma = 20.0;
          // normalize
          normalize = true;
          normalizeTimex = true;
          maxLeft = 2;
          useDisjunctive = true;
          disjunctionWidth = 4;
          useBoundarySequences = true;
          // useLemmas = true; // no-op except for German
          // usePrevNextLemmas = true; // no-op except for German
          inputEncoding = "iso-8859-1";
          // opt
          useQN = true;
          QNsize = 15;
        }
      } else if (key.equalsIgnoreCase("notags")) {
        if (Boolean.parseBoolean(val)) {
          // turn off all features that use POS tags
          // this is slightly crude: it also turns off a few things that
          // don't use tags in e.g., useTaggySequences
          useTags = false;
          useSymTags = false;
          useTaggySequences = false;
          useOccurrencePatterns = false;
        }
      } else if (key.equalsIgnoreCase("submit")) {
        if (Boolean.parseBoolean(val)) {
          useLongSequences = true;
          useTaggySequences = true;
          useNGrams = true;
          usePrev = true;
          useNext = true;
          useTags = true;
          useWordPairs = true;
          wordShape = WordShapeClassifier.WORDSHAPEDAN1;
          useSequences = true;
          usePrevSequences = true;
        }
      } else if (key.equalsIgnoreCase("binnedLengths")) {
        if (val != null) {
          String[] binnedLengthStrs = val.split("[, ]+");
          binnedLengths = new int[binnedLengthStrs.length];
          for (int i = 0; i < binnedLengths.length; i++) {
            binnedLengths[i] = Integer.parseInt(binnedLengthStrs[i]);
          }
        }
      } else if (key.equalsIgnoreCase("makeConsistent")) {
        makeConsistent = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("dump")) {
        dump = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNGrams")) {
        useNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNeighborNGrams")) {
        useNeighborNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("wordFunction")) {
        wordFunction = ReflectionLoading.loadByReflection(val);
      } else if (key.equalsIgnoreCase("conjoinShapeNGrams")) {
        conjoinShapeNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("lowercaseNGrams")) {
        lowercaseNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useIsURL")) {
        useIsURL = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useURLSequences")) {
        useURLSequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useEntityTypes")) {
        useEntityTypes = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useEntityRule")) {
        useEntityRule = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useOrdinal")) {
        useOrdinal = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useEntityTypeSequences")) {
        useEntityTypeSequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useIsDateRange")) {
        useIsDateRange = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("dehyphenateNGrams")) {
        dehyphenateNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("lowerNewgeneThreshold")) {
        lowerNewgeneThreshold = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePrev")) {
        usePrev = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNext")) {
        useNext = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTags")) {
        useTags = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordPairs")) {
        useWordPairs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useGazettes")) {
        useGazettes = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("wordShape")) {
        wordShape = WordShapeClassifier.lookupShaper(val);
      } else if (key.equalsIgnoreCase("useShapeStrings")) {
        useShapeStrings = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useGoodForNamesCpC")) {
        useGoodForNamesCpC = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDictionaryConjunctions")) {
        useDictionaryConjunctions = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDictionaryConjunctions3")) {
        useDictionaryConjunctions3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("expandMidDot")) {
        expandMidDot = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSequences")) {
        useSequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePrevSequences")) {
        usePrevSequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNextSequences")) {
        useNextSequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useLongSequences")) {
        useLongSequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useBoundarySequences")) {
        useBoundarySequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTaggySequences")) {
        useTaggySequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useExtraTaggySequences")) {
        useExtraTaggySequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTaggySequencesShapeInteraction")) {
        useTaggySequencesShapeInteraction = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("strictlyZeroethOrder")) {
        strictlyZeroethOrder = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("strictlyFirstOrder")) {
        strictlyFirstOrder = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("strictlySecondOrder")) {
        strictlySecondOrder = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("strictlyThirdOrder")) {
        strictlyThirdOrder = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("dontExtendTaggy")) {
        dontExtendTaggy = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("entitySubclassification")) {
        entitySubclassification = val;
      } else if (key.equalsIgnoreCase("useGazettePhrases")) {
        useGazettePhrases = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("phraseGazettes")) {
        StringTokenizer st = new StringTokenizer(val, " ,;\t");
        if (phraseGazettes == null) {
          phraseGazettes = new ArrayList<String>();
        }
        while (st.hasMoreTokens()) {
          phraseGazettes.add(st.nextToken());
        }
      } else if (key.equalsIgnoreCase("useSum")) {
        useSum = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("verbose")) {
        verboseMode = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("verboseMode")) {
        verboseMode = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("tolerance")) {
        tolerance = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("maxIterations")) {
        maxIterations = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("exportFeatures")) {
        exportFeatures = val;
      } else if (key.equalsIgnoreCase("printFeatures")) {
        printFeatures = val;
      } else if (key.equalsIgnoreCase("printFeaturesUpto")) {
        printFeaturesUpto = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("lastNameList")) {
        lastNameList = val;
      } else if (key.equalsIgnoreCase("maleNameList")) {
        maleNameList = val;
      } else if (key.equalsIgnoreCase("femaleNameList")) {
        femaleNameList = val;
      } else if (key.equalsIgnoreCase("useSymTags")) {
        useSymTags = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSymWordPairs")) {
        useSymWordPairs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("printClassifier")) {
        printClassifier = val;
      } else if (key.equalsIgnoreCase("printClassifierParam")) {
        printClassifierParam = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("intern")) {
        intern = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("mergetags")) {
        mergeTags = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("iobtags")) {
        iobTags = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useViterbi")) {
        useViterbi = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("intern2")) {
        intern2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("selfTest")) {
        selfTest = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("sloppyGazette")) {
        sloppyGazette = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("cleanGazette")) {
        cleanGazette = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("noMidNGrams")) {
        noMidNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useReverse")) {
        useReverse = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("retainEntitySubclassification")) {
        retainEntitySubclassification = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useLemmas")) {
        useLemmas = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePrevNextLemmas")) {
        usePrevNextLemmas = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("normalizeTerms")) {
        normalizeTerms = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("normalizeTimex")) {
        normalizeTimex = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNB")) {
        useNB = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useParenMatching")) {
        useParenMatching = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTypeSeqs")) {
        useTypeSeqs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTypeSeqs2")) {
        useTypeSeqs2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTypeSeqs3")) {
        useTypeSeqs3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDisjunctive")) {
        useDisjunctive = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("disjunctionWidth")) {
        disjunctionWidth = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useDisjunctiveShapeInteraction")) {
        useDisjunctiveShapeInteraction = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWideDisjunctive")) {
        useWideDisjunctive = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("wideDisjunctionWidth")) {
        wideDisjunctionWidth = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useDisjShape")) {
        useDisjShape = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTitle")) {
        useTitle = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("booleanFeatures")) {
        booleanFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useClassFeature")) {
        useClassFeature = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useShapeConjunctions")) {
        useShapeConjunctions = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordTag")) {
        useWordTag = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNPHead")) {
        useNPHead = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNPGovernor")) {
        useNPGovernor = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useHeadGov")) {
        useHeadGov = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useLastRealWord")) {
        useLastRealWord = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNextRealWord")) {
        useNextRealWord = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useOccurrencePatterns")) {
        useOccurrencePatterns = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTypeySequences")) {
        useTypeySequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("justify")) {
        justify = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("normalize")) {
        normalize = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("priorType")) {
        priorType = val;
      } else if (key.equalsIgnoreCase("sigma")) {
        sigma = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("epsilon")) {
        epsilon = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("beamSize")) {
        beamSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("removeTopN")) {
        removeTopN = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("removeTopNPercent")) {
        removeTopNPercent = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("randomizedRatio")) {
        randomizedRatio = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("numTimesRemoveTopN")) {
        numTimesRemoveTopN = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("maxLeft")) {
        maxLeft = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("maxRight")) {
        maxRight = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("maxNGramLeng")) {
        maxNGramLeng = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useGazFeatures")) {
        useGazFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAltGazFeatures")) {
        useAltGazFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMoreGazFeatures")) {
        useMoreGazFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAbbr")) {
        useAbbr = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMinimalAbbr")) {
        useMinimalAbbr = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAbbr1")) {
        useAbbr1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMinimalAbbr1")) {
        useMinimalAbbr1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("documentReader")) {
        System.err.println("You are using an outdated flag: -documentReader " + val);
        System.err.println("Please use -readerAndWriter instead.");
      } else if (key.equalsIgnoreCase("deleteBlankLines")) {
        deleteBlankLines = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("answerFile")) {
        answerFile = val;
      } else if (key.equalsIgnoreCase("altAnswerFile")) {
        altAnswerFile = val;
      } else if (key.equalsIgnoreCase("loadClassifier") ||
                 key.equalsIgnoreCase("model")) {
        loadClassifier = val;
      } else if (key.equalsIgnoreCase("loadTextClassifier")) {
        loadTextClassifier = val;
      } else if (key.equalsIgnoreCase("loadJarClassifier")) {
        loadJarClassifier = val;
      } else if (key.equalsIgnoreCase("loadAuxClassifier")) {
        loadAuxClassifier = val;
      } else if (key.equalsIgnoreCase("serializeTo")) {
        serializeTo = val;
      } else if (key.equalsIgnoreCase("serializeToText")) {
        serializeToText = val;
      } else if (key.equalsIgnoreCase("serializeDatasetsDir")) {
        serializeDatasetsDir = val;
      } else if (key.equalsIgnoreCase("loadDatasetsDir")) {
        loadDatasetsDir = val;
      } else if (key.equalsIgnoreCase("pushDir")) {
        pushDir = val;
      } else if (key.equalsIgnoreCase("purgeDatasets")) {
        purgeDatasets = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("keepOBInMemory")) {
        keepOBInMemory = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("fakeDataset")) {
        fakeDataset = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("numDatasetsPerFile")) {
        numDatasetsPerFile = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("trainFile")) {
        trainFile = val;
      } else if (key.equalsIgnoreCase("biasedTrainFile")) {
        biasedTrainFile = val;
      } else if (key.equalsIgnoreCase("classBias")) {
        classBias = val;
      } else if (key.equalsIgnoreCase("confusionMatrix")) {
        confusionMatrix = val;
      } else if (key.equalsIgnoreCase("adaptFile")) {
        adaptFile = val;
      } else if (key.equalsIgnoreCase("devFile")) {
        devFile = val;
      } else if (key.equalsIgnoreCase("testFile")) {
        testFile = val;
      } else if (key.equalsIgnoreCase("outputFile")) {
        outputFile = val;
      } else if (key.equalsIgnoreCase("textFile")) {
        textFile = val;
      } else if (key.equalsIgnoreCase("readStdin")) {
        readStdin = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("initialWeights")) {
        initialWeights = val;
      } else if (key.equalsIgnoreCase("interimOutputFreq")) {
        interimOutputFreq = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("inputEncoding")) {
        inputEncoding = val;
      } else if (key.equalsIgnoreCase("outputEncoding")) {
        outputEncoding = val;
      } else if (key.equalsIgnoreCase("gazette")) {
        useGazettes = true;
        StringTokenizer st = new StringTokenizer(val, " ,;\t");
        if (gazettes == null) {
          gazettes = new ArrayList<String>();
        } // for after deserialization, as gazettes is transient
        while (st.hasMoreTokens()) {
          gazettes.add(st.nextToken());
        }
      } else if (key.equalsIgnoreCase("useQN")) {
        useQN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("QNsize")) {
        QNsize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("QNsize2")) {
        QNsize2 = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("l1reg")) {
        useQN = false;
        l1reg = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("useFloat")) {
        useFloat = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("trainMap")) {
        System.err.println("trainMap and testMap are no longer valid options - please use map instead.");
        throw new RuntimeException();
      } else if (key.equalsIgnoreCase("testMap")) {
        System.err.println("trainMap and testMap are no longer valid options - please use map instead.");
        throw new RuntimeException();
      } else if (key.equalsIgnoreCase("map")) {
        map = val;
      } else if (key.equalsIgnoreCase("useMoreAbbr")) {
        useMoreAbbr = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePrevVB")) {
        usePrevVB = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNextVB")) {
        useNextVB = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useVB")) {
        if (Boolean.parseBoolean(val)) {
          useVB = true;
          usePrevVB = true;
          useNextVB = true;
        }
      } else if (key.equalsIgnoreCase("useChunks")) {
        useChunks = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useChunkySequences")) {
        useChunkySequences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("greekifyNGrams")) {
        greekifyNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("restrictTransitionsTimit")) {
        restrictTransitionsTimit = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMoreTags")) {
        useMoreTags = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useBeginSent")) {
        useBeginSent = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePosition")) {
        usePosition = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useGenia")) {
        useGENIA = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAbstr")) {
        useABSTR = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWeb")) {
        useWEB = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAnte")) {
        useANTE = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAcr")) {
        useACR = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTok")) {
        useTOK = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAbgene")) {
        useABGENE = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAbstrFreqDict")) {
        useABSTRFreqDict = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAbstrFreq")) {
        useABSTRFreq = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFreq")) {
        useFREQ = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usewebfreqdict")) {
        useWEBFreqDict = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("bioSubmitOutput")) {
        bioSubmitOutput = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("subCWGaz")) {
        subCWGaz = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("splitOnHead")) {
        splitOnHead = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("featureCountThreshold")) {
        featureCountThreshold = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useWord")) {
        useWord = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("memoryThrift")) {
        memoryThrift = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("timitDatum")) {
        timitDatum = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("splitDocuments")) {
        System.err.println("You are using an outdated flag: -splitDocuments");
        System.err.println("Please use -maxDocSize -1 instead.");
        splitDocuments = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("featureWeightThreshold")) {
        featureWeightThreshold = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("backgroundSymbol")) {
        backgroundSymbol = val;
      } else if (key.equalsIgnoreCase("featureFactory")) {
        // handle multiple feature factories.
        String[] tokens = val.split("\\s*,\\s*"); // multiple feature factories could be specified and are comma separated.
        int numFactories = tokens.length;
        if (numFactories==1){ // for compatible reason
          featureFactory = getFeatureFactory(val);
        }

        featureFactories = new String[numFactories];
        featureFactoriesArgs = new ArrayList<Object[]>(numFactories);
        for (int i = 0; i < numFactories; i++) {
          featureFactories[i] = getFeatureFactory(tokens[i]);
          featureFactoriesArgs.add(new Object[0]);
        }
      } else if (key.equalsIgnoreCase("printXML")) {
        printXML = Boolean.parseBoolean(val); // todo: This appears unused now.
        // Was it replaced by
        // outputFormat?

      } else if (key.equalsIgnoreCase("useSeenFeaturesOnly")) {
        useSeenFeaturesOnly = Boolean.parseBoolean(val);

      } else if (key.equalsIgnoreCase("useBagOfWords")) {
        useBagOfWords = Boolean.parseBoolean(val);

        // chinese word-segmenter features
      } else if (key.equalsIgnoreCase("useRadical")) {
        useRadical = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useBigramInTwoClique")) {
        useBigramInTwoClique = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useReverseAffix")) {
        useReverseAffix = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("charHalfWindow")) {
        charHalfWindow = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("purgeFeatures")) {
        purgeFeatures = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("ocrFold")) {
        ocrFold = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("morphFeatureFile")) {
        morphFeatureFile = val;
      } else if (key.equalsIgnoreCase("svmModelFile")) {
        svmModelFile = val;
        /* Dictionary */
      } else if (key.equalsIgnoreCase("useDictleng")) {
        useDictleng = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDict2")) {
        useDict2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useOutDict2")) {
        useOutDict2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("outDict2")) {
        outDict2 = val;
      } else if (key.equalsIgnoreCase("useDictCTB2")) {
        useDictCTB2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDictASBC2")) {
        useDictASBC2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDictPK2")) {
        useDictPK2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDictHK2")) {
        useDictHK2 = Boolean.parseBoolean(val);
        /* N-gram flags */
      } else if (key.equalsIgnoreCase("useWord1")) {
        useWord1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWord2")) {
        useWord2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWord3")) {
        useWord3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWord4")) {
        useWord4 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useRad1")) {
        useRad1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useRad2")) {
        useRad2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useRad2b")) {
        useRad2b = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordn")) {
        useWordn = Boolean.parseBoolean(val);
        /* affix flags */
      } else if (key.equalsIgnoreCase("useCTBPre1")) {
        useCTBPre1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useCTBSuf1")) {
        useCTBSuf1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useASBCPre1")) {
        useASBCPre1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useASBCSuf1")) {
        useASBCSuf1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useHKPre1")) {
        useHKPre1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useHKSuf1")) {
        useHKSuf1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePKPre1")) {
        usePKPre1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePKSuf1")) {
        usePKSuf1 = Boolean.parseBoolean(val);
        /* POS flags */
      } else if (key.equalsIgnoreCase("useCTBChar2")) {
        useCTBChar2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePrediction")) {
        usePrediction = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useASBCChar2")) {
        useASBCChar2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useHKChar2")) {
        useHKChar2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePKChar2")) {
        usePKChar2 = Boolean.parseBoolean(val);
        /* Rule flag */
      } else if (key.equalsIgnoreCase("useRule2")) {
        useRule2 = Boolean.parseBoolean(val);
        /* ASBC and HK */
      } else if (key.equalsIgnoreCase("useBig5")) {
        useBig5 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegDict2")) {
        useNegDict2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegDict3")) {
        useNegDict3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegDict4")) {
        useNegDict4 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegCTBDict2")) {
        useNegCTBDict2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegCTBDict3")) {
        useNegCTBDict3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegCTBDict4")) {
        useNegCTBDict4 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegASBCDict2")) {
        useNegASBCDict2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegASBCDict3")) {
        useNegASBCDict3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegASBCDict4")) {
        useNegASBCDict4 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegPKDict2")) {
        useNegPKDict2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegPKDict3")) {
        useNegPKDict3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegPKDict4")) {
        useNegPKDict4 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegHKDict2")) {
        useNegHKDict2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegHKDict3")) {
        useNegHKDict3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNegHKDict4")) {
        useNegHKDict4 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePre")) {
        usePre = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSuf")) {
        useSuf = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useRule")) {
        useRule = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAs")) {
        useAs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePk")) {
        usePk = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useHk")) {
        useHk = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMsr")) {
        useMsr = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMSRChar2")) {
        useMSRChar2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFeaturesC4gram")) {
        useFeaturesC4gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFeaturesC5gram")) {
        useFeaturesC5gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFeaturesC6gram")) {
        useFeaturesC6gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFeaturesCpC4gram")) {
        useFeaturesCpC4gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFeaturesCpC5gram")) {
        useFeaturesCpC5gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFeaturesCpC6gram")) {
        useFeaturesCpC6gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useUnicodeType")) {
        useUnicodeType = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useUnicodeBlock")) {
        useUnicodeBlock = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useUnicodeType4gram")) {
        useUnicodeType4gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useUnicodeType5gram")) {
        useUnicodeType5gram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useShapeStrings1")) {
        useShapeStrings1 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useShapeStrings3")) {
        useShapeStrings3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useShapeStrings4")) {
        useShapeStrings4 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useShapeStrings5")) {
        useShapeStrings5 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordUTypeConjunctions2")) {
        useWordUTypeConjunctions2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordUTypeConjunctions3")) {
        useWordUTypeConjunctions3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordShapeConjunctions2")) {
        useWordShapeConjunctions2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordShapeConjunctions3")) {
        useWordShapeConjunctions3 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMidDotShape")) {
        useMidDotShape = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("augmentedDateChars")) {
        augmentedDateChars = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("suppressMidDotPostprocessing")) {
        suppressMidDotPostprocessing = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("printNR")) {
        printNR = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("use4Clique")) {
        use4Clique = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFilter")) {
        useFilter = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("largeChSegFile")) {
        largeChSegFile = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("keepEnglishWhitespaces")) {
        keepEnglishWhitespaces = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("keepAllWhitespaces")) {
        keepAllWhitespaces = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("sighanPostProcessing")) {
        sighanPostProcessing = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useChPos")) {
        useChPos = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("sighanCorporaDict")) {
        sighanCorporaDict = val;
        // end chinese word-segmenter features
      } else if (key.equalsIgnoreCase("useObservedSequencesOnly")) {
        useObservedSequencesOnly = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("maxDocSize")) {
        maxDocSize = Integer.parseInt(val);
        splitDocuments = true;
      } else if (key.equalsIgnoreCase("printProbs")) {
        printProbs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("printFirstOrderProbs")) {
        printFirstOrderProbs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("saveFeatureIndexToDisk")) {
        saveFeatureIndexToDisk = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("removeBackgroundSingletonFeatures")) {
        removeBackgroundSingletonFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("doGibbs")) {
        doGibbs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNERPrior")) {
        useNERPrior = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAcqPrior")) {
        useAcqPrior = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSemPrior")) {
        useSemPrior = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useMUCFeatures")) {
        useMUCFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("initViterbi")) {
        initViterbi = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("checkNameList")) {
        checkNameList = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFirstWord")) {
        useFirstWord = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useUnknown")) {
        useUnknown = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("cacheNGrams")) {
        cacheNGrams = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNumberFeature")) {
        useNumberFeature = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("annealingRate")) {
        annealingRate = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("annealingType")) {
        if (val.equalsIgnoreCase("linear") || val.equalsIgnoreCase("exp") || val.equalsIgnoreCase("exponential")) {
          annealingType = val;
        } else {
          System.err.println("unknown annealingType: " + val + ".  Please use linear|exp|exponential");
        }
      } else if (key.equalsIgnoreCase("numSamples")) {
        numSamples = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("inferenceType")) {
        inferenceType = val;
      } else if (key.equalsIgnoreCase("loadProcessedData")) {
        loadProcessedData = val;
      } else if (key.equalsIgnoreCase("normalizationTable")) {
        normalizationTable = val;
      } else if (key.equalsIgnoreCase("dictionary")) {
        // don't set if empty string or spaces or true: revert it to null
        // special case so can empty out dictionary list on command line!
        val = val.trim();
        if (val.length() > 0 && !"true".equals(val) && !"null".equals(val) && !"false".equals("val")) {
          dictionary = val;
        } else {
          dictionary = null;
        }
      } else if (key.equalsIgnoreCase("serDictionary")) {
        // don't set if empty string or spaces or true: revert it to null
        // special case so can empty out dictionary list on command line!
        val = val.trim();
        if (val.length() > 0 && !"true".equals(val) && !"null".equals(val) && !"false".equals("val")) {
          serializedDictionary = val;
        } else {
          serializedDictionary = null;
        }
      } else if (key.equalsIgnoreCase("dictionary2")) {
        // don't set if empty string or spaces or true: revert it to null
        // special case so can empty out dictionary list on command line!
        val = val.trim();
        if (val.length() > 0 && !"true".equals(val) && !"null".equals(val) && !"false".equals("val")) {
          dictionary2 = val;
        } else {
          dictionary2 = null;
        }
      } else if (key.equalsIgnoreCase("normTableEncoding")) {
        normTableEncoding = val;
      } else if (key.equalsIgnoreCase("useLemmaAsWord")) {
        useLemmaAsWord = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("type")) {
        type = val;
      } else if (key.equalsIgnoreCase("readerAndWriter")) {
        readerAndWriter = val;
      } else if (key.equalsIgnoreCase("plainTextDocumentReaderAndWriter")) {
        plainTextDocumentReaderAndWriter = val;
      } else if (key.equalsIgnoreCase("gazFilesFile")) {
        gazFilesFile = val;
      } else if (key.equalsIgnoreCase("baseTrainDir")) {
        baseTrainDir = val;
      } else if (key.equalsIgnoreCase("baseTestDir")) {
        baseTestDir = val;
      } else if (key.equalsIgnoreCase("trainFiles")) {
        trainFiles = val;
      } else if (key.equalsIgnoreCase("trainFileList")) {
        trainFileList = val;
      } else if (key.equalsIgnoreCase("trainDirs")) {
        trainDirs = val;
      } else if (key.equalsIgnoreCase("testDirs")) {
        testDirs = val;
      } else if (key.equalsIgnoreCase("testFiles")) {
        testFiles = val;
      } else if (key.equalsIgnoreCase("textFiles")) {
        textFiles = val;
      } else if (key.equalsIgnoreCase("usePrediction2")) {
        usePrediction2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useObservedFeaturesOnly")) {
        useObservedFeaturesOnly = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("iobWrapper")) {
        iobWrapper = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDistSim")) {
        useDistSim = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("casedDistSim")) {
        casedDistSim = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("distSimFileFormat")) {
        distSimFileFormat = val;
      } else if (key.equalsIgnoreCase("distSimMaxBits")) {
        distSimMaxBits = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("numberEquivalenceDistSim")) {
        numberEquivalenceDistSim = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("unknownWordDistSimClass")) {
        unknownWordDistSimClass = val;
      } else if (key.equalsIgnoreCase("useOnlySeenWeights")) {
        useOnlySeenWeights = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("predProp")) {
        predProp = val;
      } else if (key.equalsIgnoreCase("distSimLexicon")) {
        distSimLexicon = val;
      } else if (key.equalsIgnoreCase("useSegmentation")) {
        useSegmentation = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useInternal")) {
        useInternal = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useExternal")) {
        useExternal = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useEitherSideWord")) {
        useEitherSideWord = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useEitherSideDisjunctive")) {
        useEitherSideDisjunctive = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("featureDiffThresh")) {
        featureDiffThresh = Double.parseDouble(val);
        if (props.getProperty("numTimesPruneFeatures") == null) {
          numTimesPruneFeatures = 1;
        }
      } else if (key.equalsIgnoreCase("numTimesPruneFeatures")) {
        numTimesPruneFeatures = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("newgeneThreshold")) {
        newgeneThreshold = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("adaptFile")) {
        adaptFile = val;
      } else if (key.equalsIgnoreCase("doAdaptation")) {
        doAdaptation = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("selfTrainFile")) {
        selfTrainFile = val;
      } else if (key.equalsIgnoreCase("selfTrainIterations")) {
        selfTrainIterations = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("selfTrainWindowSize")) {
        selfTrainWindowSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("selfTrainConfidenceThreshold")) {
        selfTrainConfidenceThreshold = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("numFolds")) {
        numFolds = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("startFold")) {
        startFold = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("endFold")) {
        endFold = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("adaptSigma")) {
        adaptSigma = Double.parseDouble(val);
      } else if (key.startsWith("prop") && !key.equals("prop")) {
        comboProps.add(val);
      } else if (key.equalsIgnoreCase("outputFormat")) {
        outputFormat = val;
      } else if (key.equalsIgnoreCase("useSMD")) {
        useSMD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useScaledSGD")) {
        useScaledSGD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("scaledSGDMethod")) {
        scaledSGDMethod = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("tuneSGD")) {
        tuneSGD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("StochasticCalculateMethod")) {
        if (val.equalsIgnoreCase("AlgorithmicDifferentiation")) {
          stochasticMethod = StochasticCalculateMethods.AlgorithmicDifferentiation;
        } else if (val.equalsIgnoreCase("IncorporatedFiniteDifference")) {
          stochasticMethod = StochasticCalculateMethods.IncorporatedFiniteDifference;
        } else if (val.equalsIgnoreCase("ExternalFinitedifference")) {
          stochasticMethod = StochasticCalculateMethods.ExternalFiniteDifference;
        }
      } else if (key.equalsIgnoreCase("initialGain")) {
        initialGain = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("stochasticBatchSize")) {
        stochasticBatchSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("SGD2QNhessSamples")) {
        SGD2QNhessSamples = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useSGD")) {
        useSGD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useInPlaceSGD")) {
        useInPlaceSGD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSGDtoQN")) {
        useSGDtoQN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("SGDPasses")) {
        SGDPasses = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("QNPasses")) {
        QNPasses = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("gainSGD")) {
        gainSGD = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("useHybrid")) {
        useHybrid = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("hybridCutoffIteration")) {
        hybridCutoffIteration = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useStochasticQN")) {
        useStochasticQN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("outputIterationsToFile")) {
        outputIterationsToFile = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("testObjFunction")) {
        testObjFunction = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("testVariance")) {
        testVariance = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("CRForder")) {
        CRForder = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("CRFwindow")) {
        CRFwindow = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("testHessSamples")) {
        testHessSamples = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("estimateInitial")) {
        estimateInitial = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("printLabelValue")) {
        printLabelValue = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("searchGraphPrefix")) {
        searchGraphPrefix = val;
      } else if (key.equalsIgnoreCase("searchGraphPrune")) {
        searchGraphPrune = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("kBest")) {
        useKBest = true;
        kBest = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useRobustQN")) {
        useRobustQN = true;
      } else if (key.equalsIgnoreCase("combo")) {
        combo = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("verboseForTrueCasing")) {
        verboseForTrueCasing = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("trainHierarchical")) {
        trainHierarchical = val;
      } else if (key.equalsIgnoreCase("domain")) {
        domain = val;
      } else if (key.equalsIgnoreCase("baseline")) {
        baseline = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("doFE")) {
        doFE = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("restrictLabels")) {
        restrictLabels = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("transferSigmas")) {
        transferSigmas = val;
      } else if (key.equalsIgnoreCase("announceObjectBankEntries")) {
        announceObjectBankEntries = true;
      } else if (key.equalsIgnoreCase("mixedCaseMapFile")) {
        mixedCaseMapFile = val;
      } else if (key.equalsIgnoreCase("auxTrueCaseModels")) {
        auxTrueCaseModels = val;
      } else if (key.equalsIgnoreCase("use2W")) {
        use2W = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useLC")) {
        useLC = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useYetMoreCpCShapes")) {
        useYetMoreCpCShapes = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useIfInteger")) {
        useIfInteger = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("twoStage")) {
        twoStage = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("evaluateIters")) {
        evaluateIters = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("evalCmd")) {
        evalCmd = val;
      } else if (key.equalsIgnoreCase("evaluateTrain")) {
        evaluateTrain = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("evaluateBackground")) {
        evaluateBackground = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("tuneSampleSize")) {
        tuneSampleSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useTopics")) {
        useTopics = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePhraseFeatures")) {
        usePhraseFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePhraseWords")) {
        usePhraseWords = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePhraseWordTags")) {
        usePhraseWordTags = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePhraseWordSpecialTags")) {
        usePhraseWordSpecialTags = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useProtoFeatures")) {
        useProtoFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useWordnetFeatures")) {
        useWordnetFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("wikiFeatureDbFile")) {
        wikiFeatureDbFile = val;
      } else if (key.equalsIgnoreCase("tokenizerOptions")) {
        tokenizerOptions = val;
      } else if (key.equalsIgnoreCase("tokenizerFactory")) {
        tokenizerFactory = val;
      } else if (key.equalsIgnoreCase("useCommonWordsFeature")) {
        useCommonWordsFeature = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useYear")) {
        useYear = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSentenceNumber")) {
        useSentenceNumber = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useLabelSource")) {
        useLabelSource = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("tokenFactory")) {

        tokenFactory = val;
      } else if (key.equalsIgnoreCase("tokensAnnotationClassName")) {
        tokensAnnotationClassName = val;
      } else if (key.equalsIgnoreCase("numLopExpert")) {
        numLopExpert = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("initialLopScales")) {
        initialLopScales = val;
      } else if (key.equalsIgnoreCase("initialLopWeights")) {
        initialLopWeights = val;
      } else if (key.equalsIgnoreCase("includeFullCRFInLOP")) {
        includeFullCRFInLOP = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("backpropLopTraining")) {
        backpropLopTraining = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("randomLopWeights")) {
        randomLopWeights = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("randomLopFeatureSplit")) {
        randomLopFeatureSplit = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("nonLinearCRF")) {
        nonLinearCRF = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("secondOrderNonLinear")) {
        secondOrderNonLinear = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("numHiddenUnits")) {
        numHiddenUnits = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useOutputLayer")) {
        useOutputLayer = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useHiddenLayer")) {
        useHiddenLayer = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("gradientDebug")) {
        gradientDebug = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("checkGradient")) {
        checkGradient = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSigmoid")) {
        useSigmoid = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("skipOutputRegularization")) {
        skipOutputRegularization = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("sparseOutputLayer")) {
        sparseOutputLayer = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("tieOutputLayer")) {
        tieOutputLayer = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("blockInitialize")) {
        blockInitialize = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("softmaxOutputLayer")) {
        softmaxOutputLayer = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("loadBisequenceClassifierEn")) {
        loadBisequenceClassifierEn = val;
      } else if (key.equalsIgnoreCase("bisequenceClassifierPropEn")) {
        bisequenceClassifierPropEn = val;
      } else if (key.equalsIgnoreCase("loadBisequenceClassifierCh")) {
        loadBisequenceClassifierCh = val;
      } else if (key.equalsIgnoreCase("bisequenceClassifierPropCh")) {
        bisequenceClassifierPropCh = val;
      } else if (key.equalsIgnoreCase("bisequenceTestFileEn")) {
        bisequenceTestFileEn = val;
      } else if (key.equalsIgnoreCase("bisequenceTestFileCh")) {
        bisequenceTestFileCh = val;
      } else if (key.equalsIgnoreCase("bisequenceTestOutputEn")) {
        bisequenceTestOutputEn = val;
      } else if (key.equalsIgnoreCase("bisequenceTestOutputCh")) {
        bisequenceTestOutputCh = val;
      } else if (key.equalsIgnoreCase("bisequenceTestAlignmentFile")) {
        bisequenceTestAlignmentFile = val;
      } else if (key.equalsIgnoreCase("bisequenceAlignmentTestOutput")) {
        bisequenceAlignmentTestOutput = val;
      } else if (key.equalsIgnoreCase("bisequencePriorType")) {
        bisequencePriorType = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("bisequenceAlignmentPriorPenaltyCh")) {
        bisequenceAlignmentPriorPenaltyCh = val;
      } else if (key.equalsIgnoreCase("bisequenceAlignmentPriorPenaltyEn")) {
        bisequenceAlignmentPriorPenaltyEn = val;
      } else if (key.equalsIgnoreCase("alignmentPruneThreshold")) {
        alignmentPruneThreshold = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("alignmentDecodeThreshold")) {
        alignmentDecodeThreshold = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("factorInAlignmentProb")) {
        factorInAlignmentProb = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useChromaticSampling")) {
        useChromaticSampling = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSequentialScanSampling")) {
        useSequentialScanSampling = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("maxAllowedChromaticSize")) {
        maxAllowedChromaticSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("keepEmptySentences")) {
        keepEmptySentences = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useBilingualNERPrior")) {
        useBilingualNERPrior = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("samplingSpeedUpThreshold")) {
        samplingSpeedUpThreshold = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("entityMatrixCh")) {
        entityMatrixCh = val;
      } else if (key.equalsIgnoreCase("entityMatrixEn")) {
        entityMatrixEn = val;
      } else if (key.equalsIgnoreCase("multiThreadGibbs")) {
        multiThreadGibbs = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("matchNERIncentive")) {
        matchNERIncentive = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useEmbedding")) {
        useEmbedding = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("prependEmbedding")) {
        prependEmbedding = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("embeddingWords")) {
        embeddingWords = val;
      } else if (key.equalsIgnoreCase("embeddingVectors")) {
        embeddingVectors = val;
      } else if (key.equalsIgnoreCase("transitionEdgeOnly")) {
        transitionEdgeOnly = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("priorLambda")) {
        priorLambda = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("addCapitalFeatures")) {
        addCapitalFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("arbitraryInputLayerSize")) {
        arbitraryInputLayerSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("noEdgeFeature")) {
        noEdgeFeature = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("terminateOnEvalImprovement")) {
        terminateOnEvalImprovement = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("terminateOnEvalImprovementNumOfEpoch")) {
        terminateOnEvalImprovementNumOfEpoch = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useMemoryEvaluator")) {
        useMemoryEvaluator = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("suppressTestDebug")) {
        suppressTestDebug = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useOWLQN")) {
        useOWLQN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("printWeights")) {
        printWeights = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("totalDataSlice")) {
        totalDataSlice = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("numOfSlices")) {
        numOfSlices = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("regularizeSoftmaxTieParam")) {
        regularizeSoftmaxTieParam = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("softmaxTieLambda")) {
        softmaxTieLambda = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("totalFeatureSlice")) {
        totalFeatureSlice = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("numOfFeatureSlices")) {
        numOfFeatureSlices = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("addBiasToEmbedding")) {
        addBiasToEmbedding = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("hardcodeSoftmaxOutputWeights")) {
        hardcodeSoftmaxOutputWeights = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNERPriorBIO")) {
        useNERPriorBIO = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("entityMatrix")) {
        entityMatrix = val;
      } else if (key.equalsIgnoreCase("multiThreadClassifier")) {
        multiThreadClassifier = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useDualDecomp")) {
        useDualDecomp = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("biAlignmentPriorIsPMI")) {
        biAlignmentPriorIsPMI = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("dampDDStepSizeWithAlignmentProb")) {
        dampDDStepSizeWithAlignmentProb = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("dualDecompAlignment")) {
        dualDecompAlignment = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("dualDecompInitialStepSizeAlignment")) {
        dualDecompInitialStepSizeAlignment = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("dualDecompNotBIO")) {
        dualDecompNotBIO = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("berkeleyAlignerLoadPath")) {
        berkeleyAlignerLoadPath = val;
      } else if (key.equalsIgnoreCase("useBerkeleyAlignerForViterbi")) {
        useBerkeleyAlignerForViterbi = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useBerkeleyCompetitivePosterior")) {
        useBerkeleyCompetitivePosterior = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDenero")) {
        useDenero = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("alignDDAlpha")) {
        alignDDAlpha = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("factorInBiEdgePotential")) {
        factorInBiEdgePotential = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("noNeighborConstraints")) {
        noNeighborConstraints = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("includeC2EViterbi")) {
        includeC2EViterbi = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("initWithPosterior")) {
        initWithPosterior = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("nerSlowerTimes")) {
        nerSlowerTimes = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("nerSkipFirstK")) {
        nerSkipFirstK = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("powerAlignProb")) {
        powerAlignProb = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("powerAlignProbAsAddition")) {
        powerAlignProbAsAddition = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("initWithNERPosterior")) {
        initWithNERPosterior = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("applyNERPenalty")) {
        applyNERPenalty = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useGenericFeatures")) {
        useGenericFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("printFactorTable")) {
        printFactorTable = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAdaGradFOBOS")) {
        useAdaGradFOBOS = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("initRate")) {
        initRate = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("groupByFeatureTemplate")) {
        groupByFeatureTemplate = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("groupByOutputClass")) {
        groupByOutputClass = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("priorAlpha")) {
        priorAlpha = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("splitWordRegex")){
        splitWordRegex = val;
      } else if (key.equalsIgnoreCase("groupByInput")){
        groupByInput = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("groupByHiddenUnit")){
        groupByHiddenUnit = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("unigramLM")){
        unigramLM = val;
      } else if (key.equalsIgnoreCase("bigramLM")){
        bigramLM = val;
      } else if (key.equalsIgnoreCase("wordSegBeamSize")){
        wordSegBeamSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("vocabFile")){
        vocabFile = val;
      } else if (key.equalsIgnoreCase("normalizedFile")){
        normalizedFile = val;
      } else if (key.equalsIgnoreCase("averagePerceptron")){
        averagePerceptron = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("loadCRFSegmenterPath")){
        loadCRFSegmenterPath = val;
      } else if (key.equalsIgnoreCase("loadPCTSegmenterPath")){
        loadPCTSegmenterPath = val;
      } else if (key.equalsIgnoreCase("crfSegmenterProp")){
        crfSegmenterProp = val;
      } else if (key.equalsIgnoreCase("pctSegmenterProp")){
        pctSegmenterProp = val;
      } else if (key.equalsIgnoreCase("dualDecompMaxItr")){
        dualDecompMaxItr = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("dualDecompInitialStepSize")){
        dualDecompInitialStepSize = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("dualDecompDebug")){
        dualDecompDebug = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("intermediateSegmenterOut")){
        intermediateSegmenterOut = val;
      } else if (key.equalsIgnoreCase("intermediateSegmenterModel")){
        intermediateSegmenterModel = val;
      } else if (key.equalsIgnoreCase("useCWSWordFeatures")){
        useCWSWordFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useCWSWordFeaturesAll")){
        useCWSWordFeaturesAll = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useCWSWordFeaturesBigram")){
        useCWSWordFeaturesBigram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("pctSegmenterLenAdjust")){
        pctSegmenterLenAdjust = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTrainLexicon")){
        useTrainLexicon = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useCWSFeatures")){
        useCWSFeatures = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("appendLC")){
        appendLC = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("perceptronDebug")){
        perceptronDebug = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("pctSegmenterScaleByCRF")){
        pctSegmenterScaleByCRF = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("pctSegmenterScale")){
        pctSegmenterScale = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("separateASCIIandRange")){
        separateASCIIandRange = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("dropoutRate")){
        dropoutRate = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("dropoutScale")){
        dropoutScale = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("multiThreadGrad")){
        multiThreadGrad = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("maxQNItr")){
        maxQNItr = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("dropoutApprox")){
        dropoutApprox = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("unsupDropoutFile")){
        unsupDropoutFile = val;
      } else if (key.equalsIgnoreCase("unsupDropoutScale")){
        unsupDropoutScale = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("startEvaluateIters")){
        startEvaluateIters = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("multiThreadPerceptron")){
        multiThreadPerceptron = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("lazyUpdate")){
        lazyUpdate = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("featureCountThresh")){
        featureCountThresh = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("serializeWeightsTo")) {
        serializeWeightsTo = val;
      } else if (key.equalsIgnoreCase("geDebug")){
        geDebug = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("doFeatureDiscovery")){
        doFeatureDiscovery = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("loadWeightsFrom")) {
        loadWeightsFrom = val;
      } else if (key.equalsIgnoreCase("loadClassIndexFrom")) {
        loadClassIndexFrom = val;
      } else if (key.equalsIgnoreCase("serializeClassIndexTo")) {
        serializeClassIndexTo = val;
      } else if (key.equalsIgnoreCase("learnCHBasedOnEN")){
        learnCHBasedOnEN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("learnENBasedOnCH")){
        learnENBasedOnCH = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("loadWeightsFromEN")){
        loadWeightsFromEN = val;
      } else if (key.equalsIgnoreCase("loadWeightsFromCH")){
        loadWeightsFromCH = val;
      } else if (key.equalsIgnoreCase("serializeToEN")){
        serializeToEN = val;
      } else if (key.equalsIgnoreCase("serializeToCH")){
        serializeToCH = val;
      } else if (key.equalsIgnoreCase("testFileEN")){
        testFileEN = val;
      } else if (key.equalsIgnoreCase("testFileCH")){
        testFileCH = val;
      } else if (key.equalsIgnoreCase("unsupFileEN")){
        unsupFileEN = val;
      } else if (key.equalsIgnoreCase("unsupFileCH")){
        unsupFileCH = val;
      } else if (key.equalsIgnoreCase("unsupAlignFile")){
        unsupAlignFile = val;
      } else if (key.equalsIgnoreCase("supFileEN")){
        supFileEN = val;
      } else if (key.equalsIgnoreCase("supFileCH")){
        supFileCH = val;
      } else if (key.equalsIgnoreCase("serializeFeatureIndexTo")){
        serializeFeatureIndexTo = val;
      } else if (key.equalsIgnoreCase("loadFeatureIndexFromEN")){
        loadFeatureIndexFromEN = val;
      } else if (key.equalsIgnoreCase("loadFeatureIndexFromCH")){
        loadFeatureIndexFromCH = val;
      } else if (key.equalsIgnoreCase("lambdaEN")){
        lambdaEN = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("lambdaCH")){
        lambdaCH = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("alternateTraining")){
        alternateTraining = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("weightByEntropy")){
        weightByEntropy = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useKL")){
        useKL = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useHardGE")){
        useHardGE = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useCRFforUnsup")){
        useCRFforUnsup = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useGEforSup")){
        useGEforSup = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useKnownLCWords")){
        useKnownLCWords = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useNoisyLabel")){
        useNoisyLabel = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("errorMatrix")) {
        errorMatrix = val;
      } else if (key.equalsIgnoreCase("printTrainLabels")){
        printTrainLabels = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("labelDictionaryCutoff")) {
        labelDictionaryCutoff = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useAdaDelta")){
        useAdaDelta = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAdaDiff")){
        useAdaDiff = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("adaGradEps")){
        adaGradEps = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("adaDeltaRho")){
        adaDeltaRho = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("useRandomSeed")){
        useRandomSeed = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("terminateOnAvgImprovement")){
        terminateOnAvgImprovement = Boolean.parseBoolean(val);

        // ADD VALUE ABOVE HERE
      } else if (key.length() > 0 && !key.equals("prop")) {
        System.err.println("Unknown property: |" + key + '|');
      }
    }
    if (startFold > numFolds) {
      System.err.println("startFold > numFolds -> setting startFold to 1");
      startFold = 1;
    }
    if (endFold > numFolds) {
      System.err.println("endFold > numFolds -> setting to numFolds");
      endFold = numFolds;
    }

    if (combo) {
      splitDocuments = false;
    }

    stringRep = sb.toString();
  } // end setProperties()

  // Thang Sep13: refactor to be used for multiple factories.
  private String getFeatureFactory(String val){
    if (val.equalsIgnoreCase("SuperSimpleFeatureFactory")) {
      val = "edu.stanford.nlp.sequences.SuperSimpleFeatureFactory";
    } else if (val.equalsIgnoreCase("NERFeatureFactory")) {
      val = "edu.stanford.nlp.ie.NERFeatureFactory";
    } else if (val.equalsIgnoreCase("GazNERFeatureFactory")) {
      val = "edu.stanford.nlp.sequences.GazNERFeatureFactory";
    } else if (val.equalsIgnoreCase("IncludeAllFeatureFactory")) {
      val = "edu.stanford.nlp.sequences.IncludeAllFeatureFactory";
    } else if (val.equalsIgnoreCase("PhraseFeatureFactory")) {
      val = "edu.stanford.nlp.article.extraction.PhraseFeatureFactory";
    } else if (val.equalsIgnoreCase("EmbeddingFeatureFactory")) {
      val = "edu.stanford.nlp.ie.EmbeddingFeatureFactory";
    }

    return val;
  }
  /**
   * Print the properties specified by this object.
   *
   * @return A String describing the properties specified by this object.
   */
  @Override
  public String toString() {
    return stringRep;
  }

  /**
   * note that this does *not* return string representation of arrays, lists and
   * enums
   *
   * @throws IllegalAccessException
   * @throws IllegalArgumentException
   */
  public String getNotNullTrueStringRep() {
    try {
      String rep = "";
      String joiner = "\n";
      Field[] f = this.getClass().getFields();
      for (Field ff : f) {

        String name = ff.getName();
        Class<?> type = ff.getType();

        if (type.equals(Boolean.class) || type.equals(boolean.class)) {
          boolean val = ff.getBoolean(this);
          if (val) {
            rep += joiner + name + "=" + val;
          }
        } else if (type.equals(String.class)) {
          String val = (String) ff.get(this);
          if (val != null)
            rep += joiner + name + "=" + val;
        } else if (type.equals(Double.class)) {
          Double val = (Double) ff.get(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(double.class)) {
          double val = ff.getDouble(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(Integer.class)) {
          Integer val = (Integer) ff.get(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(int.class)) {
          int val = ff.getInt(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(Float.class)) {
          Float val = (Float) ff.get(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(float.class)) {
          float val = ff.getFloat(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(Byte.class)) {
          Byte val = (Byte) ff.get(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(byte.class)) {
          byte val = ff.getByte(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(char.class)) {
          char val = ff.getChar(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(Long.class)) {
          Long val = (Long) ff.get(this);
          rep += joiner + name + "=" + val;
        } else if (type.equals(long.class)) {
          long val = ff.getLong(this);
          rep += joiner + name + "=" + val;
        }
      }
      return rep;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

} // end class SeqClassifierFlags
