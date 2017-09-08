package old.edu.stanford.nlp.sequences;

import old.edu.stanford.nlp.ling.CoreLabel;
import old.edu.stanford.nlp.optimization.StochasticCalculateMethods;
import old.edu.stanford.nlp.process.WordShapeClassifier;

import java.io.Serializable;
import java.util.*;


/** Flags for sequence classifiers. Documentation for general flags and
 *  flags for NER can be found in the Javadoc of
 *  {@link edu.stanford.nlp.ie.NERFeatureFactory}.
 *  Documentation for the flags for Chinese word segmentation can be
 *  found in the Javadoc of
 *  {@link edu.stanford.nlp.wordseg.ChineseSegmenterFeatureFactory}.
 *  <p/>
 *  <i>Programming note:</i> Try <b>very hard</b> to only add new variables
 *  at the end of the list of variables (and not to change existing variables).
 *  Otherwise you usually break all currently serialized classifiers!
 *  Search for "ADD VARIABLES ABOVE HERE" below.
 *
 *  @author Jenny Finkel
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
  public boolean useWordLabelCounts = false;
  //boolean usePrevInstanceLabel = false;
  //boolean useNextInstanceLabel = false;
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

  public boolean useWord = true;  // ON by default
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
  public boolean useEntityRule =false;
  public boolean useOrdinal=false;
  public boolean useACR = false;
  public boolean useANTE = false;

  public boolean useMoreTags = false;

  public boolean useChunks = false;
  public boolean useChunkySequences = false;

  public boolean usePrevVB = false;
  public boolean useNextVB = false;
  public boolean useVB = false;
  public boolean subCWGaz = false;

  public String documentReader = "ColumnDocumentReader";  // OBSOLETE: delete when breaking serialization sometime.

  //  public String trainMap = "word=0,tag=1,answer=2";
  //  public String testMap = "word=0,tag=1,answer=2";
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
  public boolean useCTBChar2= false;
  public boolean useASBCChar2= false;
  public boolean useHKChar2= false;
  public boolean usePKChar2= false;
  public boolean useRule2=false;
  public boolean useDict2=false;
  public boolean useOutDict2=false;
  public String  outDict2="/u/htseng/scr/chunking/segmentation/out.lexicon";
  public boolean useDictleng=false;
  public boolean useDictCTB2=false;
  public boolean useDictASBC2=false;
  public boolean useDictPK2=false;
  public boolean useDictHK2=false;
  public boolean useBig5=false;
  public boolean useNegDict2=false;
  public boolean useNegDict3=false;
  public boolean useNegDict4=false;
  public boolean useNegCTBDict2=false;
  public boolean useNegCTBDict3=false;
  public boolean useNegCTBDict4=false;
  public boolean useNegASBCDict2=false;
  public boolean useNegASBCDict3=false;
  public boolean useNegASBCDict4=false;
  public boolean useNegHKDict2=false;
  public boolean useNegHKDict3=false;
  public boolean useNegHKDict4=false;
  public boolean useNegPKDict2=false;
  public boolean useNegPKDict3=false;
  public boolean useNegPKDict4=false;
  public boolean usePre=false;
  public boolean useSuf=false;
  public boolean useRule=false;
  public boolean useHk=false;
  public boolean useMsr=false;
  public boolean useMSRChar2=false;
  public boolean usePk=false;
  public boolean useAs=false;
  public boolean useFilter=false; // this flag is used for nothing; delete when breaking serialization
  public boolean largeChSegFile =false; // this flag is used for nothing; delete when breaking serialization
  public boolean useRad2b = false;

  /**
   * Keep the whitespaces between English words in testFile when printing out answers.
   * Doesn't really change the content of the CoreLabels. (For Chinese segmentation.)
   */
  public boolean keepEnglishWhitespaces = false;

  /**
   * Keep all the whitespaces words in testFile when printing out answers.
   * Doesn't really change the content of the CoreLabels. (For Chinese segmentation.)
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

  /** for Sighan bakeoff 2005, the path to the dictionary of bigrams appeared in corpus */
  public String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";

  // end Sighan 20005 chinese word-segmenter features/properties

  public boolean useWordShapeGaz = false;
  public String wordShapeGaz = null;

  // TODO: This should maybe be removed in favor of suppressing splitting when maxDocLengh <= 0, when
  // next breaking serialization
  public boolean splitDocuments = true;

  public boolean printXML = false;

  public boolean useSeenFeaturesOnly = false;

  public String lastNameList = "/u/nlp/data/dist.all.last";
  public String maleNameList = "/u/nlp/data/dist.male.first";
  public String femaleNameList = "/u/nlp/data/dist.female.first";

  // don't want these serialized
  public transient String trainFile = null;
  /** NER adapation (Gaussian prior) parameters. */
  public transient String adaptFile = null;
  public transient String devFile = null;
  public transient String testFile = null;
  public transient String textFile = null;
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
  public boolean mergeTags; // whether to merge B- and I- tags
  public boolean splitOnHead;

  // threshold
  public int featureCountThreshold = 0;
  public double featureWeightThreshold = 0.0;

  // feature factory
  public String featureFactory = "edu.stanford.nlp.ie.NERFeatureFactory";

  public String backgroundSymbol = DEFAULT_BACKGROUND_SYMBOL;
  //use
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
  /** If true and doGibbs also true, will do generic Gibbs inference without any priors */
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
  public String trainDirs = null; // cdm 2009: this is currently unsupported, but one user wanted something like this....
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

  public boolean iobWrapper = false;
  public boolean iobTags = false;
  public boolean useSegmentation = false; /* binary segmentation feature for character-based Chinese NER */

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
  public int selfTrainWindowSize = 1; //Unigram
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
  public StochasticCalculateMethods stochasticMethod = StochasticCalculateMethods.NoneSpecified ;
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
  public int CRForder = 1;
  public int CRFwindow = 2;
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

  public boolean printLabelValue;  // Old printErrorStuff

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

  public boolean announceObjectBankEntries = false; // whether to print a line giving each ObjectBank entry (usually a filename)

  //Arabic Subject Detector flags
  public boolean usePos = false;
  public boolean useAgreement = false;
  public boolean useAccCase = false;
  public boolean useInna = false;
  public boolean useConcord = false;
  public boolean useFirstNgram = false;
  public boolean useLastNgram = false;
  public boolean collapseNN = false;
  public boolean useConjBreak = false;
  public boolean useAuxPairs = false;
  public boolean usePPVBPairs = false;
  public boolean useAnnexing = false;
  public boolean useTemporalNN = false;
  public boolean usePath = false;
  public boolean innaPPAttach = false;
  public boolean markProperNN = false;
  public boolean markMasdar = false;
  public boolean useSVO = false;

  public int numTags = 3;
  public boolean useTagsCpC = false;
  public boolean useTagsCpCp2C = false;
  public boolean useTagsCpCp2Cp3C = false;
  public boolean useTagsCpCp2Cp3Cp4C = false;

  public double l1reg = 0.0;

  // truecaser flags:
  public String mixedCaseMapFile = "";
  public String auxTrueCaseModels = "";


  // more flags inspired by Zhang and Johnson 2003
  public boolean use2W = false;
  public boolean useLC = false;
  public boolean useYetMoreCpCShapes = false;

  // "ADD VARIABLES ABOVE HERE"



  public SeqClassifierFlags() {
  }

  /** Create a new SeqClassifierFlags object and initialize it
   *  using values in the Properties object.
   *  The properties are printed to stderr as it works.
   *
   *  @param props The properties object used for initialization
   */
  public SeqClassifierFlags(Properties props) {
    setProperties(props, true);
  }

  /**
   * Initialize this object using values in Properties object.
   * The properties are printed to stderr as it works.
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
    StringBuilder sb = new StringBuilder(stringRep);
    for (Enumeration e = props.propertyNames(); e.hasMoreElements(); ) {
      String key = (String) e.nextElement();
      String val = props.getProperty(key);
      if (! (key.length() == 0 && val.length() == 0)) {
        if (printProps) {
          System.err.println(key+ '=' +val);
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
          // should this be set?? maxNGramLeng = 6;  No (to get best score).
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
          disjunctionWidth = 4;  // clearly optimal for CoNLL
          useBoundarySequences = true;
          useLemmas = true;  // no-op except for German
          usePrevNextLemmas = true;  // no-op except for German
          inputEncoding="iso-8859-1"; // needed for CoNLL German files
          // opt
          useQN = true;
          QNsize = 15;
        }
      } else if (key.equalsIgnoreCase("conllNoTags")) {
        if (Boolean.parseBoolean(val)) {
          readerAndWriter = "edu.stanford.nlp.sequences.ColumnDocumentReaderAndWriter";
          //          trainMap=testMap="word=0,answer=1";
          map="word=0,answer=1";
          useObservedSequencesOnly = true;
          // useClassFeature = true;
          useLongSequences = true;
          //useTaggySequences = true;
          useNGrams = true;
          usePrev = true;
          useNext = true;
          //useTags = true;
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
          //useOccurrencePatterns = true;
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
          //useLemmas = true;  // no-op except for German
          //usePrevNextLemmas = true;  // no-op except for German
          inputEncoding="iso-8859-1";
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
      } else if (key.equalsIgnoreCase("useEntityRule")){
        useEntityRule = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useOrdinal")){
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
      } else if (key.equalsIgnoreCase("useSum")) {
        useSum = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("verboseMode")) {
        verboseMode = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("tolerance")) {
        tolerance = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("maxIterations")) {
        maxIterations = Integer.parseInt(val);
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
        System.err.println("You are using an outdated flag: -documentReader "+val);
        System.err.println("Please use -readerAndWriter instead.");
      } else if (key.equalsIgnoreCase("deleteBlankLines")) {
        deleteBlankLines = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("answerFile")) {
        answerFile = val;
      } else if (key.equalsIgnoreCase("altAnswerFile")) {
        altAnswerFile = val;
      } else if (key.equalsIgnoreCase("loadClassifier")) {
        loadClassifier = val;
      } else if (key.equalsIgnoreCase("loadTextClassifier")) {
        loadTextClassifier = val;
      } else if (key.equalsIgnoreCase("loadJarClassifier")) {
        loadJarClassifier = val;
      } else if (key.equalsIgnoreCase("loadAuxClassifier")) {
        loadAuxClassifier=val;
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
        if (gazettes == null) { gazettes = new ArrayList<String>(); } // for after deserialization, as gazettes is transient
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
        splitDocuments = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("featureWeightThreshold")) {
        featureWeightThreshold = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("backgroundSymbol")) {
        backgroundSymbol = val;
      } else if (key.equalsIgnoreCase("featureFactory")) {
        featureFactory = val;
        if (featureFactory.equalsIgnoreCase("SuperSimpleFeatureFactory")) {
          featureFactory = "edu.stanford.nlp.sequences.SuperSimpleFeatureFactory";
        } else if (featureFactory.equalsIgnoreCase("NERFeatureFactory")) {
          featureFactory = "edu.stanford.nlp.ie.NERFeatureFactory";
        } else if (featureFactory.equalsIgnoreCase("GazNERFeatureFactory")) {
          featureFactory = "edu.stanford.nlp.sequences.GazNERFeatureFactory";
        } else if (featureFactory.equalsIgnoreCase("IncludeAllFeatureFactory")) {
          featureFactory = "edu.stanford.nlp.sequences.IncludeAllFeatureFactory";
        }

      } else if (key.equalsIgnoreCase("printXML")) {
        printXML = Boolean.parseBoolean(val);

      } else if (key.equalsIgnoreCase("useSeenFeaturesOnly")) {
        useSeenFeaturesOnly = Boolean.parseBoolean(val);

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
        /*Dictionary*/
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
        /*N-gram flags*/
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
        /*affix flags*/
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
        /*POS flags*/
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
        /*ASBC and HK */
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
          System.err.println("unknown annealingType: "+val+".  Please use linear|exp|exponential");
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
        if (val.length() > 0 && ! "true".equals(val) && ! "null".equals(val) && ! "false".equals("val")) {
          dictionary = val;
        } else {
          dictionary = null;
        }
      } else if (key.equalsIgnoreCase("serDictionary")) {
        // don't set if empty string or spaces or true: revert it to null
        // special case so can empty out dictionary list on command line!
        val = val.trim();
        if (val.length() > 0 && ! "true".equals(val) && ! "null".equals(val) && ! "false".equals("val")) {
          serializedDictionary = val;
        } else {
          serializedDictionary = null;
        }
      } else if (key.equalsIgnoreCase("dictionary2")) {
        // don't set if empty string or spaces or true: revert it to null
        // special case so can empty out dictionary list on command line!
        val = val.trim();
        if (val.length() > 0 && ! "true".equals(val) && ! "null".equals(val) && ! "false".equals("val")) {
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
      } else if (key.equalsIgnoreCase("trainDirs")){
        trainDirs = val;
      } else if (key.equalsIgnoreCase("testDirs")){
        testDirs = val;
      } else if (key.equalsIgnoreCase("testFiles")) {
        testFiles = val;
      } else if (key.equalsIgnoreCase("usePrediction2")) {
        usePrediction2 = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useObservedFeaturesOnly")) {
        useObservedFeaturesOnly = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("iobWrapper")) {
        iobWrapper = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useDistSim")) {
        useDistSim = Boolean.parseBoolean(val);
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
      } else if (key.equalsIgnoreCase("useScaledSGD")){
        useScaledSGD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("scaledSGDMethod")){
        scaledSGDMethod = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("tuneSGD")){
        tuneSGD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("StochasticCalculateMethod")) {
        if (val.equalsIgnoreCase("AlgorithmicDifferentiation")){
          stochasticMethod = StochasticCalculateMethods.AlgorithmicDifferentiation;
        } else if(val.equalsIgnoreCase("IncorporatedFiniteDifference")){
          stochasticMethod = StochasticCalculateMethods.IncorporatedFiniteDifference ;
        } else if(val.equalsIgnoreCase("ExternalFinitedifference")){
          stochasticMethod = StochasticCalculateMethods.ExternalFiniteDifference ;
        }
      } else if (key.equalsIgnoreCase("initialGain")) {
        initialGain = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("stochasticBatchSize")){
        stochasticBatchSize = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("SGD2QNhessSamples")){
        SGD2QNhessSamples = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useSGD")) {
        useSGD = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSGDtoQN")){
        useSGDtoQN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("SGDPasses")){
        SGDPasses = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("QNPasses")){
        QNPasses = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("gainSGD")) {
        gainSGD = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("useHybrid")){
        useHybrid = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("hybridCutoffIteration")){
        hybridCutoffIteration = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useStochasticQN")){
        useStochasticQN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("outputIterationsToFile")){
        outputIterationsToFile = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("testObjFunction")){
        testObjFunction = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("testVariance")){
        testVariance = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("CRForder")){
        CRForder = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("CRFwindow")){
        CRFwindow = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("testHessSamples")){
        testHessSamples = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("estimateInitial")){
        estimateInitial = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("printLabelValue")){
        printLabelValue = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("searchGraphPrefix")){
        searchGraphPrefix = val;
      } else if (key.equalsIgnoreCase("searchGraphPrune")){
        searchGraphPrune = Double.parseDouble(val);
      } else if (key.equalsIgnoreCase("kBest")){
        useKBest = true;
        kBest = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useRobustQN")){
        useRobustQN = true;
      } else if (key.equalsIgnoreCase("combo")){
        combo = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("verboseForTrueCasing")) {
        verboseForTrueCasing = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("trainHierarchical")){
        trainHierarchical = val;
      } else if (key.equalsIgnoreCase("domain")){
        domain = val;
      } else if(key.equalsIgnoreCase("baseline")) {
        baseline = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("doFE")) {
        doFE = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("restrictLabels")) {
        restrictLabels = Boolean.parseBoolean(val);
      } else if(key.equalsIgnoreCase("transferSigmas")){
        transferSigmas = val;
      } else if (key.equalsIgnoreCase("announceObjectBankEntries")) {
        announceObjectBankEntries = true;
      } else if (key.equalsIgnoreCase("usePos")) {
        usePos = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAgreement")) {
        useAgreement = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAccCase")) {
        useAccCase = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useInna")) {
        useInna = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useConcord")) {
        useConcord = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useFirstNgram")) {
        useFirstNgram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useLastNgram")) {
        useLastNgram = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("collapseNN")) {
        collapseNN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTagsCpC")) {
        useTagsCpC = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTagsCpCp2C")) {
        useTagsCpCp2C = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTagsCpCp2Cp3C")) {
        useTagsCpCp2Cp3C = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTagsCpCp2Cp3Cp4C")) {
        useTagsCpCp2Cp3Cp4C = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("numTags")) {
        numTags = Integer.parseInt(val);
      } else if (key.equalsIgnoreCase("useConjBreak")) {
        useConjBreak = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAuxPairs")) {
        useAuxPairs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePPVBPairs")) {
        usePPVBPairs = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useAnnexing")) {
        useAnnexing = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useTemporalNN")) {
        useTemporalNN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("markProperNN")) {
        markProperNN = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("usePath")) {
        usePath = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("markMasdar")) {
        markMasdar = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("innaPPAttach")) {
        innaPPAttach = Boolean.parseBoolean(val);
      } else if (key.equalsIgnoreCase("useSVO")) {
        useSVO = Boolean.parseBoolean(val);
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

      //ADD VALUE ABOVE HERE
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

    if (combo) { splitDocuments = false; }

    stringRep = sb.toString();
  } // end setProperties()

  /** Print the properties specified by this object.
   *  @return A String describing the properties specified by this object.
   */
  @Override
  public String toString() {
    return stringRep;
  }

} // end class SeqClassifierFlags
