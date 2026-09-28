package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.optimization.StochasticCalculateMethods;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

/**
 * Flags for sequence classifiers. Documentation for general flags and
 * flags for NER can be found in the Javadoc of
 * {@link edu.stanford.nlp.ie.NERFeatureFactory}. Documentation for the flags
 * for Chinese word segmentation can be found in the Javadoc of
 * {@link edu.stanford.nlp.wordseg.ChineseSegmenterFeatureFactory}.
 *
 * <i>IMPORTANT NOTE IF CHANGING THIS FILE:</i> <b>MAKE SURE</b> TO
 * ONLY ADD NEW VARIABLES AT THE END OF THE LIST OF VARIABLES (and not
 * to change existing variables)! Otherwise you usually break all
 * currently serialized classifiers!!! Search for "ADD VARIABLES ABOVE
 * HERE" below.
 *
 * Some general flags are described here
 * <table border="1">
 * <caption>Flags for sequence classifiers</caption>
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
 * the old SGD, faster to converge than Quasi-Newton if there are very large of
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
 * some ramp up time for QN). NOTE: Remember to set useQN to false</td>
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
 * <td>tokenizerOptions</td><td>String</td>
 * <td>(null)</td>
 * <td>Extra options to supply to the tokenizer when creating it.</td>
 * </tr>
 * <tr>
 * <td>tokenizerFactory</td><td>String</td>
 * <td>(null)</td>
 * <td>A different tokenizer factory to use if the ReaderAndWriter in question uses tokenizers.</td>
 * </tr>
 * </table>
 *
 * @author Jenny Finkel
 */
