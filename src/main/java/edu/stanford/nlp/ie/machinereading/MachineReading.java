package edu.stanford.nlp.ie.machinereading;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import edu.stanford.nlp.ie.machinereading.structure.*;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.EntityMentionsAnnotation;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Main driver for Machine Reading training, annotation, and evaluation. Does
 * entity, relation, and event extraction for all corpora.
 *
 * This code has been adapted for 4 domains, all defined in the edu.stanford.nlp.ie.machinereading.domains package.
 * For each domain, you need a properties file that is the only command line parameter for MachineReading.
 * Minimally, for each domain you need to define a reader class that extends the GenericDataSetReader class
 * and overrides the public Annotation read(String path) method.
 *
 * How to run: java edu.stanford.nlp.ie.machinereading.MachineReading -arguments propertiesFile
 *
 * This method creates an Annotation with additional objects per sentence: EntityMentions and RelationMentions.
 * Using these objects, the classifiers that get called from MachineReading train entity and relation extractors.
 * The simplest example domain currently is in edu.stanford.nlp.ie.machinereading.domains.roth,
 * which is a simple entity and relation extraction using a dataset created by Dan Roth. The properties file for the domain is at
 * projects/more/src/edu/stanford/nlp/ie/machinereading/domains/roth/roth.properties
 *
 * @author David McCLosky
 * @author mrsmith
 * @author Mihai
 */
