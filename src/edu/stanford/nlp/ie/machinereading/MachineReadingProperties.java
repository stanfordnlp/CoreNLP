package edu.stanford.nlp.ie.machinereading;

import java.util.logging.Logger;

import edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory;
import edu.stanford.nlp.util.ArgumentParser.Option;

public class MachineReadingProperties {

  @Option(name="logger", gloss="Static logger for this entire class")
  public static Logger logger = Logger.getLogger(MachineReading.class.getName());

  /*
   * general options
   */

  @Option(name = "datasetReaderClass", gloss = "which GenericDataSetReader to use (needs to match the corpus in question)", required = true)
  public static Class<GenericDataSetReader> datasetReaderClass;


  @Option(name = "datasetAuxReaderClass", gloss = "which GenericDataSetReader to use for aux data set (needs to match the corpus in question)")
  public static Class<GenericDataSetReader> datasetAuxReaderClass;

  @Option(name = "useNewHeadFinder", gloss = "If false, use the original head (and worse) finding mechanism in GenericDataSetReader.  This option is primarily around for legacy purposes.")
  public static boolean useNewHeadFinder = true;

  @Option(name = "readerLogLevel", gloss = "verbosity of the corpus reader")
  public static String readerLogLevel = "SEVERE";

  @Option(name = "serializeCorpora", gloss = "if false, we do not attempt to serialize the train/test corpora after reading")
  public static boolean serializeCorpora = true;

  @Option(name = "forceGenerationOfIndexSpans", gloss = "if true (default), regenerate span annotations for trees")
  public static boolean forceGenerationOfIndexSpans = true;

  /*
   * entity extraction options
   */


  @Option(name="serializedEntityExtractorPath",gloss="where to store/load the serialized entity extraction model")
  protected static String serializedEntityExtractorPath = "";

  @Option(name="serializedEntityExtractionResults",gloss="where to store the serialized sentences containing the results of entity extraction")
  protected static String serializedEntityExtractionResults;

  // TODO this option is temporary and should be removed when (if?) gazetteers get
  // folded into feature factories

  @Option(name = "entityGazetteerPath", gloss = "location of entity gazetteer file (if you're using one) -- this is a temporary option")
  public static String entityGazetteerPath;

  @Option(name = "entityClassifier", gloss = "entity extractor class to use")
  public static Class<BasicEntityExtractor> entityClassifier = edu.stanford.nlp.ie.machinereading.BasicEntityExtractor.class;

  @Option(name = "entityResultsPrinters", gloss = "comma-separated list of ResultsPrinter subclasses to use for printing the results of entity extraction")
  public static String entityResultsPrinters = "";

  /*
   * relation extraction options
   */

  @Option(name="serializedRelationExtractorPath",gloss="where to store/load the serialized relation extraction model")
  protected static String serializedRelationExtractorPath = null;

  @Option(name="serializedRelationExtractionResults",gloss="where to store the serialized sentences containing the results of relation extraction")
  protected static String serializedRelationExtractionResults = null;

  @Option(name = "relationFeatureFactoryClass", gloss = "FeatureFactory class to use for generating features from relations for relation extraction")
  public static Class<? extends RelationFeatureFactory> relationFeatureFactoryClass = edu.stanford.nlp.ie.machinereading.BasicRelationFeatureFactory.class;

  @Option(name = "relationMentionFactoryClass", gloss = "relation mention factory class to use.")
  public static Class<RelationMentionFactory> relationMentionFactoryClass =  edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory.class;

  @Option(name = "relationFeatures", gloss = "comma-separated list of feature types to generate for relation extraction.")
  public static String relationFeatures = "all";

  @Option(name = "relationResultsPrinters", gloss = "comma-separated list of ResultsPrinter subclasses to use for printing the results of relation extraction")
  public static String relationResultsPrinters = "edu.stanford.nlp.ie.machinereading.RelationExtractorResultsPrinter";

  @Option(name = "trainRelationsUsingPredictedEntities", gloss = "if true, the relation extraction model trains using predicted rather than gold entity mentions")
  public static boolean trainRelationsUsingPredictedEntities = false;

  @Option(name = "testRelationsUsingPredictedEntities", gloss = "if true, the relation extraction model is evaluated using predicted rather than gold entity mentions.")
  public static boolean testRelationsUsingPredictedEntities = false;

  @Option(name = "createUnrelatedRelations", gloss = "If true, it creates automatically negative examples by generating all combinations between EntityMentions in a sentence")
  public static boolean createUnrelatedRelations = true;

  @Option(name = "doNotLexicalizeFirstArg", gloss = "If true, it does not create any lexicalized features from the first argument (needed for KBP)")
  public static boolean doNotLexicalizeFirstArg = false;