public class SeqClassifierFlags implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SeqClassifierFlags.class);

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

  // Turned on if non-null. Becomes part of the filename features are printed to.
  // The meaning of this option varies between classifiers (see exportFeatures for another option):
  //  - CMMClassifier: print the features of each datum
  //  - CRFClassifier: just dump the list of feature names for the whole dataset
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
  /** Set useShapeStrings to be true to say that the model should use word shape features and they are provided in
   *  the tokens, but should not be calculated via a word shape function. This flag must be false if the word shape
   *  features will be calculated; word shape features are also added if there is a defined word shape function.
   */
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

  // TODO OBSOLETE: delete when breaking serialization sometime.
  public String documentReader = "ColumnDocumentReader";

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

  public boolean printXML; // This is disused and can be removed when breaking serialization

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
  public transient List<String> gazettes = new ArrayList<>();
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
  public boolean useNERPrior = false; // todo [cdm 2014]: Disused, to be deleted, use priorModelFactory
  public boolean useAcqPrior = false; // todo [cdm 2014]: Disused, to be deleted, use priorModelFactory

  public boolean useUniformPrior = false; // todo [cdm 2014]: Disused, to be deleted, use priorModelFactory
  public boolean useMUCFeatures = false;
  public double annealingRate = 0.0;
  public String annealingType = null;
  public String loadProcessedData = null;

  public boolean initViterbi = true;

  public boolean useUnknown = false;

  public boolean checkNameList = false;

  public boolean useSemPrior = false; // todo [cdm 2014]: Disused, to be deleted, use priorModelFactory
  public boolean useFirstWord = false;

  public boolean useNumberFeature = false;

  public int ocrFold = 0;
  public transient boolean ocrTrain = false; // CDM 2017: Disused. Can delete....

  public String classifierType = "MaxEnt";
  public String svmModelFile = null;

  public String inferenceType = "Viterbi";

  public boolean useLemmaAsWord = false;

  public String type = "cmm";

  public String readerAndWriter = "edu.stanford.nlp.sequences.ColumnDocumentReaderAndWriter";

  public List<String> comboProps = new ArrayList<>();

  public boolean usePrediction = false;

  public boolean useAltGazFeatures = false;

  public String gazFilesFile = null;

  public boolean usePrediction2 = false;
  public String baseTrainDir = ".";
  public String baseTestDir = ".";
  /** A regex pattern for files, which will be evaluated within a particular directory.
   *  If non-null, used over trainFileList and trainFile.
   */
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

  /** Binary segmentation feature for character-based Chinese NER. */
  public boolean useSegmentation = false;

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

  // Only print the features for the first this many tokens encountered
  public int printFeaturesUpto = Integer.MAX_VALUE;

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

  // This is for use with the OWLQNMinimizer L1 regularization. To use it, set useQN=false,
  // and this to a positive number. A smaller number means more features are retained.
  // Depending on the problem, a good value might be
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

  // Filename to which the features generated by a CRF classify will be exported (if non-null)
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
   * Use prefixes and suffixes from the previous and current word in edge clique.
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
  // L1-prior used in QNMinimizer's OWLQN
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

  public boolean useNERPriorBIO = false; // todo [cdm 2014]: Disused, to be deleted, use priorModelFactory
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
  // keenon: changed from = 1, nowadays it makes sense to default to parallelism
  public int multiThreadGrad = Runtime.getRuntime().availableProcessors();
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
  public transient String serializeFeatureIndexToText = null;
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
  public boolean useKnownLCWords = true; // disused, can be deleted when breaking serialization
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

  public boolean strictGoodCoNLL = false;
  public boolean removeStrictGoodCoNLLDuplicates = false;

  /** A class name for a factory that vends a prior NER model that
   *  implements both SequenceModel and SequenceListener, and which
   *  is used in the Gibbs sampling sequence model inference.
   */
  public String priorModelFactory;

  /** Put in undirected (left/right) bag of words features for local
   *  neighborhood. Seems much worse than regular useDisjunctive.
   */
  public boolean useUndirectedDisjunctive;

  public boolean splitSlashHyphenWords;  // unused with new enum below. Remove when breaking serialization.

  /** How many words it is okay to add to knownLCWords after initial training.
   *  If this number is negative, then add any number of further words during classifying/testing.
   *  If this number is non-negative (greater than or equal to 0), then add at most this many words
   *  to the knownLCWords. By default, this is now set to 0, so there is no transductive learning on the
   *  test set, since too many people complained about results changing over runs. However, traditionally
   *  we used a non-zero value, and this usually helps performance a bit (until 2014 it was -1, then it
   *  was set to 10_000, so that memory would not grow without bound if a SequenceClassifier is run for
   *  a long time.
   */
  public int maxAdditionalKnownLCWords = 0; // was 10_000;

  public enum SlashHyphenEnum { NONE, WFRAG, WORD, BOTH };

  public SlashHyphenEnum slashHyphenTreatment = SlashHyphenEnum.NONE;

  public boolean useTitle2 = false;

  public boolean showNCCInfo;
  public boolean showCCInfo;
  public String crfToExamine;
  public boolean useSUTime;
  public boolean applyNumericClassifiers;
  public String combinationMode;
  public String nerModel;

  /**
   * Use prefixes and suffixes from the previous and next word in node clique.
   */
  public boolean useMoreNeighborNGrams = false;

  /** if using dict2 in a segmenter, load it with this filename */
  public String dict2name = "";

  // "ADD VARIABLES ABOVE HERE"


  public transient List<String> phraseGazettes = null;
  public transient Properties props = null;

  /**
   * Create a new SeqClassifierFlags object initialized with default values.
   */
  public SeqClassifierFlags() { }

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
   * Create a new SeqClassifierFlags object and initialize it using values in
   * the Properties object. The properties are printed to stderr as it works.
   *
   * @param props The properties object used for initialization
   * @param printProps Whether to print the properties on construction
   */
  public SeqClassifierFlags(Properties props, boolean printProps) {
    setProperties(props, printProps);
  }

  /**
   * Initialize this object using values in Properties object. The properties
   * are printed to stderr as it works.
   *
   * @param props The properties object used for initialization
   */
  public final void setProperties(Properties props) {
    setProperties(props, true);
  }

  /**
   * Initialize using values in Properties file.
   *
   * @param props The properties object used for initialization
   * @param printProps Whether to print the properties to stderr as it works.
   */
  public void setProperties(Properties props, boolean printProps) {
    this.props = props;
    StringBuilder sb = new StringBuilder(stringRep);
    for (String key : props.stringPropertyNames()) {
      String val = props.getProperty(key);
      if (!(key.isEmpty() && val.isEmpty())) {
        if (printProps) {
          log.info(key + '=' + val);
        }
        sb.append(key).append('=').append(val).append('\n');
      }
      // Case labels are lowercase so that keys match case-insensitively.
      switch (key.toLowerCase(Locale.ROOT)) {
        case "macro":
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
          break;
        case "goodconll":
          // This was developed for CMMClassifier after the original 2003 CoNLL work.
          // It is for an MEMM.  You shouldn't use it with CRFClassifier.
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
            // This was originally 20, but in Aug 2006 increased to 50, because that helped
            // for English, but actually even smaller than 20 helps for languages like
            // Spanish, so dropped in 2014 to 5.0.
            sigma = 5.0;
            // normalize
            normalize = true;
            normalizeTimex = true; // this was sort of wrong for German since it lowercases months, but didn't do too much harm
            maxLeft = 2;
            useDisjunctive = true;
            disjunctionWidth = 4; // clearly optimal for CoNLL
            useBoundarySequences = true;
            useLemmas = true; // no-op except for German
            usePrevNextLemmas = true; // no-op except for German
            strictGoodCoNLL = true; // don't add some CpC features added later
            removeStrictGoodCoNLLDuplicates = true; // added in 2014; the duplicated features don't help
            inputEncoding = "iso-8859-1"; // needed for CoNLL German and Spanish files
            // optimization
            useQN = true;
            QNsize = 15;
          }
          break;
        case "conllnotags":
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
          break;
        case "notags":
          if (Boolean.parseBoolean(val)) {
            // turn off all features that use POS tags
            // this is slightly crude: it also turns off a few things that
            // don't use tags in e.g., useTaggySequences
            useTags = false;
            useSymTags = false;
            useTaggySequences = false;
            useOccurrencePatterns = false;
          }
          break;
        case "submit":
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
          break;
        case "binnedlengths":
          if (val != null) {
            String[] binnedLengthStrs = val.split("[, ]+");
            binnedLengths = new int[binnedLengthStrs.length];
            for (int i = 0; i < binnedLengths.length; i++) {
              binnedLengths[i] = Integer.parseInt(binnedLengthStrs[i]);
            }
          }
          break;
        case "makeconsistent":
          makeConsistent = Boolean.parseBoolean(val);
          break;
        case "dump":
          dump = Boolean.parseBoolean(val);
          break;
        case "usengrams":
          useNGrams = Boolean.parseBoolean(val);
          break;
        case "useneighborngrams":
          useNeighborNGrams = Boolean.parseBoolean(val);
          break;
        case "usemoreneighborngrams":
          useMoreNeighborNGrams = Boolean.parseBoolean(val);
          break;
        case "wordfunction":
          wordFunction = ReflectionLoading.loadByReflection(val);
          break;
        case "conjoinshapengrams":
          conjoinShapeNGrams = Boolean.parseBoolean(val);
          break;
        case "lowercasengrams":
          lowercaseNGrams = Boolean.parseBoolean(val);
          break;
        case "useisurl":
          useIsURL = Boolean.parseBoolean(val);
          break;
        case "useurlsequences":
          useURLSequences = Boolean.parseBoolean(val);
          break;
        case "useentitytypes":
          useEntityTypes = Boolean.parseBoolean(val);
          break;
        case "useentityrule":
          useEntityRule = Boolean.parseBoolean(val);
          break;
        case "useordinal":
          useOrdinal = Boolean.parseBoolean(val);
          break;
        case "useentitytypesequences":
          useEntityTypeSequences = Boolean.parseBoolean(val);
          break;
        case "useisdaterange":
          useIsDateRange = Boolean.parseBoolean(val);
          break;
        case "dehyphenatengrams":
          dehyphenateNGrams = Boolean.parseBoolean(val);
          break;
        case "lowernewgenethreshold":
          lowerNewgeneThreshold = Boolean.parseBoolean(val);
          break;
        case "useprev":
          usePrev = Boolean.parseBoolean(val);
          break;
        case "usenext":
          useNext = Boolean.parseBoolean(val);
          break;
        case "usetags":
          useTags = Boolean.parseBoolean(val);
          break;
        case "usewordpairs":
          useWordPairs = Boolean.parseBoolean(val);
          break;
        case "usegazettes":
          useGazettes = Boolean.parseBoolean(val);
          break;
        case "wordshape":
          wordShape = WordShapeClassifier.lookupShaper(val);
          if (wordShape == WordShapeClassifier.NOWORDSHAPE) {
            log.warn("There is no word shaper called '" + val + "'; no word shape features will be used.");
          }
          break;
        case "useshapestrings":
          useShapeStrings = Boolean.parseBoolean(val);
          break;
        case "usegoodfornamescpc":
          useGoodForNamesCpC = Boolean.parseBoolean(val);
          break;
        case "usedictionaryconjunctions":
          useDictionaryConjunctions = Boolean.parseBoolean(val);
          break;
        case "usedictionaryconjunctions3":
          useDictionaryConjunctions3 = Boolean.parseBoolean(val);
          break;
        case "expandmiddot":
          expandMidDot = Boolean.parseBoolean(val);
          break;
        case "usesequences":
          useSequences = Boolean.parseBoolean(val);
          break;
        case "useprevsequences":
          usePrevSequences = Boolean.parseBoolean(val);
          break;
        case "usenextsequences":
          useNextSequences = Boolean.parseBoolean(val);
          break;
        case "uselongsequences":
          useLongSequences = Boolean.parseBoolean(val);
          break;
        case "useboundarysequences":
          useBoundarySequences = Boolean.parseBoolean(val);
          break;
        case "usetaggysequences":
          useTaggySequences = Boolean.parseBoolean(val);
          break;
        case "useextrataggysequences":
          useExtraTaggySequences = Boolean.parseBoolean(val);
          break;
        case "usetaggysequencesshapeinteraction":
          useTaggySequencesShapeInteraction = Boolean.parseBoolean(val);
          break;
        case "strictlyzeroethorder":
          strictlyZeroethOrder = Boolean.parseBoolean(val);
          break;
        case "strictlyfirstorder":
          strictlyFirstOrder = Boolean.parseBoolean(val);
          break;
        case "strictlysecondorder":
          strictlySecondOrder = Boolean.parseBoolean(val);
          break;
        case "strictlythirdorder":
          strictlyThirdOrder = Boolean.parseBoolean(val);
          break;
        case "dontextendtaggy":
          dontExtendTaggy = Boolean.parseBoolean(val);
          break;
        case "entitysubclassification":
          entitySubclassification = val;
          break;
        case "usegazettephrases":
          useGazettePhrases = Boolean.parseBoolean(val);
          break;
        case "phrasegazettes": {
          StringTokenizer st = new StringTokenizer(val, " ,;\t");
          if (phraseGazettes == null) {
            phraseGazettes = new ArrayList<>();
          }
          while (st.hasMoreTokens()) {
            phraseGazettes.add(st.nextToken());
          }
          break;
        }
        case "usesum":
          useSum = Boolean.parseBoolean(val);
          break;
        case "verbose":
          verboseMode = Boolean.parseBoolean(val);
          break;
        case "verbosemode":
          verboseMode = Boolean.parseBoolean(val);
          break;
        case "tolerance":
          tolerance = Double.parseDouble(val);
          break;
        case "maxiterations":
          maxIterations = Integer.parseInt(val);
          break;
        case "exportfeatures":
          exportFeatures = val;
          break;
        case "printfeatures":
          printFeatures = val;
          break;
        case "printfeaturesupto":
          printFeaturesUpto = Integer.parseInt(val);
          break;
        case "lastnamelist":
          lastNameList = val;
          break;
        case "malenamelist":
          maleNameList = val;
          break;
        case "femalenamelist":
          femaleNameList = val;
          break;
        case "usesymtags":
          useSymTags = Boolean.parseBoolean(val);
          break;
        case "usesymwordpairs":
          useSymWordPairs = Boolean.parseBoolean(val);
          break;
        case "printclassifier":
          printClassifier = val;
          break;
        case "printclassifierparam":
          printClassifierParam = Integer.parseInt(val);
          break;
        case "intern":
          intern = Boolean.parseBoolean(val);
          break;
        case "mergetags":
          mergeTags = Boolean.parseBoolean(val);
          break;
        case "iobtags":
          iobTags = Boolean.parseBoolean(val);
          break;
        case "useviterbi":
          useViterbi = Boolean.parseBoolean(val);
          break;
        case "intern2":
          intern2 = Boolean.parseBoolean(val);
          break;
        case "selftest":
          selfTest = Boolean.parseBoolean(val);
          break;
        case "sloppygazette":
          sloppyGazette = Boolean.parseBoolean(val);
          break;
        case "cleangazette":
          cleanGazette = Boolean.parseBoolean(val);
          break;
        case "nomidngrams":
          noMidNGrams = Boolean.parseBoolean(val);
          break;
        case "usereverse":
          useReverse = Boolean.parseBoolean(val);
          break;
        case "retainentitysubclassification":
          retainEntitySubclassification = Boolean.parseBoolean(val);
          break;
        case "uselemmas":
          useLemmas = Boolean.parseBoolean(val);
          break;
        case "useprevnextlemmas":
          usePrevNextLemmas = Boolean.parseBoolean(val);
          break;
        case "normalizeterms":
          normalizeTerms = Boolean.parseBoolean(val);
          break;
        case "normalizetimex":
          normalizeTimex = Boolean.parseBoolean(val);
          break;
        case "usenb":
          useNB = Boolean.parseBoolean(val);
          break;
        case "useparenmatching":
          useParenMatching = Boolean.parseBoolean(val);
          break;
        case "usetypeseqs":
          useTypeSeqs = Boolean.parseBoolean(val);
          break;
        case "usetypeseqs2":
          useTypeSeqs2 = Boolean.parseBoolean(val);
          break;
        case "usetypeseqs3":
          useTypeSeqs3 = Boolean.parseBoolean(val);
          break;
        case "usedisjunctive":
          useDisjunctive = Boolean.parseBoolean(val);
          break;
        case "useundirecteddisjunctive":
          useUndirectedDisjunctive = Boolean.parseBoolean(val);
          break;
        case "splitslashhyphenwords":
          try {
            slashHyphenTreatment = SlashHyphenEnum.valueOf(val.trim().toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException | NullPointerException iae) {
            slashHyphenTreatment = SlashHyphenEnum.NONE;
          }
          break;
        case "disjunctionwidth":
          disjunctionWidth = Integer.parseInt(val);
          break;
        case "usedisjunctiveshapeinteraction":
          useDisjunctiveShapeInteraction = Boolean.parseBoolean(val);
          break;
        case "usewidedisjunctive":
          useWideDisjunctive = Boolean.parseBoolean(val);
          break;
        case "widedisjunctionwidth":
          wideDisjunctionWidth = Integer.parseInt(val);
          break;
        case "usedisjshape":
          useDisjShape = Boolean.parseBoolean(val);
          break;
        case "usetitle":
          useTitle = Boolean.parseBoolean(val);
          break;
        case "usetitle2":
          useTitle2 = Boolean.parseBoolean(val);
          break;
        case "booleanfeatures":
          booleanFeatures = Boolean.parseBoolean(val);
          break;
        case "useclassfeature":
          useClassFeature = Boolean.parseBoolean(val);
          break;
        case "useshapeconjunctions":
          useShapeConjunctions = Boolean.parseBoolean(val);
          break;
        case "usewordtag":
          useWordTag = Boolean.parseBoolean(val);
          break;
        case "usenphead":
          useNPHead = Boolean.parseBoolean(val);
          break;
        case "usenpgovernor":
          useNPGovernor = Boolean.parseBoolean(val);
          break;
        case "useheadgov":
          useHeadGov = Boolean.parseBoolean(val);
          break;
        case "uselastrealword":
          useLastRealWord = Boolean.parseBoolean(val);
          break;
        case "usenextrealword":
          useNextRealWord = Boolean.parseBoolean(val);
          break;
        case "useoccurrencepatterns":
          useOccurrencePatterns = Boolean.parseBoolean(val);
          break;
        case "usetypeysequences":
          useTypeySequences = Boolean.parseBoolean(val);
          break;
        case "justify":
          justify = Boolean.parseBoolean(val);
          break;
        case "normalize":
          normalize = Boolean.parseBoolean(val);
          break;
        case "priortype":
          priorType = val;
          break;
        case "sigma":
          sigma = Double.parseDouble(val);
          break;
        case "epsilon":
          epsilon = Double.parseDouble(val);
          break;
        case "beamsize":
          beamSize = Integer.parseInt(val);
          break;
        case "removetopn":
          removeTopN = Integer.parseInt(val);
          break;
        case "removetopnpercent":
          removeTopNPercent = Double.parseDouble(val);
          break;
        case "randomizedratio":
          randomizedRatio = Double.parseDouble(val);
          break;
        case "numtimesremovetopn":
          numTimesRemoveTopN = Integer.parseInt(val);
          break;
        case "maxleft":
          maxLeft = Integer.parseInt(val);
          break;
        case "maxright":
          maxRight = Integer.parseInt(val);
          break;
        case "maxngramleng":
          maxNGramLeng = Integer.parseInt(val);
          break;
        case "usegazfeatures":
          useGazFeatures = Boolean.parseBoolean(val);
          break;
        case "usealtgazfeatures":
          useAltGazFeatures = Boolean.parseBoolean(val);
          break;
        case "usemoregazfeatures":
          useMoreGazFeatures = Boolean.parseBoolean(val);
          break;
        case "useabbr":
          useAbbr = Boolean.parseBoolean(val);
          break;
        case "useminimalabbr":
          useMinimalAbbr = Boolean.parseBoolean(val);
          break;
        case "useabbr1":
          useAbbr1 = Boolean.parseBoolean(val);
          break;
        case "useminimalabbr1":
          useMinimalAbbr1 = Boolean.parseBoolean(val);
          break;
        case "documentreader":
          log.info("You are using an outdated flag: -documentReader " + val);
          log.info("Please use -readerAndWriter instead.");
          break;
        case "deleteblanklines":
          deleteBlankLines = Boolean.parseBoolean(val);
          break;
        case "answerfile":
          answerFile = val;
          break;
        case "altanswerfile":
          altAnswerFile = val;
          break;
        case "loadclassifier":
        case "model":
          loadClassifier = val;
          break;
        case "loadtextclassifier":
          loadTextClassifier = val;
          break;
        case "loadjarclassifier":
          loadJarClassifier = val;
          break;
        case "loadauxclassifier":
          loadAuxClassifier = val;
          break;
        case "serializeto":
          serializeTo = val;
          break;
        case "serializetotext":
          serializeToText = val;
          break;
        case "serializedatasetsdir":
          serializeDatasetsDir = val;
          break;
        case "loaddatasetsdir":
          loadDatasetsDir = val;
          break;
        case "pushdir":
          pushDir = val;
          break;
        case "purgedatasets":
          purgeDatasets = Boolean.parseBoolean(val);
          break;
        case "keepobinmemory":
          keepOBInMemory = Boolean.parseBoolean(val);
          break;
        case "fakedataset":
          fakeDataset = Boolean.parseBoolean(val);
          break;
        case "numdatasetsperfile":
          numDatasetsPerFile = Integer.parseInt(val);
          break;
        case "trainfile":
          trainFile = val;
          break;
        case "biasedtrainfile":
          biasedTrainFile = val;
          break;
        case "classbias":
          classBias = val;
          break;
        case "confusionmatrix":
          confusionMatrix = val;
          break;
        case "adaptfile":
          adaptFile = val;
          break;
        case "devfile":
          devFile = val;
          break;
        case "testfile":
          testFile = val;
          break;
        case "outputfile":
          outputFile = val;
          break;
        case "textfile":
          textFile = val;
          break;
        case "readstdin":
          readStdin = Boolean.parseBoolean(val);
          break;
        case "initialweights":
          initialWeights = val;
          break;
        case "interimoutputfreq":
          interimOutputFreq = Integer.parseInt(val);
          break;
        case "inputencoding":
          inputEncoding = val;
          break;
        case "outputencoding":
          outputEncoding = val;
          break;
        case "encoding":
          inputEncoding = val;
          outputEncoding = val;
          break;
        case "gazette": {
          useGazettes = true;
          StringTokenizer st = new StringTokenizer(val, " ,;\t");
          if (gazettes == null) {
            gazettes = new ArrayList<>();
          } // for after deserialization, as gazettes is transient
          while (st.hasMoreTokens()) {
            gazettes.add(st.nextToken());
          }
          break;
        }
        case "useqn":
          useQN = Boolean.parseBoolean(val);
          break;
        case "qnsize":
          QNsize = Integer.parseInt(val);
          break;
        case "qnsize2":
          QNsize2 = Integer.parseInt(val);
          break;
        case "l1reg":
          useQN = false;
          l1reg = Double.parseDouble(val);
          break;
        case "usefloat":
          useFloat = Boolean.parseBoolean(val);
          break;
        case "trainmap":
          log.info("trainMap and testMap are no longer valid options - please use map instead.");
          throw new RuntimeException();
        case "testmap":
          log.info("trainMap and testMap are no longer valid options - please use map instead.");
          throw new RuntimeException();
        case "map":
          map = val;
          break;
        case "usemoreabbr":
          useMoreAbbr = Boolean.parseBoolean(val);
          break;
        case "useprevvb":
          usePrevVB = Boolean.parseBoolean(val);
          break;
        case "usenextvb":
          useNextVB = Boolean.parseBoolean(val);
          break;
        case "usevb":
          if (Boolean.parseBoolean(val)) {
            useVB = true;
            usePrevVB = true;
            useNextVB = true;
          }
          break;
        case "usechunks":
          useChunks = Boolean.parseBoolean(val);
          break;
        case "usechunkysequences":
          useChunkySequences = Boolean.parseBoolean(val);
          break;
        case "greekifyngrams":
          greekifyNGrams = Boolean.parseBoolean(val);
          break;
        case "restricttransitionstimit":
          restrictTransitionsTimit = Boolean.parseBoolean(val);
          break;
        case "usemoretags":
          useMoreTags = Boolean.parseBoolean(val);
          break;
        case "usebeginsent":
          useBeginSent = Boolean.parseBoolean(val);
          break;
        case "useposition":
          usePosition = Boolean.parseBoolean(val);
          break;
        case "usegenia":
          useGENIA = Boolean.parseBoolean(val);
          break;
        case "useabstr":
          useABSTR = Boolean.parseBoolean(val);
          break;
        case "useweb":
          useWEB = Boolean.parseBoolean(val);
          break;
        case "useante":
          useANTE = Boolean.parseBoolean(val);
          break;
        case "useacr":
          useACR = Boolean.parseBoolean(val);
          break;
        case "usetok":
          useTOK = Boolean.parseBoolean(val);
          break;
        case "useabgene":
          useABGENE = Boolean.parseBoolean(val);
          break;
        case "useabstrfreqdict":
          useABSTRFreqDict = Boolean.parseBoolean(val);
          break;
        case "useabstrfreq":
          useABSTRFreq = Boolean.parseBoolean(val);
          break;
        case "usefreq":
          useFREQ = Boolean.parseBoolean(val);
          break;
        case "usewebfreqdict":
          useWEBFreqDict = Boolean.parseBoolean(val);
          break;
        case "biosubmitoutput":
          bioSubmitOutput = Boolean.parseBoolean(val);
          break;
        case "subcwgaz":
          subCWGaz = Boolean.parseBoolean(val);
          break;
        case "splitonhead":
          splitOnHead = Boolean.parseBoolean(val);
          break;
        case "featurecountthreshold":
          featureCountThreshold = Integer.parseInt(val);
          break;
        case "useword":
          useWord = Boolean.parseBoolean(val);
          break;
        case "memorythrift":
          memoryThrift = Boolean.parseBoolean(val);
          break;
        case "timitdatum":
          timitDatum = Boolean.parseBoolean(val);
          break;
        case "splitdocuments":
          log.info("You are using an outdated flag: -splitDocuments");
          log.info("Please use -maxDocSize -1 instead.");
          splitDocuments = Boolean.parseBoolean(val);
          break;
        case "featureweightthreshold":
          featureWeightThreshold = Double.parseDouble(val);
          break;
        case "backgroundsymbol":
          backgroundSymbol = val;
          break;
        case "featurefactory": {
          // handle multiple feature factories.
          String[] tokens = val.split("\\s*,\\s*"); // multiple feature factories could be specified and are comma separated.
          int numFactories = tokens.length;
          if (numFactories==1){ // for compatible reason
            featureFactory = getFeatureFactory(val);
          }

          featureFactories = new String[numFactories];
          featureFactoriesArgs = new ArrayList<>(numFactories);
          for (int i = 0; i < numFactories; i++) {
            featureFactories[i] = getFeatureFactory(tokens[i]);
            featureFactoriesArgs.add(new Object[0]);
          }
          break;
        }
        case "printxml":
          log.info("printXML is disused; perhaps try using the -outputFormat xml option.");

          break;
        case "useseenfeaturesonly":
          useSeenFeaturesOnly = Boolean.parseBoolean(val);

          break;
        case "usebagofwords":
          useBagOfWords = Boolean.parseBoolean(val);

          // chinese word-segmenter features
          break;
        case "useradical":
          useRadical = Boolean.parseBoolean(val);
          break;
        case "usebigramintwoclique":
          useBigramInTwoClique = Boolean.parseBoolean(val);
          break;
        case "usereverseaffix":
          useReverseAffix = Boolean.parseBoolean(val);
          break;
        case "charhalfwindow":
          charHalfWindow = Integer.parseInt(val);
          break;
        case "purgefeatures":
          purgeFeatures = Integer.parseInt(val);
          break;
        case "ocrfold":
          ocrFold = Integer.parseInt(val);
          break;
        case "morphfeaturefile":
          morphFeatureFile = val;
          break;
        case "svmmodelfile":
          svmModelFile = val;
          /* Dictionary */
          break;
        case "usedictleng":
          useDictleng = Boolean.parseBoolean(val);
          break;
        case "usedict2":
          useDict2 = Boolean.parseBoolean(val);
          break;
        case "useoutdict2":
          useOutDict2 = Boolean.parseBoolean(val);
          break;
        case "outdict2":
          outDict2 = val;
          break;
        case "usedictctb2":
          useDictCTB2 = Boolean.parseBoolean(val);
          break;
        case "usedictasbc2":
          useDictASBC2 = Boolean.parseBoolean(val);
          break;
        case "usedictpk2":
          useDictPK2 = Boolean.parseBoolean(val);
          break;
        case "usedicthk2":
          useDictHK2 = Boolean.parseBoolean(val);
          /* N-gram flags */
          break;
        case "useword1":
          useWord1 = Boolean.parseBoolean(val);
          break;
        case "useword2":
          useWord2 = Boolean.parseBoolean(val);
          break;
        case "useword3":
          useWord3 = Boolean.parseBoolean(val);
          break;
        case "useword4":
          useWord4 = Boolean.parseBoolean(val);
          break;
        case "userad1":
          useRad1 = Boolean.parseBoolean(val);
          break;
        case "userad2":
          useRad2 = Boolean.parseBoolean(val);
          break;
        case "userad2b":
          useRad2b = Boolean.parseBoolean(val);
          break;
        case "usewordn":
          useWordn = Boolean.parseBoolean(val);
          /* affix flags */
          break;
        case "usectbpre1":
          useCTBPre1 = Boolean.parseBoolean(val);
          break;
        case "usectbsuf1":
          useCTBSuf1 = Boolean.parseBoolean(val);
          break;
        case "useasbcpre1":
          useASBCPre1 = Boolean.parseBoolean(val);
          break;
        case "useasbcsuf1":
          useASBCSuf1 = Boolean.parseBoolean(val);
          break;
        case "usehkpre1":
          useHKPre1 = Boolean.parseBoolean(val);
          break;
        case "usehksuf1":
          useHKSuf1 = Boolean.parseBoolean(val);
          break;
        case "usepkpre1":
          usePKPre1 = Boolean.parseBoolean(val);
          break;
        case "usepksuf1":
          usePKSuf1 = Boolean.parseBoolean(val);
          /* POS flags */
          break;
        case "usectbchar2":
          useCTBChar2 = Boolean.parseBoolean(val);
          break;
        case "useprediction":
          usePrediction = Boolean.parseBoolean(val);
          break;
        case "useasbcchar2":
          useASBCChar2 = Boolean.parseBoolean(val);
          break;
        case "usehkchar2":
          useHKChar2 = Boolean.parseBoolean(val);
          break;
        case "usepkchar2":
          usePKChar2 = Boolean.parseBoolean(val);
          /* Rule flag */
          break;
        case "userule2":
          useRule2 = Boolean.parseBoolean(val);
          /* ASBC and HK */
          break;
        case "usebig5":
          useBig5 = Boolean.parseBoolean(val);
          break;
        case "usenegdict2":
          useNegDict2 = Boolean.parseBoolean(val);
          break;
        case "usenegdict3":
          useNegDict3 = Boolean.parseBoolean(val);
          break;
        case "usenegdict4":
          useNegDict4 = Boolean.parseBoolean(val);
          break;
        case "usenegctbdict2":
          useNegCTBDict2 = Boolean.parseBoolean(val);
          break;
        case "usenegctbdict3":
          useNegCTBDict3 = Boolean.parseBoolean(val);
          break;
        case "usenegctbdict4":
          useNegCTBDict4 = Boolean.parseBoolean(val);
          break;
        case "usenegasbcdict2":
          useNegASBCDict2 = Boolean.parseBoolean(val);
          break;
        case "usenegasbcdict3":
          useNegASBCDict3 = Boolean.parseBoolean(val);
          break;
        case "usenegasbcdict4":
          useNegASBCDict4 = Boolean.parseBoolean(val);
          break;
        case "usenegpkdict2":
          useNegPKDict2 = Boolean.parseBoolean(val);
          break;
        case "usenegpkdict3":
          useNegPKDict3 = Boolean.parseBoolean(val);
          break;
        case "usenegpkdict4":
          useNegPKDict4 = Boolean.parseBoolean(val);
          break;
        case "useneghkdict2":
          useNegHKDict2 = Boolean.parseBoolean(val);
          break;
        case "useneghkdict3":
          useNegHKDict3 = Boolean.parseBoolean(val);
          break;
        case "useneghkdict4":
          useNegHKDict4 = Boolean.parseBoolean(val);
          break;
        case "usepre":
          usePre = Boolean.parseBoolean(val);
          break;
        case "usesuf":
          useSuf = Boolean.parseBoolean(val);
          break;
        case "userule":
          useRule = Boolean.parseBoolean(val);
          break;
        case "useas":
          useAs = Boolean.parseBoolean(val);
          break;
        case "usepk":
          usePk = Boolean.parseBoolean(val);
          break;
        case "usehk":
          useHk = Boolean.parseBoolean(val);
          break;
        case "usemsr":
          useMsr = Boolean.parseBoolean(val);
          break;
        case "usemsrchar2":
          useMSRChar2 = Boolean.parseBoolean(val);
          break;
        case "usefeaturesc4gram":
          useFeaturesC4gram = Boolean.parseBoolean(val);
          break;
        case "usefeaturesc5gram":
          useFeaturesC5gram = Boolean.parseBoolean(val);
          break;
        case "usefeaturesc6gram":
          useFeaturesC6gram = Boolean.parseBoolean(val);
          break;
        case "usefeaturescpc4gram":
          useFeaturesCpC4gram = Boolean.parseBoolean(val);
          break;
        case "usefeaturescpc5gram":
          useFeaturesCpC5gram = Boolean.parseBoolean(val);
          break;
        case "usefeaturescpc6gram":
          useFeaturesCpC6gram = Boolean.parseBoolean(val);
          break;
        case "useunicodetype":
          useUnicodeType = Boolean.parseBoolean(val);
          break;
        case "useunicodeblock":
          useUnicodeBlock = Boolean.parseBoolean(val);
          break;
        case "useunicodetype4gram":
          useUnicodeType4gram = Boolean.parseBoolean(val);
          break;
        case "useunicodetype5gram":
          useUnicodeType5gram = Boolean.parseBoolean(val);
          break;
        case "useshapestrings1":
          useShapeStrings1 = Boolean.parseBoolean(val);
          break;
        case "useshapestrings3":
          useShapeStrings3 = Boolean.parseBoolean(val);
          break;
        case "useshapestrings4":
          useShapeStrings4 = Boolean.parseBoolean(val);
          break;
        case "useshapestrings5":
          useShapeStrings5 = Boolean.parseBoolean(val);
          break;
        case "usewordutypeconjunctions2":
          useWordUTypeConjunctions2 = Boolean.parseBoolean(val);
          break;
        case "usewordutypeconjunctions3":
          useWordUTypeConjunctions3 = Boolean.parseBoolean(val);
          break;
        case "usewordshapeconjunctions2":
          useWordShapeConjunctions2 = Boolean.parseBoolean(val);
          break;
        case "usewordshapeconjunctions3":
          useWordShapeConjunctions3 = Boolean.parseBoolean(val);
          break;
        case "usemiddotshape":
          useMidDotShape = Boolean.parseBoolean(val);
          break;
        case "augmenteddatechars":
          augmentedDateChars = Boolean.parseBoolean(val);
          break;
        case "suppressmiddotpostprocessing":
          suppressMidDotPostprocessing = Boolean.parseBoolean(val);
          break;
        case "printnr":
          printNR = Boolean.parseBoolean(val);
          break;
        case "use4clique":
          use4Clique = Boolean.parseBoolean(val);
          break;
        case "usefilter":
          useFilter = Boolean.parseBoolean(val);
          break;
        case "largechsegfile":
          largeChSegFile = Boolean.parseBoolean(val);
          break;
        case "keepenglishwhitespaces":
          keepEnglishWhitespaces = Boolean.parseBoolean(val);
          break;
        case "keepallwhitespaces":
          keepAllWhitespaces = Boolean.parseBoolean(val);
          break;
        case "sighanpostprocessing":
          sighanPostProcessing = Boolean.parseBoolean(val);
          break;
        case "usechpos":
          useChPos = Boolean.parseBoolean(val);
          break;
        case "sighancorporadict":
          sighanCorporaDict = val;
          // end chinese word-segmenter features
          break;
        case "useobservedsequencesonly":
          useObservedSequencesOnly = Boolean.parseBoolean(val);
          break;
        case "maxdocsize":
          maxDocSize = Integer.parseInt(val);
          splitDocuments = true;
          break;
        case "printprobs":
          printProbs = Boolean.parseBoolean(val);
          break;
        case "printfirstorderprobs":
          printFirstOrderProbs = Boolean.parseBoolean(val);
          break;
        case "savefeatureindextodisk":
          saveFeatureIndexToDisk = Boolean.parseBoolean(val);
          break;
        case "removebackgroundsingletonfeatures":
          removeBackgroundSingletonFeatures = Boolean.parseBoolean(val);
          break;
        case "dogibbs":
          doGibbs = Boolean.parseBoolean(val);
          break;
        case "usemucfeatures":
          useMUCFeatures = Boolean.parseBoolean(val);
          break;
        case "initviterbi":
          initViterbi = Boolean.parseBoolean(val);
          break;
        case "checknamelist":
          checkNameList = Boolean.parseBoolean(val);
          break;
        case "usefirstword":
          useFirstWord = Boolean.parseBoolean(val);
          break;
        case "useunknown":
          useUnknown = Boolean.parseBoolean(val);
          break;
        case "cachengrams":
          cacheNGrams = Boolean.parseBoolean(val);
          break;
        case "usenumberfeature":
          useNumberFeature = Boolean.parseBoolean(val);
          break;
        case "annealingrate":
          annealingRate = Double.parseDouble(val);
          break;
        case "annealingtype":
          if (val.equalsIgnoreCase("linear") || val.equalsIgnoreCase("exp") || val.equalsIgnoreCase("exponential")) {
            annealingType = val;
          } else {
            log.info("unknown annealingType: " + val + ".  Please use linear|exp|exponential");
          }
          break;
        case "numsamples":
          numSamples = Integer.parseInt(val);
          break;
        case "inferencetype":
          inferenceType = val;
          break;
        case "loadprocesseddata":
          loadProcessedData = val;
          break;
        case "normalizationtable":
          normalizationTable = val;
          break;
        case "dictionary":
          // don't set if empty string or spaces or true: revert it to null
          // special case so can empty out dictionary list on command line!
          val = val.trim();
          if (val.length() > 0 && !"true".equals(val) && !"null".equals(val) && !"false".equals("val")) {
            dictionary = val;
          } else {
            dictionary = null;
          }
          break;
        case "serdictionary":
          // don't set if empty string or spaces or true: revert it to null
          // special case so can empty out dictionary list on command line!
          val = val.trim();
          if (val.length() > 0 && !"true".equals(val) && !"null".equals(val) && !"false".equals("val")) {
            serializedDictionary = val;
          } else {
            serializedDictionary = null;
          }
          break;
        case "dictionary2":
          // don't set if empty string or spaces or true: revert it to null
          // special case so can empty out dictionary list on command line!
          val = val.trim();
          if (val.length() > 0 && !"true".equals(val) && !"null".equals(val) && !"false".equals("val")) {
            dictionary2 = val;
          } else {
            dictionary2 = null;
          }
          break;
        case "normtableencoding":
          normTableEncoding = val;
          break;
        case "uselemmaasword":
          useLemmaAsWord = Boolean.parseBoolean(val);
          break;
        case "type":
          type = val;
          break;
        case "readerandwriter":
          readerAndWriter = val;
          break;
        case "plaintextdocumentreaderandwriter":
          plainTextDocumentReaderAndWriter = val;
          break;
        case "gazfilesfile":
          gazFilesFile = val;
          break;
        case "basetraindir":
          baseTrainDir = val;
          break;
        case "basetestdir":
          baseTestDir = val;
          break;
        case "trainfiles":
          trainFiles = val;
          break;
        case "trainfilelist":
          trainFileList = val;
          break;
        case "traindirs":
          trainDirs = val;
          break;
        case "testdirs":
          testDirs = val;
          break;
        case "testfiles":
          testFiles = val;
          break;
        case "textfiles":
          textFiles = val;
          break;
        case "useprediction2":
          usePrediction2 = Boolean.parseBoolean(val);
          break;
        case "useobservedfeaturesonly":
          useObservedFeaturesOnly = Boolean.parseBoolean(val);
          break;
        case "iobwrapper":
          iobWrapper = Boolean.parseBoolean(val);
          break;
        case "usedistsim":
          useDistSim = Boolean.parseBoolean(val);
          break;
        case "caseddistsim":
          casedDistSim = Boolean.parseBoolean(val);
          break;
        case "distsimfileformat":
          distSimFileFormat = val;
          break;
        case "distsimmaxbits":
          distSimMaxBits = Integer.parseInt(val);
          break;
        case "numberequivalencedistsim":
          numberEquivalenceDistSim = Boolean.parseBoolean(val);
          break;
        case "unknownworddistsimclass":
          unknownWordDistSimClass = val;
          break;
        case "useonlyseenweights":
          useOnlySeenWeights = Boolean.parseBoolean(val);
          break;
        case "predprop":
          predProp = val;
          break;
        case "distsimlexicon":
          distSimLexicon = val;
          break;
        case "usesegmentation":
          useSegmentation = Boolean.parseBoolean(val);
          break;
        case "useinternal":
          useInternal = Boolean.parseBoolean(val);
          break;
        case "useexternal":
          useExternal = Boolean.parseBoolean(val);
          break;
        case "useeithersideword":
          useEitherSideWord = Boolean.parseBoolean(val);
          break;
        case "useeithersidedisjunctive":
          useEitherSideDisjunctive = Boolean.parseBoolean(val);
          break;
        case "featurediffthresh":
          featureDiffThresh = Double.parseDouble(val);
          if (props.getProperty("numTimesPruneFeatures") == null) {
            numTimesPruneFeatures = 1;
          }
          break;
        case "numtimesprunefeatures":
          numTimesPruneFeatures = Integer.parseInt(val);
          break;
        case "newgenethreshold":
          newgeneThreshold = Double.parseDouble(val);
          break;
        case "doadaptation":
          doAdaptation = Boolean.parseBoolean(val);
          break;
        case "selftrainfile":
          selfTrainFile = val;
          break;
        case "selftrainiterations":
          selfTrainIterations = Integer.parseInt(val);
          break;
        case "selftrainwindowsize":
          selfTrainWindowSize = Integer.parseInt(val);
          break;
        case "selftrainconfidencethreshold":
          selfTrainConfidenceThreshold = Double.parseDouble(val);
          break;
        case "numfolds":
          numFolds = Integer.parseInt(val);
          break;
        case "startfold":
          startFold = Integer.parseInt(val);
          break;
        case "endfold":
          endFold = Integer.parseInt(val);
          break;
        case "adaptsigma":
          adaptSigma = Double.parseDouble(val);
          break;
        case "outputformat":
          outputFormat = val;
          break;
        case "usesmd":
          useSMD = Boolean.parseBoolean(val);
          break;
        case "usescaledsgd":
          useScaledSGD = Boolean.parseBoolean(val);
          break;
        case "scaledsgdmethod":
          scaledSGDMethod = Integer.parseInt(val);
          break;
        case "tunesgd":
          tuneSGD = Boolean.parseBoolean(val);
          break;
        case "stochasticcalculatemethod":
          if (val.equalsIgnoreCase("AlgorithmicDifferentiation")) {
            stochasticMethod = StochasticCalculateMethods.AlgorithmicDifferentiation;
          } else if (val.equalsIgnoreCase("IncorporatedFiniteDifference")) {
            stochasticMethod = StochasticCalculateMethods.IncorporatedFiniteDifference;
          } else if (val.equalsIgnoreCase("ExternalFinitedifference")) {
            stochasticMethod = StochasticCalculateMethods.ExternalFiniteDifference;
          }
          break;
        case "initialgain":
          initialGain = Double.parseDouble(val);
          break;
        case "stochasticbatchsize":
          stochasticBatchSize = Integer.parseInt(val);
          break;
        case "sgd2qnhesssamples":
          SGD2QNhessSamples = Integer.parseInt(val);
          break;
        case "usesgd":
          useSGD = Boolean.parseBoolean(val);
          break;
        case "useinplacesgd":
          useInPlaceSGD = Boolean.parseBoolean(val);
          break;
        case "usesgdtoqn":
          useSGDtoQN = Boolean.parseBoolean(val);
          break;
        case "sgdpasses":
          SGDPasses = Integer.parseInt(val);
          break;
        case "qnpasses":
          QNPasses = Integer.parseInt(val);
          break;
        case "gainsgd":
          gainSGD = Double.parseDouble(val);
          break;
        case "usehybrid":
          useHybrid = Boolean.parseBoolean(val);
          break;
        case "hybridcutoffiteration":
          hybridCutoffIteration = Integer.parseInt(val);
          break;
        case "usestochasticqn":
          useStochasticQN = Boolean.parseBoolean(val);
          break;
        case "outputiterationstofile":
          outputIterationsToFile = Boolean.parseBoolean(val);
          break;
        case "testobjfunction":
          testObjFunction = Boolean.parseBoolean(val);
          break;
        case "testvariance":
          testVariance = Boolean.parseBoolean(val);
          break;
        case "crforder":
          CRForder = Integer.parseInt(val);
          break;
        case "crfwindow":
          CRFwindow = Integer.parseInt(val);
          break;
        case "testhesssamples":
          testHessSamples = Boolean.parseBoolean(val);
          break;
        case "estimateinitial":
          estimateInitial = Boolean.parseBoolean(val);
          break;
        case "printlabelvalue":
          printLabelValue = Boolean.parseBoolean(val);
          break;
        case "searchgraphprefix":
          searchGraphPrefix = val;
          break;
        case "searchgraphprune":
          searchGraphPrune = Double.parseDouble(val);
          break;
        case "kbest":
          useKBest = true;
          kBest = Integer.parseInt(val);
          break;
        case "userobustqn":
          useRobustQN = true;
          break;
        case "combo":
          combo = Boolean.parseBoolean(val);
          break;
        case "verbosefortruecasing":
          verboseForTrueCasing = Boolean.parseBoolean(val);
          break;
        case "trainhierarchical":
          trainHierarchical = val;
          break;
        case "domain":
          domain = val;
          break;
        case "baseline":
          baseline = Boolean.parseBoolean(val);
          break;
        case "dofe":
          doFE = Boolean.parseBoolean(val);
          break;
        case "restrictlabels":
          restrictLabels = Boolean.parseBoolean(val);
          break;
        case "transfersigmas":
          transferSigmas = val;
          break;
        case "announceobjectbankentries":
          announceObjectBankEntries = true;
          break;
        case "mixedcasemapfile":
          mixedCaseMapFile = val;
          break;
        case "auxtruecasemodels":
          auxTrueCaseModels = val;
          break;
        case "use2w":
          use2W = Boolean.parseBoolean(val);
          break;
        case "uselc":
          useLC = Boolean.parseBoolean(val);
          break;
        case "useyetmorecpcshapes":
          useYetMoreCpCShapes = Boolean.parseBoolean(val);
          break;
        case "useifinteger":
          useIfInteger = Boolean.parseBoolean(val);
          break;
        case "twostage":
          twoStage = Boolean.parseBoolean(val);
          break;
        case "evaluateiters":
          evaluateIters = Integer.parseInt(val);
          break;
        case "evalcmd":
          evalCmd = val;
          break;
        case "evaluatetrain":
          evaluateTrain = Boolean.parseBoolean(val);
          break;
        case "evaluatebackground":
          evaluateBackground = Boolean.parseBoolean(val);
          break;
        case "tunesamplesize":
          tuneSampleSize = Integer.parseInt(val);
          break;
        case "usetopics":
          useTopics = Boolean.parseBoolean(val);
          break;
        case "usephrasefeatures":
          usePhraseFeatures = Boolean.parseBoolean(val);
          break;
        case "usephrasewords":
          usePhraseWords = Boolean.parseBoolean(val);
          break;
        case "usephrasewordtags":
          usePhraseWordTags = Boolean.parseBoolean(val);
          break;
        case "usephrasewordspecialtags":
          usePhraseWordSpecialTags = Boolean.parseBoolean(val);
          break;
        case "useprotofeatures":
          useProtoFeatures = Boolean.parseBoolean(val);
          break;
        case "usewordnetfeatures":
          useWordnetFeatures = Boolean.parseBoolean(val);
          break;
        case "wikifeaturedbfile":
          wikiFeatureDbFile = val;
          break;
        case "tokenizeroptions":
          tokenizerOptions = val;
          break;
        case "tokenizerfactory":
          tokenizerFactory = val;
          break;
        case "usecommonwordsfeature":
          useCommonWordsFeature = Boolean.parseBoolean(val);
          break;
        case "useyear":
          useYear = Boolean.parseBoolean(val);
          break;
        case "usesentencenumber":
          useSentenceNumber = Boolean.parseBoolean(val);
          break;
        case "uselabelsource":
          useLabelSource = Boolean.parseBoolean(val);
          break;
        case "tokenfactory":

          tokenFactory = val;
          break;
        case "tokensannotationclassname":
          tokensAnnotationClassName = val;
          break;
        case "numlopexpert":
          numLopExpert = Integer.parseInt(val);
          break;
        case "initiallopscales":
          initialLopScales = val;
          break;
        case "initiallopweights":
          initialLopWeights = val;
          break;
        case "includefullcrfinlop":
          includeFullCRFInLOP = Boolean.parseBoolean(val);
          break;
        case "backproploptraining":
          backpropLopTraining = Boolean.parseBoolean(val);
          break;
        case "randomlopweights":
          randomLopWeights = Boolean.parseBoolean(val);
          break;
        case "randomlopfeaturesplit":
          randomLopFeatureSplit = Boolean.parseBoolean(val);
          break;
        case "nonlinearcrf":
          nonLinearCRF = Boolean.parseBoolean(val);
          break;
        case "secondordernonlinear":
          secondOrderNonLinear = Boolean.parseBoolean(val);
          break;
        case "numhiddenunits":
          numHiddenUnits = Integer.parseInt(val);
          break;
        case "useoutputlayer":
          useOutputLayer = Boolean.parseBoolean(val);
          break;
        case "usehiddenlayer":
          useHiddenLayer = Boolean.parseBoolean(val);
          break;
        case "gradientdebug":
          gradientDebug = Boolean.parseBoolean(val);
          break;
        case "checkgradient":
          checkGradient = Boolean.parseBoolean(val);
          break;
        case "usesigmoid":
          useSigmoid = Boolean.parseBoolean(val);
          break;
        case "skipoutputregularization":
          skipOutputRegularization = Boolean.parseBoolean(val);
          break;
        case "sparseoutputlayer":
          sparseOutputLayer = Boolean.parseBoolean(val);
          break;
        case "tieoutputlayer":
          tieOutputLayer = Boolean.parseBoolean(val);
          break;
        case "blockinitialize":
          blockInitialize = Boolean.parseBoolean(val);
          break;
        case "softmaxoutputlayer":
          softmaxOutputLayer = Boolean.parseBoolean(val);
          break;
        case "loadbisequenceclassifieren":
          loadBisequenceClassifierEn = val;
          break;
        case "bisequenceclassifierpropen":
          bisequenceClassifierPropEn = val;
          break;
        case "loadbisequenceclassifierch":
          loadBisequenceClassifierCh = val;
          break;
        case "bisequenceclassifierpropch":
          bisequenceClassifierPropCh = val;
          break;
        case "bisequencetestfileen":
          bisequenceTestFileEn = val;
          break;
        case "bisequencetestfilech":
          bisequenceTestFileCh = val;
          break;
        case "bisequencetestoutputen":
          bisequenceTestOutputEn = val;
          break;
        case "bisequencetestoutputch":
          bisequenceTestOutputCh = val;
          break;
        case "bisequencetestalignmentfile":
          bisequenceTestAlignmentFile = val;
          break;
        case "bisequencealignmenttestoutput":
          bisequenceAlignmentTestOutput = val;
          break;
        case "bisequencepriortype":
          bisequencePriorType = Integer.parseInt(val);
          break;
        case "bisequencealignmentpriorpenaltych":
          bisequenceAlignmentPriorPenaltyCh = val;
          break;
        case "bisequencealignmentpriorpenaltyen":
          bisequenceAlignmentPriorPenaltyEn = val;
          break;
        case "alignmentprunethreshold":
          alignmentPruneThreshold = Double.parseDouble(val);
          break;
        case "alignmentdecodethreshold":
          alignmentDecodeThreshold = Double.parseDouble(val);
          break;
        case "factorinalignmentprob":
          factorInAlignmentProb = Boolean.parseBoolean(val);
          break;
        case "usechromaticsampling":
          useChromaticSampling = Boolean.parseBoolean(val);
          break;
        case "usesequentialscansampling":
          useSequentialScanSampling = Boolean.parseBoolean(val);
          break;
        case "maxallowedchromaticsize":
          maxAllowedChromaticSize = Integer.parseInt(val);
          break;
        case "keepemptysentences":
          keepEmptySentences = Boolean.parseBoolean(val);
          break;
        case "usebilingualnerprior":
          useBilingualNERPrior = Boolean.parseBoolean(val);
          break;
        case "samplingspeedupthreshold":
          samplingSpeedUpThreshold = Integer.parseInt(val);
          break;
        case "entitymatrixch":
          entityMatrixCh = val;
          break;
        case "entitymatrixen":
          entityMatrixEn = val;
          break;
        case "multithreadgibbs":
          multiThreadGibbs = Integer.parseInt(val);
          break;
        case "matchnerincentive":
          matchNERIncentive = Boolean.parseBoolean(val);
          break;
        case "useembedding":
          useEmbedding = Boolean.parseBoolean(val);
          break;
        case "prependembedding":
          prependEmbedding = Boolean.parseBoolean(val);
          break;
        case "embeddingwords":
          embeddingWords = val;
          break;
        case "embeddingvectors":
          embeddingVectors = val;
          break;
        case "transitionedgeonly":
          transitionEdgeOnly = Boolean.parseBoolean(val);
          break;
        case "priorlambda":
          priorLambda = Double.parseDouble(val);
          break;
        case "addcapitalfeatures":
          addCapitalFeatures = Boolean.parseBoolean(val);
          break;
        case "arbitraryinputlayersize":
          arbitraryInputLayerSize = Integer.parseInt(val);
          break;
        case "noedgefeature":
          noEdgeFeature = Boolean.parseBoolean(val);
          break;
        case "terminateonevalimprovement":
          terminateOnEvalImprovement = Boolean.parseBoolean(val);
          break;
        case "terminateonevalimprovementnumofepoch":
          terminateOnEvalImprovementNumOfEpoch = Integer.parseInt(val);
          break;
        case "usememoryevaluator":
          useMemoryEvaluator = Boolean.parseBoolean(val);
          break;
        case "suppresstestdebug":
          suppressTestDebug = Boolean.parseBoolean(val);
          break;
        case "useowlqn":
          useOWLQN = Boolean.parseBoolean(val);
          break;
        case "printweights":
          printWeights = Boolean.parseBoolean(val);
          break;
        case "totaldataslice":
          totalDataSlice = Integer.parseInt(val);
          break;
        case "numofslices":
          numOfSlices = Integer.parseInt(val);
          break;
        case "regularizesoftmaxtieparam":
          regularizeSoftmaxTieParam = Boolean.parseBoolean(val);
          break;
        case "softmaxtielambda":
          softmaxTieLambda = Double.parseDouble(val);
          break;
        case "totalfeatureslice":
          totalFeatureSlice = Integer.parseInt(val);
          break;
        case "numoffeatureslices":
          numOfFeatureSlices = Integer.parseInt(val);
          break;
        case "addbiastoembedding":
          addBiasToEmbedding = Boolean.parseBoolean(val);
          break;
        case "hardcodesoftmaxoutputweights":
          hardcodeSoftmaxOutputWeights = Boolean.parseBoolean(val);
          break;
        case "entitymatrix":
          entityMatrix = val;
          break;
        case "multithreadclassifier":
          multiThreadClassifier = Integer.parseInt(val);
          break;
        case "usedualdecomp":
          useDualDecomp = Boolean.parseBoolean(val);
          break;
        case "bialignmentpriorispmi":
          biAlignmentPriorIsPMI = Boolean.parseBoolean(val);
          break;
        case "dampddstepsizewithalignmentprob":
          dampDDStepSizeWithAlignmentProb = Boolean.parseBoolean(val);
          break;
        case "dualdecompalignment":
          dualDecompAlignment = Boolean.parseBoolean(val);
          break;
        case "dualdecompinitialstepsizealignment":
          dualDecompInitialStepSizeAlignment = Double.parseDouble(val);
          break;
        case "dualdecompnotbio":
          dualDecompNotBIO = Boolean.parseBoolean(val);
          break;
        case "berkeleyalignerloadpath":
          berkeleyAlignerLoadPath = val;
          break;
        case "useberkeleyalignerforviterbi":
          useBerkeleyAlignerForViterbi = Boolean.parseBoolean(val);
          break;
        case "useberkeleycompetitiveposterior":
          useBerkeleyCompetitivePosterior = Boolean.parseBoolean(val);
          break;
        case "usedenero":
          useDenero = Boolean.parseBoolean(val);
          break;
        case "alignddalpha":
          alignDDAlpha = Double.parseDouble(val);
          break;
        case "factorinbiedgepotential":
          factorInBiEdgePotential = Boolean.parseBoolean(val);
          break;
        case "noneighborconstraints":
          noNeighborConstraints = Boolean.parseBoolean(val);
          break;
        case "includec2eviterbi":
          includeC2EViterbi = Boolean.parseBoolean(val);
          break;
        case "initwithposterior":
          initWithPosterior = Boolean.parseBoolean(val);
          break;
        case "nerslowertimes":
          nerSlowerTimes = Integer.parseInt(val);
          break;
        case "nerskipfirstk":
          nerSkipFirstK = Integer.parseInt(val);
          break;
        case "poweralignprob":
          powerAlignProb = Boolean.parseBoolean(val);
          break;
        case "poweralignprobasaddition":
          powerAlignProbAsAddition = Boolean.parseBoolean(val);
          break;
        case "initwithnerposterior":
          initWithNERPosterior = Boolean.parseBoolean(val);
          break;
        case "applynerpenalty":
          applyNERPenalty = Boolean.parseBoolean(val);
          break;
        case "usegenericfeatures":
          useGenericFeatures = Boolean.parseBoolean(val);
          break;
        case "printfactortable":
          printFactorTable = Boolean.parseBoolean(val);
          break;
        case "useadagradfobos":
          useAdaGradFOBOS = Boolean.parseBoolean(val);
          break;
        case "initrate":
          initRate = Double.parseDouble(val);
          break;
        case "groupbyfeaturetemplate":
          groupByFeatureTemplate = Boolean.parseBoolean(val);
          break;
        case "groupbyoutputclass":
          groupByOutputClass = Boolean.parseBoolean(val);
          break;
        case "prioralpha":
          priorAlpha = Double.parseDouble(val);
          break;
        case "splitwordregex":
          splitWordRegex = val;
          break;
        case "groupbyinput":
          groupByInput = Boolean.parseBoolean(val);
          break;
        case "groupbyhiddenunit":
          groupByHiddenUnit = Boolean.parseBoolean(val);
          break;
        case "unigramlm":
          unigramLM = val;
          break;
        case "bigramlm":
          bigramLM = val;
          break;
        case "wordsegbeamsize":
          wordSegBeamSize = Integer.parseInt(val);
          break;
        case "vocabfile":
          vocabFile = val;
          break;
        case "normalizedfile":
          normalizedFile = val;
          break;
        case "averageperceptron":
          averagePerceptron = Boolean.parseBoolean(val);
          break;
        case "loadcrfsegmenterpath":
          loadCRFSegmenterPath = val;
          break;
        case "loadpctsegmenterpath":
          loadPCTSegmenterPath = val;
          break;
        case "crfsegmenterprop":
          crfSegmenterProp = val;
          break;
        case "pctsegmenterprop":
          pctSegmenterProp = val;
          break;
        case "dualdecompmaxitr":
          dualDecompMaxItr = Integer.parseInt(val);
          break;
        case "dualdecompinitialstepsize":
          dualDecompInitialStepSize = Double.parseDouble(val);
          break;
        case "dualdecompdebug":
          dualDecompDebug = Boolean.parseBoolean(val);
          break;
        case "intermediatesegmenterout":
          intermediateSegmenterOut = val;
          break;
        case "intermediatesegmentermodel":
          intermediateSegmenterModel = val;
          break;
        case "usecwswordfeatures":
          useCWSWordFeatures = Boolean.parseBoolean(val);
          break;
        case "usecwswordfeaturesall":
          useCWSWordFeaturesAll = Boolean.parseBoolean(val);
          break;
        case "usecwswordfeaturesbigram":
          useCWSWordFeaturesBigram = Boolean.parseBoolean(val);
          break;
        case "pctsegmenterlenadjust":
          pctSegmenterLenAdjust = Boolean.parseBoolean(val);
          break;
        case "usetrainlexicon":
          useTrainLexicon = Boolean.parseBoolean(val);
          break;
        case "usecwsfeatures":
          useCWSFeatures = Boolean.parseBoolean(val);
          break;
        case "appendlc":
          appendLC = Boolean.parseBoolean(val);
          break;
        case "perceptrondebug":
          perceptronDebug = Boolean.parseBoolean(val);
          break;
        case "pctsegmenterscalebycrf":
          pctSegmenterScaleByCRF = Boolean.parseBoolean(val);
          break;
        case "pctsegmenterscale":
          pctSegmenterScale = Double.parseDouble(val);
          break;
        case "separateasciiandrange":
          separateASCIIandRange = Boolean.parseBoolean(val);
          break;
        case "dropoutrate":
          dropoutRate = Double.parseDouble(val);
          break;
        case "dropoutscale":
          dropoutScale = Double.parseDouble(val);
          break;
        case "multithreadgrad":
          multiThreadGrad = Integer.parseInt(val);
          break;
        case "maxqnitr":
          maxQNItr = Integer.parseInt(val);
          break;
        case "dropoutapprox":
          dropoutApprox = Boolean.parseBoolean(val);
          break;
        case "unsupdropoutfile":
          unsupDropoutFile = val;
          break;
        case "unsupdropoutscale":
          unsupDropoutScale = Double.parseDouble(val);
          break;
        case "startevaluateiters":
          startEvaluateIters = Integer.parseInt(val);
          break;
        case "multithreadperceptron":
          multiThreadPerceptron = Integer.parseInt(val);
          break;
        case "lazyupdate":
          lazyUpdate = Boolean.parseBoolean(val);
          break;
        case "featurecountthresh":
          featureCountThresh = Integer.parseInt(val);
          break;
        case "serializeweightsto":
          serializeWeightsTo = val;
          break;
        case "gedebug":
          geDebug = Boolean.parseBoolean(val);
          break;
        case "dofeaturediscovery":
          doFeatureDiscovery = Boolean.parseBoolean(val);
          break;
        case "loadweightsfrom":
          loadWeightsFrom = val;
          break;
        case "loadclassindexfrom":
          loadClassIndexFrom = val;
          break;
        case "serializeclassindexto":
          serializeClassIndexTo = val;
          break;
        case "learnchbasedonen":
          learnCHBasedOnEN = Boolean.parseBoolean(val);
          break;
        case "learnenbasedonch":
          learnENBasedOnCH = Boolean.parseBoolean(val);
          break;
        case "loadweightsfromen":
          loadWeightsFromEN = val;
          break;
        case "loadweightsfromch":
          loadWeightsFromCH = val;
          break;
        case "serializetoen":
          serializeToEN = val;
          break;
        case "serializetoch":
          serializeToCH = val;
          break;
        case "testfileen":
          testFileEN = val;
          break;
        case "testfilech":
          testFileCH = val;
          break;
        case "unsupfileen":
          unsupFileEN = val;
          break;
        case "unsupfilech":
          unsupFileCH = val;
          break;
        case "unsupalignfile":
          unsupAlignFile = val;
          break;
        case "supfileen":
          supFileEN = val;
          break;
        case "supfilech":
          supFileCH = val;
          break;
        case "serializefeatureindexto":
          serializeFeatureIndexTo = val;
          break;
        case "serializefeatureindextotext":
          serializeFeatureIndexToText = val;
          break;
        case "loadfeatureindexfromen":
          loadFeatureIndexFromEN = val;
          break;
        case "loadfeatureindexfromch":
          loadFeatureIndexFromCH = val;
          break;
        case "lambdaen":
          lambdaEN = Double.parseDouble(val);
          break;
        case "lambdach":
          lambdaCH = Double.parseDouble(val);
          break;
        case "alternatetraining":
          alternateTraining = Boolean.parseBoolean(val);
          break;
        case "weightbyentropy":
          weightByEntropy = Boolean.parseBoolean(val);
          break;
        case "usekl":
          useKL = Boolean.parseBoolean(val);
          break;
        case "usehardge":
          useHardGE = Boolean.parseBoolean(val);
          break;
        case "usecrfforunsup":
          useCRFforUnsup = Boolean.parseBoolean(val);
          break;
        case "usegeforsup":
          useGEforSup = Boolean.parseBoolean(val);
          break;
        case "useknownlcwords":
          log.info("useKnownLCWords is deprecated; see maxAdditionalKnownLCWords (true = -1, false = 0)");
          maxAdditionalKnownLCWords = Boolean.parseBoolean(val) ? -1: 0;
          break;
        case "usenoisylabel":
          useNoisyLabel = Boolean.parseBoolean(val);
          break;
        case "errormatrix":
          errorMatrix = val;
          break;
        case "printtrainlabels":
          printTrainLabels = Boolean.parseBoolean(val);
          break;
        case "labeldictionarycutoff":
          labelDictionaryCutoff = Integer.parseInt(val);
          break;
        case "useadadelta":
          useAdaDelta = Boolean.parseBoolean(val);
          break;
        case "useadadiff":
          useAdaDiff = Boolean.parseBoolean(val);
          break;
        case "adagradeps":
          adaGradEps = Double.parseDouble(val);
          break;
        case "adadeltarho":
          adaDeltaRho = Double.parseDouble(val);
          break;
        case "userandomseed":
          useRandomSeed = Boolean.parseBoolean(val);
          break;
        case "terminateonavgimprovement":
          terminateOnAvgImprovement = Boolean.parseBoolean(val);
          break;
        case "strictgoodconll":
          strictGoodCoNLL = Boolean.parseBoolean(val);
          break;
        case "removestrictgoodconllduplicates":
          removeStrictGoodCoNLLDuplicates = Boolean.parseBoolean(val);
          break;
        case "priormodelfactory":
          priorModelFactory = val;
          break;
        case "maxadditionalknownlcwords":
          maxAdditionalKnownLCWords = Integer.parseInt(val);
          break;
        case "shownccinfo":
          showNCCInfo = Boolean.parseBoolean(val);
          break;
        case "showccinfo":
          showCCInfo = Boolean.parseBoolean(val);
          break;
        case "crftoexamine":
          crfToExamine = val;
          break;
        case "ner.usesutime":
          useSUTime = Boolean.parseBoolean(val);
          break;
        case "ner.applynumericclassifiers":
          applyNumericClassifiers = Boolean.parseBoolean(val);
          break;
        case "ner.combinationmode":
          combinationMode = val;
          break;
        case "ner.model":
          nerModel = val;
          break;
        case "sutime.language":
          break;
        case "dict2name":
          dict2name = val;
          break;
        // ADD VALUE ABOVE HERE.  Case labels must be all lowercase:
        // keys are lowercased before the switch, so they match case-insensitively.
        default:
          if (key.startsWith("prop") && !key.equals("prop")) {
            comboProps.add(val);
          } else if (! key.isEmpty() && ! key.equals("prop")) {
            log.info("Unknown property: |" + key + '|');
          }
      }
    }
    if (startFold > numFolds) {
      log.info("startFold > numFolds -> setting startFold to 1");
      startFold = 1;
    }
    if (endFold > numFolds) {
      log.info("endFold > numFolds -> setting to numFolds");
      endFold = numFolds;
    }

    if (combo) {
      splitDocuments = false;
    }

    stringRep = sb.toString();
  } // end setProperties()

  public static Map<String, Integer> flagsToNumArgs() {
    Map<String, Integer> numArgs = new HashMap<String, Integer>();
    numArgs.put("maxDocSize", 1);
    return numArgs;
  }

  // Thang Sep13: refactor to be used for multiple factories.
  private static String getFeatureFactory(String val){
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
   * @throws IllegalArgumentException
   */
  public String getNotNullTrueStringRep() {
    try {
      StringBuilder rep = new StringBuilder();
      String joiner = "\n";
      Field[] f = this.getClass().getFields();
      for (Field ff : f) {

        String name = ff.getName();
        Class<?> type = ff.getType();

        if (type.equals(Boolean.class) || type.equals(boolean.class)) {
          boolean val = ff.getBoolean(this);
          if (val) {
            rep.append(joiner).append(name).append('=').append(val);
          }
        } else if (type.equals(String.class)) {
          String val = (String) ff.get(this);
          if (val != null)
            rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(Double.class)) {
          Double val = (Double) ff.get(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(double.class)) {
          double val = ff.getDouble(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(Integer.class)) {
          Integer val = (Integer) ff.get(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(int.class)) {
          int val = ff.getInt(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(Float.class)) {
          Float val = (Float) ff.get(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(float.class)) {
          float val = ff.getFloat(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(Byte.class)) {
          Byte val = (Byte) ff.get(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(byte.class)) {
          byte val = ff.getByte(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(char.class)) {
          char val = ff.getChar(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(Long.class)) {
          Long val = (Long) ff.get(this);
          rep.append(joiner).append(name).append('=').append(val);
        } else if (type.equals(long.class)) {
          long val = ff.getLong(this);
          rep.append(joiner).append(name).append('=').append(val);
        }
      }
      return rep.toString();
    } catch (Exception e) {
      e.printStackTrace();
      return "";
    }
  }

} // end class SeqClassifierFlags