public class MachineReading  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(MachineReading.class);

  /** Store command-line args so they can be passed to other classes */
  private final String[] args;

  /*
   * class attributes
   */
  private GenericDataSetReader reader;
  private GenericDataSetReader auxReader;


  private Extractor entityExtractor;
  // TODO could add an entityExtractorPostProcessor if we need one
  private Extractor relationExtractor;
  private Extractor relationExtractionPostProcessor;
  private Extractor eventExtractor;
  private Extractor consistencyChecker;

  private boolean forceRetraining;
  private boolean forceParseSentences;


  /**
   * Array of pairs of datasets (training, testing)
   * If cross validation is enabled, the length of this array is the number of folds; otherwise it is 1
   * The first element in each pair is the training corpus; the second is testing
   */
  private Pair<Annotation, Annotation> [] datasets;

  /**
   * Stores the predictions of the extractors
   * The first index is the partition number (of length 1 is cross validation is not enabled)
   * The second index is the task: 0 - entities, 1 - relations, 2 - events
   * Note: we need to store separate predictions per task because they may not be compatible with each other.
   *       For example, we may have predicted entities in task 0 but use gold entities for task 1.
   */
  private Annotation [][] predictions;

  private Set<ResultsPrinter> entityResultsPrinterSet;
  private Set<ResultsPrinter> relationResultsPrinterSet;
  @SuppressWarnings("unused")
  private Set<ResultsPrinter> eventResultsPrinterSet;

  private static final int ENTITY_LEVEL = 0;
  private static final int RELATION_LEVEL = 1;
  private static final int EVENT_LEVEL = 2;


  public static void main(String[] args) throws Exception {
    MachineReading mr = makeMachineReading(args);
    mr.run();
  }

  public static void setLoggerLevel(Level level) {
    setConsoleLevel(Level.FINEST);
    MachineReadingProperties.logger.setLevel(level);
  }

  public static void setConsoleLevel(Level level) {
    // get the top Logger:
    Logger topLogger = java.util.logging.Logger.getLogger("");

    // Handler for console (reuse it if it already exists)
    Handler consoleHandler = null;
    // see if there is already a console handler
    for (Handler handler : topLogger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        // found the console handler
        consoleHandler = handler;
        break;
      }
    }

    if (consoleHandler == null) {
      // there was no console handler found, create a new one
      consoleHandler = new ConsoleHandler();
      topLogger.addHandler(consoleHandler);
    }
    // set the console handler level:
    consoleHandler.setLevel(level);
    consoleHandler.setFormatter(new SimpleFormatter());
  }

  /**
   * Use the makeMachineReading* methods to create MachineReading objects!
   */
  private MachineReading(String [] args) {
    this.args = args;
  }

  protected MachineReading() {
    this.args = StringUtils.EMPTY_STRING_ARRAY;
  }

  /**
   * Creates a MR object to be used only for annotation purposes (no training)
   * This is needed in order to integrate MachineReading with BaselineNLProcessor
   */
  public static MachineReading makeMachineReadingForAnnotation(
          GenericDataSetReader reader,
          Extractor entityExtractor,
          Extractor relationExtractor,
          Extractor eventExtractor,
          Extractor consistencyChecker,
          Extractor relationPostProcessor,
          boolean testRelationsUsingPredictedEntities,
          boolean verbose) {
    MachineReading mr = new MachineReading();

    // readers needed to assign syntactic heads to predicted entities
    mr.reader = reader;
    mr.auxReader = null;

    // no results printers needed
    mr.entityResultsPrinterSet = new HashSet<>();
    mr.setRelationResultsPrinterSet(new HashSet<>());

    // create the storage for the generated annotations
    mr.predictions = new Annotation[3][1];

    // create the entity/relation classifiers
    mr.entityExtractor = entityExtractor;
    MachineReadingProperties.extractEntities = entityExtractor != null;
    mr.relationExtractor = relationExtractor;
    MachineReadingProperties.extractRelations = relationExtractor != null;
    MachineReadingProperties.testRelationsUsingPredictedEntities = testRelationsUsingPredictedEntities;
    mr.eventExtractor = eventExtractor;
    MachineReadingProperties.extractEvents = eventExtractor != null;
    mr.consistencyChecker = consistencyChecker;
    mr.relationExtractionPostProcessor = relationPostProcessor;

    Level level = verbose ? Level.FINEST : Level.SEVERE;
    if (entityExtractor != null)
      entityExtractor.setLoggerLevel(level);
    if (mr.relationExtractor != null)
      mr.relationExtractor.setLoggerLevel(level);
    if (mr.eventExtractor != null)
      mr.eventExtractor.setLoggerLevel(level);

    return mr;
  }

  public static MachineReading makeMachineReading(String [] args) throws IOException {
    // install global parameters
    MachineReading mr = new MachineReading(args);
    //TODO:
    ArgumentParser.fillOptions(MachineReadingProperties.class, args);
    //Arguments.parse(args, mr);
    log.info("PERCENTAGE OF TRAIN: " + MachineReadingProperties.percentageOfTrain);

    // convert args to properties
    Properties props = StringUtils.argsToProperties(args);
    if (props == null) {
      throw new RuntimeException("ERROR: failed to find Properties in the given arguments!");
    }

    String logLevel = props.getProperty("logLevel", "INFO");
    setLoggerLevel(Level.parse(logLevel.toUpperCase()));

    // install reader specific parameters
    GenericDataSetReader reader = mr.makeReader(props);
    GenericDataSetReader auxReader = mr.makeAuxReader();
    Level readerLogLevel = Level.parse(MachineReadingProperties.readerLogLevel.toUpperCase());
    reader.setLoggerLevel(readerLogLevel);
    if (auxReader != null) {
      auxReader.setLoggerLevel(readerLogLevel);
    }
    log.info("The reader log level is set to " + readerLogLevel);
    //Execution.fillOptions(GenericDataSetReaderProps.class, args);
    //Arguments.parse(args, reader);

    // create the pre-processing pipeline
    StanfordCoreNLP pipe = new StanfordCoreNLP(props, false);
    reader.setProcessor(pipe);
    if (auxReader != null) {
      auxReader.setProcessor(pipe);
    }

    // create the results printers
    mr.makeResultsPrinters(args);

    return mr;
  }

  /**
   * Performs extraction. This will train a new extraction model and evaluate
   * the model on the test set. Depending on the MachineReading instance's
   * parameters, it may skip training if a model already exists or skip
   * evaluation.
   *
   * returns results string, can be compared in a utest
   */
  public List<String> run() throws Exception {
    this.forceRetraining = ! MachineReadingProperties.loadModel;

    if (MachineReadingProperties.trainOnly) {
      this.forceRetraining= true;
    }
    List<String> retMsg = new ArrayList<>();
    boolean haveSerializedEntityExtractor = serializedModelExists(MachineReadingProperties.serializedEntityExtractorPath);
    boolean haveSerializedRelationExtractor = serializedModelExists(MachineReadingProperties.serializedRelationExtractorPath);
    boolean haveSerializedEventExtractor = serializedModelExists(MachineReadingProperties.serializedEventExtractorPath);
    Annotation training = null;
    Annotation aux = null;
    if ((MachineReadingProperties.extractEntities && !haveSerializedEntityExtractor) ||
            (MachineReadingProperties.extractRelations && !haveSerializedRelationExtractor) ||
            (MachineReadingProperties.extractEvents && !haveSerializedEventExtractor) ||
            this.forceRetraining|| MachineReadingProperties.crossValidate){
      // load training sentences
      training = loadOrMakeSerializedSentences(MachineReadingProperties.trainPath, reader, new File(MachineReadingProperties.serializedTrainingSentencesPath));
      if (auxReader != null) {
        MachineReadingProperties.logger.severe("Reading auxiliary dataset from " + MachineReadingProperties.auxDataPath + "...");
        aux = loadOrMakeSerializedSentences(MachineReadingProperties.auxDataPath, auxReader, new File(
                MachineReadingProperties.serializedAuxTrainingSentencesPath));
        MachineReadingProperties.logger.severe("Done reading auxiliary dataset.");
      }
    }

    Annotation testing = null;
    if (!MachineReadingProperties.trainOnly && !MachineReadingProperties.crossValidate) {
      // load test sentences
      File serializedTestSentences = new File(MachineReadingProperties.serializedTestSentencesPath);
      testing = loadOrMakeSerializedSentences(MachineReadingProperties.testPath, reader, serializedTestSentences);
    }

    //
    // create the actual datasets to be used for training and annotation
    //
    makeDataSets(training, testing, aux);

    //
    // process (training + annotate) one partition at a time
    //
    for(int partition = 0; partition < datasets.length; partition ++){
      assert(datasets.length > partition);
      assert(datasets[partition] != null);
      assert(MachineReadingProperties.trainOnly || datasets[partition].second() != null);

      // train all models
      train(datasets[partition].first(), (MachineReadingProperties.crossValidate ? partition : -1));
      // annotate using all models
      if(! MachineReadingProperties.trainOnly){
        MachineReadingProperties.logger.info("annotating partition " + partition );
        annotate(datasets[partition].second(), (MachineReadingProperties.crossValidate ? partition: -1));
      }
    }

    //
    // now report overall results
    //
    if(! MachineReadingProperties.trainOnly){
      // merge test sets for the gold data
      Annotation gold = new Annotation("");
      for (Pair<Annotation, Annotation> dataset : datasets)
        AnnotationUtils.addSentences(gold, dataset.second().get(SentencesAnnotation.class));

      // merge test sets with predicted annotations
      Annotation[] mergedPredictions = new Annotation[3];
      assert(predictions != null);
      for (int taskLevel = 0; taskLevel < mergedPredictions.length; taskLevel++) {
        mergedPredictions[taskLevel] = new Annotation("");
        for(int fold = 0; fold < predictions[taskLevel].length; fold ++){
          if (predictions[taskLevel][fold] == null) continue;
          AnnotationUtils.addSentences(mergedPredictions[taskLevel], predictions[taskLevel][fold].get(CoreAnnotations.SentencesAnnotation.class));
        }
      }
      //
      // evaluate all tasks: entity, relation, and event recognition
      //
      if(MachineReadingProperties.extractEntities && ! entityResultsPrinterSet.isEmpty()){
        retMsg.addAll(printTask("entity extraction", entityResultsPrinterSet, gold, mergedPredictions[ENTITY_LEVEL]));
      }

      if(MachineReadingProperties.extractRelations && ! getRelationResultsPrinterSet().isEmpty()){
        retMsg.addAll(printTask("relation extraction", getRelationResultsPrinterSet(), gold, mergedPredictions[RELATION_LEVEL]));
      }

      //
      // Save the sentences with the predicted annotations
      //
      if (MachineReadingProperties.extractEntities && MachineReadingProperties.serializedEntityExtractionResults != null)
        IOUtils.writeObjectToFile(mergedPredictions[ENTITY_LEVEL], MachineReadingProperties.serializedEntityExtractionResults);
      if (MachineReadingProperties.extractRelations && MachineReadingProperties.serializedRelationExtractionResults != null)
        IOUtils.writeObjectToFile(mergedPredictions[RELATION_LEVEL],MachineReadingProperties.serializedRelationExtractionResults);
      if (MachineReadingProperties.extractEvents && MachineReadingProperties.serializedEventExtractionResults != null)
        IOUtils.writeObjectToFile(mergedPredictions[EVENT_LEVEL],MachineReadingProperties.serializedEventExtractionResults);

    }

    return retMsg;
  }

  private static List<String> printTask(String taskName, Set<ResultsPrinter> printers, Annotation gold, Annotation pred) {
    List<String> retMsg = new ArrayList<>();
    for (ResultsPrinter rp : printers){
      String msg = rp.printResults(gold, pred);
      retMsg.add(msg);
      MachineReadingProperties.logger.severe("Overall " + taskName + " results, using printer " + rp.getClass() + ":\n" + msg);
    }
    return retMsg;
  }

  protected void train(Annotation training, int partition) throws Exception {
    //
    // train entity extraction
    //
    if (MachineReadingProperties.extractEntities) {
      MachineReadingProperties.logger.info("Training entity extraction model(s)");
      if (partition != -1) MachineReadingProperties.logger.info("In partition #" + partition);
      String modelName = MachineReadingProperties.serializedEntityExtractorPath;
      if (partition != -1) modelName += "." + partition;
      File modelFile = new File(modelName);

      MachineReadingProperties.logger.fine("forceRetraining = " + this.forceRetraining+ ", modelFile.exists = " + modelFile.exists());
      if(! this.forceRetraining&& modelFile.exists()){
        MachineReadingProperties.logger.info("Loading entity extraction model from " + modelName + " ...");
        entityExtractor = BasicEntityExtractor.load(modelName, MachineReadingProperties.entityClassifier, false);
      } else {
        MachineReadingProperties.logger.info("Training entity extraction model...");
        entityExtractor = makeEntityExtractor(MachineReadingProperties.entityClassifier, MachineReadingProperties.entityGazetteerPath);
        entityExtractor.train(training);
        MachineReadingProperties.logger.info("Serializing entity extraction model to " + modelName + " ...");
        entityExtractor.save(modelName);
      }
    }

    //
    // train relation extraction
    //
    if (MachineReadingProperties.extractRelations) {
      MachineReadingProperties.logger.info("Training relation extraction model(s)");
      if (partition != -1)
        MachineReadingProperties.logger.info("In partition #" + partition);
      String modelName = MachineReadingProperties.serializedRelationExtractorPath;
      if (partition != -1)
        modelName += "." + partition;

      if (MachineReadingProperties.useRelationExtractionModelMerging) {
        String[] modelNames = MachineReadingProperties.serializedRelationExtractorPath.split(",");
        if (partition != -1) {
          for (int i = 0; i < modelNames.length; i++) {
            modelNames[i] += "." + partition;
          }
        }

        relationExtractor = ExtractorMerger.buildRelationExtractorMerger(modelNames);
      } else if (!this.forceRetraining&& new File(modelName).exists()) {
        MachineReadingProperties.logger.info("Loading relation extraction model from " + modelName + " ...");
        //TODO change this to load any type of BasicRelationExtractor
        relationExtractor = BasicRelationExtractor.load(modelName);
      } else {
        RelationFeatureFactory rff = makeRelationFeatureFactory(MachineReadingProperties.relationFeatureFactoryClass, MachineReadingProperties.relationFeatures, MachineReadingProperties.doNotLexicalizeFirstArg);
        ArgumentParser.fillOptions(rff, args);

        Annotation predicted = null;
        if (MachineReadingProperties.trainRelationsUsingPredictedEntities) {
          // generate predicted entities
          assert(entityExtractor != null);
          predicted = AnnotationUtils.deepMentionCopy(training);
          entityExtractor.annotate(predicted);
          for (ResultsPrinter rp : entityResultsPrinterSet){
            String msg = rp.printResults(training, predicted);
            MachineReadingProperties.logger.info("Training relation extraction using predicted entitities: entity scores using printer " + rp.getClass() + ":\n" + msg);
          }

          // change relation mentions to use predicted entity mentions rather than gold ones
          try {
            changeGoldRelationArgsToPredicted(predicted);
          } catch (Exception e) {
            // we may get here for unknown EntityMentionComparator class
            throw new RuntimeException(e);
          }
        }

        Annotation dataset;
        if (MachineReadingProperties.trainRelationsUsingPredictedEntities) {
          dataset = predicted;
        } else {
          dataset = training;
        }

        Set<String> relationsToSkip = new HashSet<>(StringUtils.split(MachineReadingProperties.relationsToSkipDuringTraining, ","));
        List<List<RelationMention>> backedUpRelations = new ArrayList<>();
        if (relationsToSkip.size() > 0) {
          // we need to backup the relations since removeSkippableRelations modifies dataset in place and we can't duplicate CoreMaps safely (or can we?)
          for (CoreMap sent : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
            List<RelationMention> relationMentions = sent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
            backedUpRelations.add(relationMentions);
          }

          removeSkippableRelations(dataset, relationsToSkip);
        }

        //relationExtractor = new BasicRelationExtractor(rff, MachineReadingProperties.createUnrelatedRelations, makeRelationMentionFactory(MachineReadingProperties.relationMentionFactoryClass));
        relationExtractor = makeRelationExtractor(MachineReadingProperties.relationClassifier, rff, MachineReadingProperties.createUnrelatedRelations,
                makeRelationMentionFactory(MachineReadingProperties.relationMentionFactoryClass));
        ArgumentParser.fillOptions(relationExtractor, args);
        //Arguments.parse(args,relationExtractor);
        MachineReadingProperties.logger.info("Training relation extraction model...");
        relationExtractor.train(dataset);
        MachineReadingProperties.logger.info("Serializing relation extraction model to " + modelName + " ...");
        relationExtractor.save(modelName);

        if (relationsToSkip.size() > 0) {
          // restore backed up relations into dataset
          int sentenceIndex = 0;

          for (CoreMap sentence : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
            List<RelationMention> relationMentions = backedUpRelations.get(sentenceIndex);
            sentence.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, relationMentions);
            sentenceIndex++;
          }
        }
      }
    }

    //
    // train event extraction -- currently just works with MSTBasedEventExtractor
    //
    if (MachineReadingProperties.extractEvents) {
      MachineReadingProperties.logger.info("Training event extraction model(s)");
      if (partition != -1) MachineReadingProperties.logger.info("In partition #" + partition);
      String modelName = MachineReadingProperties.serializedEventExtractorPath;
      if (partition != -1) modelName += "." + partition;
      File modelFile = new File(modelName);

      if(!this.forceRetraining&& modelFile.exists()) {
        MachineReadingProperties.logger.info("Loading event extraction model from " + modelName + " ...");
        Method mstLoader = (Class.forName("MSTBasedEventExtractor")).getMethod("load", String.class);
        eventExtractor = (Extractor) mstLoader.invoke(null, modelName);
      } else {
        Annotation predicted = null;
        if (MachineReadingProperties.trainEventsUsingPredictedEntities) {
          // generate predicted entities
          assert(entityExtractor != null);
          predicted = AnnotationUtils.deepMentionCopy(training);
          entityExtractor.annotate(predicted);
          for (ResultsPrinter rp : entityResultsPrinterSet){
            String msg = rp.printResults(training, predicted);
            MachineReadingProperties.logger.info("Training event extraction using predicted entitities: entity scores using printer " + rp.getClass() + ":\n" + msg);
          }

          // TODO: need an equivalent of changeGoldRelationArgsToPredicted here?
        }

        Constructor<?> mstConstructor = (Class.forName("edu.stanford.nlp.ie.machinereading.MSTBasedEventExtractor")).getConstructor(boolean.class);
        eventExtractor = (Extractor) mstConstructor.newInstance(MachineReadingProperties.trainEventsUsingPredictedEntities);

        MachineReadingProperties.logger.info("Training event extraction model...");
        if (MachineReadingProperties.trainRelationsUsingPredictedEntities) {
          eventExtractor.train(predicted);
        } else {
          eventExtractor.train(training);
        }
        MachineReadingProperties.logger.info("Serializing event extraction model to " + modelName + " ...");
        eventExtractor.save(modelName);
      }
    }
  }

  /**
   * Removes any relations with relation types in relationsToSkip from a dataset.  Dataset is modified in place.
   */
  private static void removeSkippableRelations(Annotation dataset, Set<String> relationsToSkip) {
    if (relationsToSkip == null || relationsToSkip.isEmpty()) {
      return;
    }
    for (CoreMap sent : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<RelationMention> relationMentions = sent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
      if (relationMentions == null) {
        continue;
      }
      List<RelationMention> newRelationMentions = new ArrayList<>();
      for (RelationMention rm: relationMentions) {
        if (!relationsToSkip.contains(rm.getType())) {
          newRelationMentions.add(rm);
        }
      }
      sent.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, newRelationMentions);
    }
  }

  /**
   * Replaces all relation arguments with predicted entities
   */
  private static void changeGoldRelationArgsToPredicted(Annotation dataset) {
    for (CoreMap sent : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<EntityMention> entityMentions = sent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
      List<RelationMention> relationMentions = sent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
      List<RelationMention> newRels = new ArrayList<>();
      for (RelationMention rm : relationMentions) {
        rm.setSentence(sent);
        if (rm.replaceGoldArgsWithPredicted(entityMentions)) {
          MachineReadingProperties.logger.info("Successfully mapped all arguments in relation mention: " + rm);
          newRels.add(rm);
        } else {
          MachineReadingProperties.logger.info("Dropped relation mention due to failed argument mapping: " + rm);
        }
      }
      sent.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, newRels);
      // we may have added new mentions to the entity list, so let's store it again
      sent.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, entityMentions);
    }
  }

  public Annotation annotate(Annotation testing) {
    return annotate(testing, -1);
  }

  protected Annotation annotate(Annotation testing, int partition) {
    int partitionIndex = (partition != -1 ? partition : 0);

    //
    // annotate entities
    //
    if (MachineReadingProperties.extractEntities) {
      assert(entityExtractor != null);
      Annotation predicted = AnnotationUtils.deepMentionCopy(testing);
      entityExtractor.annotate(predicted);

      for (ResultsPrinter rp : entityResultsPrinterSet){
        String msg = rp.printResults(testing, predicted);
        MachineReadingProperties.logger.info("Entity extraction results " + (partition != -1 ? "for partition #" + partition : "") + " using printer " + rp.getClass() + ":\n" + msg);
      }
      predictions[ENTITY_LEVEL][partitionIndex] = predicted;
    }

    //
    // annotate relations
    //
    if (MachineReadingProperties.extractRelations) {
      assert(relationExtractor != null);

      Annotation predicted = (MachineReadingProperties.testRelationsUsingPredictedEntities ? predictions[ENTITY_LEVEL][partitionIndex] : AnnotationUtils.deepMentionCopy(testing));
      // make sure the entities have the syntactic head and span set. we need this for relation extraction features

      // TODO(AngledLuffa): this call to assignSyntacticHeadToEntities
      // is changing the annotations for the original annotation.
      // This is probably not right?  It can result in changes in the
      // dependencies when run in the pipeline.  For example:
      //  "They are such as interested Thomas Aquinas and Bonaventura, Anselm and Bernard."
      // https://github.com/stanfordnlp/CoreNLP/issues/1053
      assignSyntacticHeadToEntities(predicted);
      relationExtractor.annotate(predicted);

      if (relationExtractionPostProcessor == null) {
        relationExtractionPostProcessor = makeExtractor(MachineReadingProperties.relationExtractionPostProcessorClass);
      }
      if (relationExtractionPostProcessor != null) {
        MachineReadingProperties.logger.info("Using relation extraction post processor: " + MachineReadingProperties.relationExtractionPostProcessorClass);
        relationExtractionPostProcessor.annotate(predicted);
      }

      for (ResultsPrinter rp : getRelationResultsPrinterSet()){
        String msg = rp.printResults(testing, predicted);
        MachineReadingProperties.logger.info("Relation extraction results " + (partition != -1 ? "for partition #" + partition : "") + " using printer " + rp.getClass() + ":\n" + msg);
      }

      //
      // apply the domain-specific consistency checks
      //
      if (consistencyChecker == null) {
        consistencyChecker = makeExtractor(MachineReadingProperties.consistencyCheck);
      }
      if (consistencyChecker != null) {
        MachineReadingProperties.logger.info("Using consistency checker: " + MachineReadingProperties.consistencyCheck);
        consistencyChecker.annotate(predicted);

        for (ResultsPrinter rp : entityResultsPrinterSet){
          String msg = rp.printResults(testing, predicted);
          MachineReadingProperties.logger.info("Entity extraction results AFTER consistency checks " + (partition != -1 ? "for partition #" + partition : "") + " using printer " + rp.getClass() + ":\n" + msg);
        }
        for (ResultsPrinter rp : getRelationResultsPrinterSet()){
          String msg = rp.printResults(testing, predicted);
          MachineReadingProperties.logger.info("Relation extraction results AFTER consistency checks " + (partition != -1 ? "for partition #" + partition : "") + " using printer " + rp.getClass() + ":\n" + msg);
        }
      }

      predictions[RELATION_LEVEL][partitionIndex] = predicted;
    }

    //
    // TODO: annotate events
    //

    return predictions[RELATION_LEVEL][partitionIndex];
  }

  private void assignSyntacticHeadToEntities(Annotation corpus) {
    assert(corpus != null);
    assert(corpus.get(SentencesAnnotation.class) != null);
    for(CoreMap sent: corpus.get(SentencesAnnotation.class)){
      List<CoreLabel> tokens = sent.get(TokensAnnotation.class);
      assert(tokens != null);
      Tree tree = sent.get(TreeAnnotation.class);
      if (MachineReadingProperties.forceGenerationOfIndexSpans) {
        tree.indexSpans(0);
      }
      assert(tree != null);
      if(sent.get(EntityMentionsAnnotation.class) != null){
        for(EntityMention e: sent.get(EntityMentionsAnnotation.class)){
          reader.assignSyntacticHead(e, tree, tokens, true);
        }
      }
    }
  }

  private static Extractor makeExtractor(Class<Extractor> extractorClass) {
    if (extractorClass == null) return null;
    Extractor ex;
    try {
      ex = extractorClass.getConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return ex;
  }

  @SuppressWarnings("unchecked")
  private void makeDataSets(Annotation training, Annotation testing, Annotation auxDataset) {
    if(! MachineReadingProperties.crossValidate){
      datasets = new Pair[1];
      Annotation trainingEnhanced = training;
      if (auxDataset != null) {
        trainingEnhanced = new Annotation(training.get(TextAnnotation.class));
        for(int i = 0; i < AnnotationUtils.sentenceCount(training); i ++){
          AnnotationUtils.addSentence(trainingEnhanced, AnnotationUtils.getSentence(training, i));
        }
        for (int ind = 0; ind < AnnotationUtils.sentenceCount(auxDataset); ind++) {
          AnnotationUtils.addSentence(trainingEnhanced, AnnotationUtils.getSentence(auxDataset, ind));
        }
      }
      datasets[0] = new Pair<>(trainingEnhanced, testing);

      predictions = new Annotation[3][1];
    } else {
      assert(MachineReadingProperties.kfold > 1);
      datasets = new Pair[MachineReadingProperties.kfold];
      AnnotationUtils.shuffleSentences(training);
      for (int partition = 0; partition <MachineReadingProperties.kfold; partition++) {
        int begin = AnnotationUtils.sentenceCount(training) * partition / MachineReadingProperties.kfold;
        int end = AnnotationUtils.sentenceCount(training) * (partition + 1) / MachineReadingProperties.kfold;
        MachineReadingProperties.logger.info("Creating partition #" + partition + " using offsets [" + begin + ", " + end + ") out of " + AnnotationUtils.sentenceCount(training));
        Annotation partitionTrain = new Annotation("");
        Annotation partitionTest = new Annotation("");
        for(int i = 0; i < AnnotationUtils.sentenceCount(training); i ++){
          if(i < begin){
            AnnotationUtils.addSentence(partitionTrain, AnnotationUtils.getSentence(training, i));
          } else if(i < end){
            AnnotationUtils.addSentence(partitionTest, AnnotationUtils.getSentence(training, i));
          } else {
            AnnotationUtils.addSentence(partitionTrain, AnnotationUtils.getSentence(training, i));
          }
        }

        // for learning curve experiments
        // partitionTrain = keepPercentage(partitionTrain, percentageOfTrain);
        partitionTrain = keepPercentage(partitionTrain, MachineReadingProperties.percentageOfTrain);

        if (auxDataset != null) {
          for (int ind = 0; ind < AnnotationUtils.sentenceCount(auxDataset); ind++) {
            AnnotationUtils.addSentence(partitionTrain, AnnotationUtils
                    .getSentence(auxDataset, ind));
          }
        }
        datasets[partition] = new Pair<>(partitionTrain, partitionTest);
      }

      predictions = new Annotation[3][MachineReadingProperties.kfold];
    }
  }

  /** Keeps only the first percentage sentences from the given corpus */
  private static Annotation keepPercentage(Annotation corpus, double percentage) {
    log.info("Using fraction of train: " + percentage);
    if (percentage >= 1.0) {
      return corpus;
    }
    Annotation smaller = new Annotation("");
    List<CoreMap> sents = new ArrayList<>();
    List<CoreMap> fullSents = corpus.get(SentencesAnnotation.class);
    double smallSize = (double) fullSents.size() * percentage;
    for (int i = 0; i < smallSize; i ++) {
      sents.add(fullSents.get(i));
    }
    log.info("TRAIN corpus size reduced from " + fullSents.size() + " to " + sents.size());
    smaller.set(SentencesAnnotation.class, sents);
    return smaller;
  }

  private static boolean serializedModelExists(String prefix) {
    if (!MachineReadingProperties.crossValidate) {
      File f = new File(prefix);
      return f.exists();
    }

    // in cross validation we serialize models to prefix.<FOLD COUNT>
    for (int i = 0; i < MachineReadingProperties.kfold; i++) {
      File f = new File(prefix + "." + Integer.toString(i));
      if (!f.exists()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Creates ResultsPrinter instances based on the resultsPrinters argument
   * @param args
   */
  private void makeResultsPrinters(String[] args) {
    entityResultsPrinterSet = makeResultsPrinters(MachineReadingProperties.entityResultsPrinters, args);
    setRelationResultsPrinterSet(makeResultsPrinters(MachineReadingProperties.relationResultsPrinters, args));
    eventResultsPrinterSet = makeResultsPrinters(MachineReadingProperties.eventResultsPrinters, args);
  }

  private static Set<ResultsPrinter> makeResultsPrinters(String classes, String[] args) {
    MachineReadingProperties.logger.info("Making result printers from " + classes);
    String[] printerClassNames = classes.trim().split(",\\s*");
    HashSet<ResultsPrinter> printers = new HashSet<>();
    for (String printerClassName : printerClassNames) {
      if(printerClassName.isEmpty()) continue;
      ResultsPrinter rp;
      try {
        rp = (ResultsPrinter) Class.forName(printerClassName).getConstructor().newInstance();
        printers.add(rp);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      //Execution.fillOptions(ResultsPrinterProps.class, args);
      //Arguments.parse(args,rp);
    }
    return printers;
  }

  /**
   * Constructs the corpus reader class and sets it as the reader for this MachineReading instance.
   *
   * @return corpus reader specified by datasetReaderClass
   */
  private GenericDataSetReader makeReader(Properties props) {
    try {
      if(reader == null){
        try {
          reader = MachineReadingProperties.datasetReaderClass.getConstructor(Properties.class).newInstance(props);
        } catch(java.lang.NoSuchMethodException e) {
          // if no c'tor with props found let's use the default one
          reader = MachineReadingProperties.datasetReaderClass.getConstructor().newInstance();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    reader.setUseNewHeadFinder(MachineReadingProperties.useNewHeadFinder);
    return reader;
  }

  /**
   * Constructs the corpus reader class and sets it as the reader for this MachineReading instance.
   *
   * @return corpus reader specified by datasetAuxReaderClass
   */
  private GenericDataSetReader makeAuxReader() {
    try {
      if (auxReader == null) {
        if (MachineReadingProperties.datasetAuxReaderClass != null) {
          auxReader = MachineReadingProperties.datasetAuxReaderClass.getConstructor().newInstance();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return auxReader;
  }

  public static Extractor makeEntityExtractor(
          Class<? extends BasicEntityExtractor> entityExtractorClass,
          String gazetteerPath) {
    if (entityExtractorClass == null) return null;
    BasicEntityExtractor ex;
    try {
      ex = entityExtractorClass.getConstructor(String.class).newInstance(gazetteerPath);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return ex;
  }

  private static Extractor makeRelationExtractor(
          Class<? extends BasicRelationExtractor> relationExtractorClass, RelationFeatureFactory featureFac, boolean createUnrelatedRelations, RelationMentionFactory factory) {
    if (relationExtractorClass == null) return null;
    BasicRelationExtractor ex;
    try {
      ex = relationExtractorClass.getConstructor(RelationFeatureFactory.class, Boolean.class, RelationMentionFactory.class).newInstance(featureFac, createUnrelatedRelations, factory);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return ex;
  }

  public static RelationFeatureFactory makeRelationFeatureFactory(
          Class<? extends RelationFeatureFactory> relationFeatureFactoryClass,
          String relationFeatureList,
          boolean doNotLexicalizeFirstArg) {
    if (relationFeatureList == null || relationFeatureFactoryClass == null)
      return null;
    Object[] featureList = new Object [] {relationFeatureList.trim().split(",\\s*")};
    RelationFeatureFactory rff;
    try {
      rff = relationFeatureFactoryClass.getConstructor(String[].class).newInstance(featureList);
      rff.setDoNotLexicalizeFirstArgument(doNotLexicalizeFirstArg);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return rff;
  }

  private static RelationMentionFactory makeRelationMentionFactory(
          Class<RelationMentionFactory> relationMentionFactoryClass) {
    RelationMentionFactory rmf;
    try {
      rmf = relationMentionFactoryClass.getConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return rmf;
  }

  /**
   * Gets the serialized sentences for a data set. If the serialized sentences
   * are already on disk, it loads them from there. Otherwise, the data set is
   * read with the corpus reader and the serialized sentences are saved to disk.
   *
   * @param sentencesPath Llocation of the raw data set
   * @param reader The corpus reader
   * @param serializedSentences Where the serialized sentences should be stored on disk
   * @return A list of RelationsSentences
   */
  private Annotation loadOrMakeSerializedSentences(
          String sentencesPath, GenericDataSetReader reader,
          File serializedSentences) throws IOException, ClassNotFoundException {
    Annotation corpusSentences;
    // if the serialized file exists, just read it. otherwise read the source
    // and and save the serialized file to disk
    if (MachineReadingProperties.serializeCorpora && serializedSentences.exists() && !forceParseSentences) {
      MachineReadingProperties.logger.info("Loaded serialized sentences from " + serializedSentences.getAbsolutePath() + "...");
      corpusSentences = IOUtils.readObjectFromFile(serializedSentences);
      MachineReadingProperties.logger.info("Done. Loaded " + corpusSentences.get(CoreAnnotations.SentencesAnnotation.class).size() + " sentences.");
    } else {
      // read the corpus
      MachineReadingProperties.logger.info("Parsing corpus sentences...");
      if(MachineReadingProperties.serializeCorpora)
        MachineReadingProperties.logger.info("These sentences will be serialized to " + serializedSentences.getAbsolutePath());
      corpusSentences = reader.parse(sentencesPath);
      MachineReadingProperties.logger.info("Done. Parsed " + AnnotationUtils.sentenceCount(corpusSentences) + " sentences.");

      // save corpusSentences
      if(MachineReadingProperties.serializeCorpora){
        MachineReadingProperties.logger.info("Serializing parsed sentences to " + serializedSentences.getAbsolutePath() + "...");
        IOUtils.writeObjectToFile(corpusSentences,serializedSentences);
        MachineReadingProperties.logger.info("Done. Serialized " + AnnotationUtils.sentenceCount(corpusSentences) + " sentences.");
      }
    }
    return corpusSentences;
  }

  public void setExtractEntities(boolean extractEntities) {
    MachineReadingProperties.extractEntities = extractEntities;
  }

  public void setExtractRelations(boolean extractRelations) {
    MachineReadingProperties.extractRelations = extractRelations;
  }

  public void setExtractEvents(boolean extractEvents) {
    MachineReadingProperties.extractEvents = extractEvents;
  }

  public void setForceParseSentences(boolean forceParseSentences) {
    this.forceParseSentences = forceParseSentences;
  }

  public void setDatasets(Pair<Annotation, Annotation> [] datasets) {
    this.datasets = datasets;
  }

  public Pair<Annotation, Annotation> [] getDatasets() {
    return datasets;
  }

  public void setPredictions(Annotation [][] predictions) {
    this.predictions = predictions;
  }

  public Annotation [][] getPredictions() {
    return predictions;
  }

  public void setReader(GenericDataSetReader reader) {
    this.reader = reader;
  }

  public GenericDataSetReader getReader() {
    return reader;
  }

  public void setAuxReader(GenericDataSetReader auxReader) {
    this.auxReader = auxReader;
  }

  public GenericDataSetReader getAuxReader() {
    return auxReader;
  }

  public void setEntityResultsPrinterSet(Set<ResultsPrinter> entityResultsPrinterSet) {
    this.entityResultsPrinterSet = entityResultsPrinterSet;
  }

  public Set<ResultsPrinter> getEntityResultsPrinterSet() {
    return entityResultsPrinterSet;
  }

  public void setRelationResultsPrinterSet(Set<ResultsPrinter> relationResultsPrinterSet) {
    this.relationResultsPrinterSet = relationResultsPrinterSet;
  }

  public Set<ResultsPrinter> getRelationResultsPrinterSet() {
    return relationResultsPrinterSet;
  }

}