  // TODO: temporary NFL deadline based hack. remove it.
  @Option(name = "useRelationExtractionModelMerging", gloss = "If true, the relation extractor will use ExtractorMerger for annotation (not training)")
  public static boolean useRelationExtractionModelMerging = false;

  @Option(name = "relationsToSkipDuringTraining", gloss = "comma-separated list relation types to skip during training")
  public static String relationsToSkipDuringTraining ="";

  @Option(name = "relationExtractionPostProcessorClass", gloss = "additional (probably domain-dependent) annotator to postprocess relations")
  public static Class<Extractor> relationExtractionPostProcessorClass;

  @Option(name = "relationClassifier", gloss = "relation extractor class to use")
  public static Class<? extends BasicRelationExtractor> relationClassifier = edu.stanford.nlp.ie.machinereading.BasicRelationExtractor.class;

  /*
   * event extraction options
   */

  @Option(name="serializedEventExtractorPath",gloss="where to store/load the serialized event extraction model")
  protected static String serializedEventExtractorPath = "";

  @Option(name="serializedEventExtractionResults",gloss="where to store the serialized sentences containing the results of event extraction")
  protected static String serializedEventExtractionResults;

  @Option(name = "eventResultsPrinters", gloss = "comma-separated list of ResultsPrinter subclasses to use for printing the results of event extraction")
  public static String eventResultsPrinters = "";

  @Option(name = "trainEventsUsingPredictedEntities", gloss = "if true, the event extraction model trains using predicted rather than gold entity mentions")
  public static boolean trainEventsUsingPredictedEntities = false;

  @Option(name = "testEventsUsingPredictedEntities", gloss = "if true, the event extraction model is evaluated using predicted rather than gold entity mentions")
  public static boolean testEventsUsingPredictedEntities = false;

  /*
   * global, domain-dependent options
   */

  @Option(name = "consistencyCheck", gloss = "consistency checker class to use")
  public static Class<Extractor> consistencyCheck;


  /*
   * training options
   */

  @Option(name = "trainPath", gloss = " path to the training file/directory")
  protected static String trainPath;

  @Option(name = "auxDataPath", gloss = "path to the aux training file/directory")
  protected static String auxDataPath;

  @Option(name = "serializedTrainingSentencesPath", gloss = " where to store the serialized training sentences objects", required = true)
  protected static String serializedTrainingSentencesPath;

  @Option(name = "serializedAuxTrainingSentencesPath", gloss = "where to store the serialized aux training sentences objects")
  protected static String serializedAuxTrainingSentencesPath;

  @Option(name = "loadModel", gloss = "if true, load a serialized model rather than training a new one")
  protected static boolean loadModel = false;

  @Option(name = "trainUsePipelineNER", gloss = "during training, use NER generated by the CoreNLP pipeline")
  public static boolean trainUsePipelineNER = false;

  /**
   * evaluation options (ignored if trainOnly is true)
   */


  @Option(name = "trainOnly", gloss = "if true, don't run evaluation (implies forceRetraining)")
  protected static boolean trainOnly = false;

  @Option(name = "testPath", gloss = "path to the testing file/directory")
  protected static String testPath;

  @Option(name = "serializedTestSentencesPath", gloss = "where to store the serialized test sentence objects")
  protected static String serializedTestSentencesPath;

  @Option(name="extractEntities",gloss="whether to extract entities, or use gold-standard entities for relation/event extraction")
  protected static boolean extractEntities = true;

  @Option(name="extractRelations",gloss="whether we should extract relations")
  protected static boolean extractRelations = true;

  @Option(name="extractEvents",gloss="whether we should extract events")
  protected static boolean extractEvents = true;


  /*
   * cross-validation options
   */
  @Option(name="crossValidate",gloss="if true, run cross-validation")
  protected static boolean crossValidate = false;

  @Option(name = "kfold", gloss = "number of partitions in training data for cross validation")
  public static int kfold = 5;

  @Option(name = "percentageOfTrain", gloss = "Pct of train partition to use for training (e.g., for RELMS experiment); default all = 1.0")
  public static double percentageOfTrain = 1.0;

  /**
   * Additional features, may not necessarily be used in the public release
   */
  @Option(name = "featureSimilarityThreshold")
  public static double featureSimilarityThreshold = 0.2;

  @Option(name = "computeFeatSimilarity")
  public static boolean computeFeatSimilarity = true;

  @Option(name = "featureSelectionNumFeaturesRatio")
  public static double featureSelectionNumFeaturesRatio = 0.7;

  @Option(name = "L1Reg")
  public static boolean L1Reg = false;

  @Option(name = "L2Reg")
  public static boolean L2Reg = true;

  @Option(name = "L1RegLambda")
  public static double L1RegLambda = 1.0;

  private MachineReadingProperties() {} // class of static option variables.

}
