package edu.stanford.nlp.ie.machinereading.domains.bionlp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.machinereading.GenericDataSetReader;
import edu.stanford.nlp.ie.machinereading.MachineReading;
import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.EventMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.DocumentDirectoryAnnotation;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.EntityMentionsAnnotation;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.EventMentionsAnnotation;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabelFactory;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Quadruple;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.ling.CoreAnnotations.DocIDAnnotation;

/**
 * Reads the BioNLP 09 corpus
 *
 * @author Andrey Gusev
 * @author David McClosky (mcclosky@stanford.edu)
 * @author Mihai
 *
 */
public class BioNLP09Reader extends GenericDataSetReader {

  private static final String TEXT_EXTENSION = "txt";
  protected static final String TOKENIZED_EXTENSION = "tokenized.stanford";
  protected static final String TREE_EXTENSION = "pstree.stanford";

  protected static final String ANNOTATIONS1 = "a1";
  protected static final String ANNOTATIONS2 = "a2.t1"; // we only care about Task 1 for now

  protected static final String ENTITY_TYPE_PREFIX = "T";
  protected static final String EVENT_TYPE_PREFIX = "E";
  protected static final String EVENT_MODIFICATIONS = "M";

  /**
   * If true, we remove events that have all their arguments in different sentences than the anchor
   */
  private static final boolean REMOVE_CROSS_SENTENCE_EVENTS = true;
  /**
   * If true, we remove entities whose spans overlap (this is a very small number)
   * We also remove events that are left wo arguments after this operation
   */
  private static final boolean REMOVE_OVERLAPPING_ENTITIES = true;
  /**
   * If true, we merge events that have the same anchor
   */
  private static final boolean MERGE_SAME_ANCHOR_EVENTS = true;
  /**
   * If true, we remove additional arg roles from objects that serve as more than one event argument
   */
  private static final boolean REMOVE_ARGS_FOR_ASSIGNED_OBJECTS = true;
  /**
   * If true, we remove events that have no arguments (in case the args were removed by other sanity checks)
   */
  private static final boolean REMOVE_EVENTS_WITH_NO_ARGS = true;

  /** If true, we convert Theme2 to Theme */
  private static final boolean SIMPLIFY_EDGE_LABELS = true;

  /** If true, uses the tokenization and parse trees provided by the task organizers (.tokenized and .pstree) */
  private final boolean useOfflineAnnotations;

  /** Read files generated with this beam */
  private final int predictedTriggerBeam;

  /** If false, all gold triggers that do not match predicted ones are removed */
  private boolean keepGoldTriggers;

  /** If true, parse entire documents instead of individual sentences */
  private boolean documentLevelParsing;

  /** If true, collapses unfrequent gold labels */
  private boolean collapseGoldLabels;

  /** If true, remove events left without any arguments during sanity checks */
  private boolean removeZeroArgEvents;

  private Map<String, EntityMention> idsToEntities;
  Map<String, EventMention> idsToEvents;

  int count1Pass = 0;
  int count1Fail = 0;
  int count1EventsRemoved = 0;
  int count1AnchorsRemoved = 0;
  int count2Pass = 0;
  int count2Fail = 0;
  int count2EventsRemoved = 0;
  int count2EntsRemoved = 0;
  int count2bPass = 0;
  int count2bFail = 0;
  int count2bEventsRemoved = 0;
  int count3Pass = 0;
  int count3Fail = 0;
  int count3EventsRemoved = 0;
  int count4Total = 0;
  int count4Fail = 0;
  int count4ArgsRemoved = 0;
  int count4EventsRemoved = 0;
  int count5Pass = 0;
  int count5Fail = 0;
  int count5Removed = 0;
  int totalEvents = 0;
  int totalEntities = 0;
  int totalSentences = 0;
  int countEventsWithGoldTrigers = 0;
  int countEventsWithGoldTrigersWithDeps = 0;
  int countGoldTriggers = 0;
  Counter<Integer> spanHistogram = new ClassicCounter<Integer>();

  Counter<String> eventsRemovedInCheck4 = new ClassicCounter<String>();
  Counter<String> eventsRemovedInCheck3 = new ClassicCounter<String>();
  Counter<String> eventsRemovedInCheck1 = new ClassicCounter<String>();
  Counter<String> eventsBeforeChecks = new ClassicCounter<String>();

  public BioNLP09Reader() {
    // backwards compatibility: by default use BaselineNLProcessor for NLP
    this(false, -1, true, false, true);
  }

  public BioNLP09Reader(boolean useOfflineAnnotations,
      int beamValueForPredictedTriggers,
      boolean keepGoldTriggers,
      boolean documentLevelParsing,
      boolean removeZeroArgEvents) {
    super(null, true, false, true);
    this.useOfflineAnnotations = useOfflineAnnotations;
    this.predictedTriggerBeam = beamValueForPredictedTriggers;
    this.keepGoldTriggers = keepGoldTriggers;
    this.documentLevelParsing = documentLevelParsing;
    this.collapseGoldLabels = true;
    this.removeZeroArgEvents = removeZeroArgEvents;

    // change the logger to one from our namespace
    logger = Logger.getLogger(BioNLP09Reader.class.getName());
    // run quietly by default
    logger.setLevel(Level.SEVERE);
  }

  static class AlphabeticalOrder implements Comparator<File> {

    public int compare(File o1, File o2) {
      return o1.getAbsolutePath().compareTo(o2.getAbsolutePath());
    }

  }

  public static class HasGoldAnnotation implements CoreAnnotation<Boolean> {
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  private List<File> getRawFiles(String path) throws IOException {
    File dir = new File(path);
    assert(dir.exists());

    File[] rawTextFiles = null;
    if(dir.isDirectory()){
      rawTextFiles = dir.listFiles(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.endsWith(TEXT_EXTENSION);
        }
      });
    } else {
      assert(path.endsWith(TEXT_EXTENSION));
      rawTextFiles = new File[1];
      rawTextFiles[0] = dir;
    }

    return Arrays.asList(rawTextFiles);
  }

  public Annotation read(String path) throws IOException {
    Annotation dataset = new Annotation("");
    boolean hasGold = false;

    List<File> rawTextFiles = new ArrayList<File>();
    String [] pathBits = path.split(",");
    logger.severe("Reading from " + pathBits.length + " paths:");
    int count = 0;
    for(String pb: pathBits){
      logger.severe("Path #" + (count ++) + ": " + pb);
      rawTextFiles.addAll(getRawFiles(pb));
    }
    Collections.sort(rawTextFiles, new AlphabeticalOrder());

    // we assume that for each file there will be annotations files
    logger.info("Found " + rawTextFiles.size() + " raw text files.");
    for (File rawFile : rawTextFiles) {
    	logger.info("Reading " + rawFile.getName());
      assert rawFile.exists() && rawFile.getName().endsWith(TEXT_EXTENSION) : "should be raw text file";
      String rawText = IOUtils.slurpFile(rawFile);

      // for each text file there could be multiple sentences and we have to
      // match to entities
      String docId = rawFile.getName().replace("." + TEXT_EXTENSION, "");
      String pathPrefix = rawFile.getAbsolutePath().substring(0, rawFile.getAbsolutePath().length() - TEXT_EXTENSION.length());
      List<CoreMap> sentences = getSentencesFromText(docId, pathPrefix, rawText);

      if(useOfflineAnnotations){
        try {
          List<Tree> trees = readTrees(pathPrefix + TREE_EXTENSION);
          assert trees.size() == sentences.size() : "Number of parse trees doesn't match the number of sentences in file " + rawFile;
          for(int i = 0; i < sentences.size(); i ++){
            CoreMap sent = sentences.get(i);
            Tree t = trees.get(i);
            generateSentenceAnnotations(sent, t);
          }
        } catch(IOException e) {
          logger.severe("Failed to read syntactic annotation from file: " + pathPrefix + TREE_EXTENSION);
          e.printStackTrace();
          System.exit(1);
        }
      }

      File annotationFileA1 = new File(rawFile.getCanonicalPath().replace(TEXT_EXTENSION, ANNOTATIONS1));
      assert annotationFileA1.exists();

      // reset for new set of annotations for raw text
      idsToEntities = new HashMap<String, EntityMention>();
      idsToEvents = new HashMap<String, EventMention>();

      // now add entity and events from given file
      addEntitiesAndEvents(sentences, annotationFileA1, rawText);

      File annotationFileA2 = new File(rawFile.getCanonicalPath().replace(TEXT_EXTENSION, ANNOTATIONS2));
      // a2 may not exist
      if (annotationFileA2.exists()) {
        addEntitiesAndEvents(sentences, annotationFileA2, rawText);
        hasGold = true;
      }

      if(collapseGoldLabels){
        for(CoreMap sent: sentences)
          simplifyGoldLabels(sent);
      }

      if(predictedTriggerBeam >= 0){
        File predTriggerFile = new File(rawFile.getCanonicalPath().replace(TEXT_EXTENSION, "triggers.b" + predictedTriggerBeam));
        addPredictedTriggers(sentences, predTriggerFile);
      }

      if(! keepGoldTriggers && hasGold && predictedTriggerBeam >= 0){
        removeGoldTriggers(sentences);
      }

      // sanity checks may change the content of the sentences
      sentences = sanityCheck(sentences);
      countCrossSentenceEvents(sentences);

      // add the doc id and path to each sentence
      for (CoreMap sentence : sentences) {
        sentence.set(DocIDAnnotation.class, docId);
        File parent = rawFile.getParentFile();
        assert(parent != null);
        sentence.set(DocumentDirectoryAnnotation.class, parent.getAbsolutePath());
      }

      // add sentences to dataset
      for (CoreMap sentence : sentences) {
        AnnotationUtils.addSentence(dataset, sentence);
      }
    }

    logger.severe("Events before sanity checks: " + eventsBeforeChecks);
    logger.severe("Removed " + countEventsWithGoldTrigers + " events with gold triggers, " + countEventsWithGoldTrigersWithDeps + " after deps. Removed " + countGoldTriggers + " gold triggers.");
    logger.severe("SANITY CHECK: " + count1Pass + " events passed sanity check 1, and " + count1Fail + " failed. As a consequence "
        + count1EventsRemoved + " events were removed; "
        + count1AnchorsRemoved + " anchors were removed.");
    logger.severe("Histogram of events removed in step 1: " + eventsRemovedInCheck1);
    logger.severe("SANITY CHECK: " + count2Pass + " entities/anchors passed sanity check 2, and " + count2Fail + " failed. As a consequence "
        + count2EntsRemoved + " entities and " + count2EventsRemoved + " events were removed.");
    logger.severe("SANITY CHECK: " + count2bPass + " events passed sanity check 2b, and " + count2bFail + " failed. As a consequence "
        + count2bEventsRemoved + " events were removed.");
    logger.severe("SANITY CHECK: " + count3Pass + " events passed sanity check 3, and " + count3Fail + " failed. As a consequence "
        + count3EventsRemoved + " events were removed.");
    logger.severe("Histogram of events removed in step 3: " + eventsRemovedInCheck3);
    logger.severe("SANITY CHECK: " + count4Fail + " out of " + count4Total + " events failed sanity check 4. As a consequence "
        + count4ArgsRemoved + " arguments and " +
        + count4EventsRemoved + " events were removed.");
    logger.severe("Histogram of events removed in step 4: " + eventsRemovedInCheck4);
    logger.severe("SANITY CHECK: " + count5Pass + " events passed sanity check 5, and " + count5Fail + " failed. As a consequence " + count5Removed + " events were removed.");
    logger.severe("SANITY CHECK: clean corpus contains: " + totalSentences + " sentences, " + totalEntities + " entities and " + totalEvents + " events.");
    logger.severe("Event span histogram: " + spanHistogram);

    dataset.set(HasGoldAnnotation.class, hasGold);
    return dataset;
  }

  /**
   * Simplifies gold trigger and event labels by removing most label combinations
   * This must be called *before* predicted triggers are loaded!
   * @param sent
   */
  private static void simplifyGoldLabels(CoreMap sent) {
    if(sent.get(EntityMentionsAnnotation.class) != null){
      for(EntityMention e: sent.get(EntityMentionsAnnotation.class)){
        e.setType(simplifyLabel(e.getType()));
      }
    }

    if(sent.get(EventMentionsAnnotation.class) != null){
      for(EventMention ev: sent.get(EventMentionsAnnotation.class)){
        ev.setType(simplifyLabel(ev.getType()));
      }
    }
  }
  private static String simplifyLabel(String s) {
    // don't mess with entities
    if(! isTrigger(s)) return s;

    // keep these combinations. they are reasonably common
    if(s.equalsIgnoreCase("Gene_expression/Positive_regulation")) return s;
    if(s.equalsIgnoreCase("Gene_expression/Transcription")) return s;

    // break all other combinations and keep just the non-recursive type
    String [] bits = s.split("/");
    if(bits.length == 1) return s; // not a combination
    assert(bits.length == 2);

    // System.err.println("FOUND JOINT LABEL in simplifyLabel: " + s);

    String b0 = bits[0].toLowerCase();
    String b1 = bits[1].toLowerCase();

    // if two regulations convert to one regulation
    if(b0.contains("regulation") && b1.contains("regulation")){
      // keep the most common regulation: positive, then negative, then generic
      if(b0.contains("positive") || b1.contains("positive")) return "Positive_regulation";
      else if(b0.contains("negative") || b1.contains("negative")) return "Negative_regulation";
      return "Regulation";
    }

    // if one regulation, keep the other type
    if(b0.contains("regulation")) return bits[1];
    else if(b1.contains("regulation")) return bits[0];

    // if one protein, keep the other type
    if(b0.contains("protein")) return bits[1];
    else if(b1.contains("protein")) return bits[0];

    // arbitrarily pick one
    return bits[0];
  }

  private void removeGoldTriggers(List<CoreMap> sentences) {
    annotationsSummary(sentences, "Annotations before removing gold triggers");
    logger.info("Removing events with gold triggers...");
    Set<EventMention> eventsWithGoldTriggers = new IdentityHashSet<EventMention>();
    Set<EntityMention> goldTriggers = new IdentityHashSet<EntityMention>();
    for(CoreMap sent: sentences) {
      if(sent.get(EntityMentionsAnnotation.class) != null){
        for(EntityMention e: sent.get(EntityMentionsAnnotation.class)){
          if (isGoldTriggerOnly(e)) {
            goldTriggers.add(e);
          }
        }
      }
    }
    int total = 0;
    for(CoreMap sent: sentences) {
      if(sent.get(EventMentionsAnnotation.class) != null){
        for(EventMention ev: sent.get(EventMentionsAnnotation.class)){
          total ++;
          if(goldTriggers.contains(ev.getAnchor())){
            eventsWithGoldTriggers.add(ev);
          }
        }
      }
    }
    // System.err.println("Removing " + eventsWithGoldTriggers.size() + " out of " + total + " events.");
    logger.info("eventsWithGoldTriggers.size = " + eventsWithGoldTriggers.size());
    countEventsWithGoldTrigers += eventsWithGoldTriggers.size();
    MutableInteger count = new MutableInteger(0);
    removeEvents(sentences, eventsWithGoldTriggers, count);
    countEventsWithGoldTrigersWithDeps += count.intValue();
    logger.info("Removed " + count.intValue() + " events with gold triggers.");
    annotationsSummary(sentences, "Annotations after removing gold-trigger events");
    count = new MutableInteger(0);
    removeEntities(sentences, goldTriggers, count);
    countGoldTriggers += count.intValue();
    annotationsSummary(sentences, "Annotations after removing gold-trigger anchors");
  }

  private void countCrossSentenceEvents(List<CoreMap> sentences) {
    for(CoreMap sentence: sentences) {
      List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
      if(events != null){
        for(EventMention event: events){
          Integer anchorSent = event.getAnchor().getSentence().get(SentenceIndexAnnotation.class);
          assert(anchorSent != null);
          int max = 0;
          for(ExtractionObject arg: event.getArgs()){
            Integer argSent = null;
            if(arg instanceof EventMention){
              argSent = ((EventMention) arg).getAnchor().getSentence().get(SentenceIndexAnnotation.class);
            } else {
              argSent = arg.getSentence().get(SentenceIndexAnnotation.class);
            }
            assert(argSent != null);
            int dist = Math.abs(argSent - anchorSent);
            if(dist > max) max = dist;
          }
          spanHistogram.incrementCount(max);
        }
      }
    }
  }

  /**
   * Implements sanity checks specific for BioNLP annotations
   * This method is called once for each document parsed
   * We currently check for the following things:
   * - do different events have the same anchor?
   * - do ExtractionObjects store internally a sentence that is different from the sentence that contains them?
   * - are there EntityMentions or event anchors that contain the same token in the headSpan?
   * @param sentences Sentences in the current document
   */
  private List<CoreMap> sanityCheck(List<CoreMap> sentences) {
    for(CoreMap sentence: sentences) {
      List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
      if(events != null){
        for(EventMention e: events){
          eventsBeforeChecks.incrementCount(anchorAndArgs(e));
        }
      }
    }

    //
    // CHECK 1
    // do ExtractionObjects store internally a sentence that is different from the sentence that contains them?
    // this can happen! Events may have other events from different sentences as args!
    //
    if(! documentLevelParsing){
      int sentenceCount = 0;
      Set<EventMention> crossSentenceEvents = new IdentityHashSet<EventMention>();
      Set<EntityMention> crossSentenceAnchors = new IdentityHashSet<EntityMention>();
      for(CoreMap sentence: sentences) {
        sentenceCount ++;
        List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
        if(events != null){
          logger.fine("Events in sentence #" + sentenceCount);
          logger.fine("\tText: " + sentence.get(TextAnnotation.class));
          for(EventMention e: events){
            logger.fine("\t" + e);
          }

          for(EventMention event: events) {
            boolean fail = false;
            // check the event object
            assert event.getSentence() == sentence : "FAILED sanity check 1: event " + event + " contains sentence: " + event.getSentence().get(TextAnnotation.class) + " rather than: " + sentence.get(TextAnnotation.class);
            // check the anchor
            assert event.getAnchor().getSentence() == sentence : "FAILED sanity check 1: anchor " + event.getAnchor() + " contains sentence: " + event.getAnchor().getSentence().get(TextAnnotation.class) + " rather than: " + sentence.get(TextAnnotation.class);

            // check the arguments
            Set<ExtractionObject> argsToRemove = new IdentityHashSet<ExtractionObject>();
            for(int i = 0; i < event.getArgs().size(); i ++){
              String role = event.getArgNames().get(i);
              ExtractionObject arg = event.getArg(i);
              if(arg.getSentence() != sentence){
                logger.info("FAILED sanity check 1: event argument " + arg + " with role " + role + " contains sentence: " + arg.getSentence().get(TextAnnotation.class) + " rather than: " + sentence.get(TextAnnotation.class));
                argsToRemove.add(arg);
                fail = true;
              }
            }

            if(fail){
              count1Fail ++;
              event.removeArguments(argsToRemove, true);
              if(event.getArgs().size() == 0 && removeZeroArgEvents){
                crossSentenceEvents.add(event);
                crossSentenceAnchors.add((EntityMention) event.getAnchor());
              }
            }
            else{
              count1Pass ++;
            }
          }
        }
      }
      if(REMOVE_CROSS_SENTENCE_EVENTS && crossSentenceEvents.size() > 0){
        MutableInteger count = new MutableInteger(0);
        removeEvents(sentences, crossSentenceEvents, count, false, eventsRemovedInCheck1);
        count1EventsRemoved += count.intValue();

        // TODO: should we remove the anchors of cross-sentence events? yes for now
        count = new MutableInteger(0);
        removeEntities(sentences, crossSentenceAnchors, count);
        count1AnchorsRemoved += count.intValue();
      }
    } // ! documentLevelParsing
    annotationsSummary(sentences, "Annotations after sanity check 1");

    //
    // CHECK 2
    // are there EntityMentions or event anchors that contain the same token in the headSpan?
    //
    Set<ExtractionObject> objects = new IdentityHashSet<ExtractionObject>();
    for(CoreMap sentence: sentences) {
      List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
      if(events != null){
        for(EventMention e: events){
          objects.add(e.getAnchor());
        }
      }

      List<EntityMention> entities = sentence.get(EntityMentionsAnnotation.class);
      if(entities != null){
        for(EntityMention e: entities){
          objects.add(e);
        }
      }
    }

    ExtractionObject [] objArray = new ExtractionObject[objects.size()];
    logger.fine("Found " + objArray.length + " distinct entities in this document.");
    objects.toArray(objArray);
    Set<ExtractionObject> overlappingEntities = new IdentityHashSet<ExtractionObject>();
    for(int i = 0; i < objArray.length; i ++){
      ExtractionObject o1 = objArray[i];
      boolean fail = false;
      for(int j = i + 1; j < objArray.length; j ++){
        ExtractionObject o2 = objArray[j];
        if(o1.getSentence() == o2.getSentence()){
          List<CoreLabel> tokens = o1.getSentence().get(TokensAnnotation.class);
          int h1 = headPosition(o1, tokens);
          int h2 = headPosition(o2, tokens);
          // found overlap between two entities!
          //if(intersect(o1.getExtent(), o2.getExtent())){
          if(h1 == h2) {
            logger.info("FAILED sanity check 2: object " + o1 + " overlaps with object " + o2 + " in sentence " + o1.getSentence());
            overlappingEntities.add(o1);
            overlappingEntities.add(o2);
            fail = true;
          }
        }
      }
      if(fail){
        count2Fail ++;
      }
      else{
        count2Pass ++;
      }
    }
    if(REMOVE_OVERLAPPING_ENTITIES && overlappingEntities.size() > 0){
      Set<EventMention> eventsAffectedByCheck2 = new IdentityHashSet<EventMention>();
      logger.fine("Will remove entities: " + overlappingEntities);
      for(CoreMap sent: sentences){
        // remove any overlappingEntities from our ents
        List<EntityMention> ents = sent.get(EntityMentionsAnnotation.class);
        if(ents != null){
          List<EntityMention> newEnts = new ArrayList<EntityMention>();
          for(EntityMention e: ents){
            if(! overlappingEntities.contains(e)){
              newEnts.add(e);
            } else {
              count2EntsRemoved ++;
            }
          }
          sent.set(EntityMentionsAnnotation.class, newEnts);
        }

        // remove events if their anchor or arg(s) are in overlappingEntities
        List<EventMention> events = sent.get(EventMentionsAnnotation.class);
        if(events != null){
          for(EventMention e: events){
            // the anchor may overlap with something else
            if(overlappingEntities.contains(e.getAnchor())){
              logger.finest("Found event affected by sanity check 2: " + e);
              eventsAffectedByCheck2.add(e);
            }

            // an arg may overlap
            Set<ExtractionObject> argsToRemove = new IdentityHashSet<ExtractionObject>();
            logger.finest("Inspecting arguments of event: " + e);
            for(ExtractionObject a: e.getArgs()){
              if(overlappingEntities.contains(a)){
                logger.finest("Will remove argument " + a.getObjectId() + " from event " + e.getObjectId());
                argsToRemove.add(a);
              }
            }
            if(argsToRemove.size() > 0){
              e.removeArguments(argsToRemove, true);
              // remove the event only if it is left wo args
              if(e.getArgs().size() == 0 && removeZeroArgEvents){
                eventsAffectedByCheck2.add(e);
              }
            }
          }
        }
      }
      if(eventsAffectedByCheck2.size() > 0){
        MutableInteger count = new MutableInteger(0);
        removeEvents(sentences, eventsAffectedByCheck2, count);
        count2EventsRemoved += count.intValue();
      }
    }
    annotationsSummary(sentences, "Annotations after sanity check 2");

    //
    // CHECK 2.5
    // make sure events and parents have different anchors
    // if this happens remove the parent event
    //
    Set<EventMention> sameAnchorEvents = new IdentityHashSet<EventMention>();
    for(CoreMap sentence: sentences) {
      List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
      if(events != null){
        for(EventMention event: events) {
          boolean fail = checkArgsForSameAnchor(event, event.getAnchor());
          if(fail){
            sameAnchorEvents.add(event);
            logger.info("FAILED sanity check 2b: event " + event + " has same anchor as one child in sentence " + event.getSentence());
            count2bFail ++;
          }
          else {
            count2bPass ++;
          }
        }
      }
    }
    if(sameAnchorEvents.size() > 0){
      MutableInteger count = new MutableInteger(0);
      removeEvents(sentences, sameAnchorEvents, count, false);
      count2bEventsRemoved += count.intValue();
    }
    annotationsSummary(sentences, "Annotations after sanity check 2b");

    //
    // make sure no children have the same anchor as a parent
    //
    for(CoreMap sentence: sentences) {
      List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
      if(events != null){
        for(EventMention event: events) {
          assert(! checkArgsForSameAnchor(event, event.getAnchor()));
        }
      }
    }

    //
    // CHECK 3
    // do different events have the same anchor?
    // if so, merge all such events into a single one
    //
    Set<EventMention> eventsMerged = new IdentityHashSet<EventMention>();
    Set<EventMention> eventsSeen = new IdentityHashSet<EventMention>();
    for(CoreMap sentence: sentences) {
      List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
      if(events != null){
        for(int i = 0; i < events.size(); i ++){
          EventMention e1 = events.get(i);
          boolean fail = false;
          for(int j = i + 1; j < events.size(); j ++){
            EventMention e2 = events.get(j);
            if(eventsMerged.contains(e2)) continue;
            // found two events sharing the same id
            if(e1.getAnchor().getObjectId().equals(e2.getAnchor().getObjectId())){
              //reportBindingMultipleThemes(e1, e2);
              logger.info("FAILED sanity check 3: event " + e1 + " has same anchor as event " + e2 + " in sentence " + e1.getSentence());
              if(MERGE_SAME_ANCHOR_EVENTS){
                // reportMerge(e1, eventsSeen.contains(e1), e2, eventsSeen.contains(e2));
                e1.mergeEvent(e2, true);
                eventsMerged.add(e2);
                eventsSeen.add(e1);
                logger.fine("Merge successful.");
                logger.fine("First event after merge: " + e1);
              }
              fail = true;
            }
          }
          if(fail) count3Fail ++;
          else count3Pass ++;
        }
      }
    }
    annotationsSummary(sentences, "Annotations after sanity check 3 but before removing merged events");
    Set<String> mergedEventIds = new HashSet<String>();
    for(EventMention e: eventsMerged) mergedEventIds.add(e.getObjectId());
    logger.fine("Will remove following events: " + mergedEventIds);
    if(eventsMerged.size() > 0 && MERGE_SAME_ANCHOR_EVENTS){
      MutableInteger count = new MutableInteger(0);
      removeEvents(sentences, eventsMerged, count, false, eventsRemovedInCheck3);
      count3EventsRemoved += count.intValue();
    }
    annotationsSummary(sentences, "Annotations after sanity check 3");

    //
    // CHECK 4: can we have the same object as an argument in different events?
    //
    // first build a reverse index from args to events for this doc
    HashMap<ExtractionObject, Set<EventMention>> argsToEvents = new HashMap<ExtractionObject, Set<EventMention>>();
    for (CoreMap sentence : sentences) {
      List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
      if (events == null) {
        continue;
      }

      count4Total += events.size();
      for (EventMention event : events) {
        for (ExtractionObject arg : event.getArgs()) {
          Set<EventMention> myEvents = argsToEvents.get(arg);
          if(myEvents == null){
            myEvents = new IdentityHashSet<EventMention>();
            argsToEvents.put(arg, myEvents);
          }
          // add this event
          myEvents.add(event);
        }
      }
    }
    logger.fine("Inverted arg to event index:");
    Set<ExtractionObject> argKeysUnsorted = argsToEvents.keySet();
    ExtractionObject [] sortedArgs = sortById(argKeysUnsorted);
    for(ExtractionObject arg: sortedArgs) {
      Set<EventMention> myEvents = argsToEvents.get(arg);
      StringBuffer os = new StringBuffer();
      for(EventMention e: myEvents){
        os.append(" " + e.getObjectId());
      }
      logger.fine(arg.getObjectId() + " => " + os.toString());
    }

    // now for all args stored in multiple events keep only the event whose anchor is closest to this arg
    if(REMOVE_ARGS_FOR_ASSIGNED_OBJECTS){
      Set<EventMention> eventsToRemove = new IdentityHashSet<EventMention>();
      for(ExtractionObject arg: sortedArgs){
        Set<EventMention> events = argsToEvents.get(arg);
        Set<String> eventIds = new HashSet<String>();
        for(EventMention e: events) eventIds.add(e.getObjectId());
        if(events.size() == 1) continue;
        logger.info("FAILED sanity check 4:");
        logger.info("Found one arg with multiple events: " + arg.getObjectId() + " in events: " + events);
        assert(arg.getSentence() != null);
        logger.info("In sentence: " + arg.getSentence());
        Set<EventMention> sameSentEvents = removeEventsInOtherSentences(events, arg.getSentence());
        ExtractionObject [] sortedEvents = sortById(events);
        EventMention closest = null;
        int smallestDistance = Integer.MAX_VALUE;
        if(sameSentEvents.size() == 0){
          // we don't know how to measure distance to events in other sentences...
          // let's just pick the first event for now
          closest = (EventMention) sortedEvents[0];
          logger.severe("Found argument " + arg.getObjectId() + " in document " + arg.getSentence().get(DocIDAnnotation.class) + " linked only to cross-sentence events: " + eventIds + ". This needs better handling!");
        } else {
          for(ExtractionObject oe: sortedEvents){
            EventMention e = (EventMention) oe;
            if(! sameSentEvents.contains(e)) continue; // we want an event in the same sentence!
            int dist = Math.abs(e.getAnchor().getExtentTokenStart() - arg.getExtentTokenEnd());
            if(dist < smallestDistance){
              smallestDistance = dist;
              closest = e;
              logger.fine("Found candidate for closest event: " + e.getObjectId() + " at distance " + dist);
            }
          }
        }
        assert(closest != null);
        logger.fine("Closest event is: " + closest.getObjectId() + " at distance " + smallestDistance);
        for(ExtractionObject oe: sortedEvents){
          EventMention e = (EventMention) oe;
          if(e != closest){
            Set<ExtractionObject> toRemove = new IdentityHashSet<ExtractionObject>();
            toRemove.add(arg);
            e.removeArguments(toRemove, true);
            logger.fine("Other event after removing argument "+ arg.getObjectId() + ": " + e);
            count4ArgsRemoved ++;
            if(e.getArgs().size() == 0 && removeZeroArgEvents){
              eventsToRemove.add(e);
            }
          }
        }
        logger.fine("Events to remove contains: " + eventsToRemove);
        if(eventsToRemove.size() > 0){
          count4Fail += eventsToRemove.size();
          MutableInteger count = new MutableInteger(0);
          removeEvents(sentences, eventsToRemove, count, false, eventsRemovedInCheck4);
          count4EventsRemoved += count.intValue();

          // TODO: should we remove the corresponding anchors? no for now
        }
      }
    }
    annotationsSummary(sentences, "Annotations after sanity check 4");

    //
    // CHECK 5: we should not have events with zero arguments
    //
    if(removeZeroArgEvents){
      Set<EventMention> eventsWithZeroArguments = new IdentityHashSet<EventMention>();
      for (CoreMap sentence : sentences) {
        List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
        if (events == null) {
          continue;
        }

        for (EventMention event : events) {
          // first, a really basic meta-sanity check
          assert event.getArgs().size() == event.getArgNames().size();
          if (event.getArgs().size() < 1) {
            logger.info("FAILED sanity check 5: event " + event + " has zero arguments. This should not happen!");
            eventsWithZeroArguments.add(event);
            count5Fail++;
          } else {
            count5Pass++;
          }
        }
      }
      if (eventsWithZeroArguments.size() > 0 && REMOVE_EVENTS_WITH_NO_ARGS) {
        MutableInteger count = new MutableInteger(0);
        removeEvents(sentences, eventsWithZeroArguments, count);
        count5Removed += count.intValue();
      }
    }

    //
    // CHECK 6: events and parents must be in the same sentence
    //
    if(! documentLevelParsing){
      for (CoreMap sentence : sentences) {
        List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
        if (events != null) {
          for(EventMention e: events){
            for(ExtractionObject p: e.getParents()){
              if(p.getSentence() != e.getSentence()){
                logger.info("FAILED sanity check 6: event " + e + " in sentence " + e.getSentence().get(TextAnnotation.class) + " has parent: " + p + " in sentence " + p.getSentence().get(TextAnnotation.class));
              } else {
                logger.fine("PASSED sanity check 6");
              }
            }
          }
        }
      }
    }

    //
    // CHECK 7: event args must appear in the entity mention list
    //
    if(! documentLevelParsing) {
      for (CoreMap sentence : sentences) {
        List<EventMention> events = sentence.get(EventMentionsAnnotation.class);
        List<EntityMention> entities = sentence.get(EntityMentionsAnnotation.class);
        if (events != null) {
          for(EventMention e: events){
            List<ExtractionObject> args = e.getArgs();
            for(ExtractionObject a: args){
              if(a instanceof EntityMention) {
                EntityMention ent = (EntityMention) a;
                boolean found = false;
                for(EntityMention knownEnt: entities){
                  if(knownEnt == ent){
                    found = true;
                    break;
                  }
                }
                assert found == true : "Found unregistered event argument: " + a + " in entity list: " + entities;
              }
            }
          }
        }
      }
    }

    //
    // count remaining events and entities
    //
    for(CoreMap sent: sentences){
      totalSentences ++;
      if(sent.containsKey(EntityMentionsAnnotation.class))
        totalEntities += sent.get(EntityMentionsAnnotation.class).size();
      if(sent.containsKey(EventMentionsAnnotation.class))
        totalEvents += sent.get(EventMentionsAnnotation.class).size();
    }

    return sentences;
  }

  private static String anchorAndArgs(EventMention e) {
    StringBuilder os = new StringBuilder();
    os.append(e.getAnchor().getType());
    os.append("(");
    for(int i = 0; i < e.getArgs().size(); i ++){
      if(i > 0) os.append(", ");
      os.append(e.getArgNames().get(i));
    }
    os.append(")");
    return os.toString();
  }

  /*
  private void reportMerge(EventMention e1, boolean e1Seen, EventMention e2, boolean e2Seen) {
    List<EventMention> events = new ArrayList<EventMention>();
    if(e1.getAnchor().getType().compareTo(e2.getAnchor().getType()) <= 0){
      events.add(e1);
      events.add(e2);
    } else {
      events.add(e2);
      events.add(e1);
    }
    System.err.println("MERGING: " + events.get(0).anchorAndArgs(true) + " AND " + events.get(1).anchorAndArgs(true));

    if(e1.getAnchor().getType().toLowerCase().contains("regulation") && ! e1Seen){
      System.err.println("MERGING REGULATION: " + sigSeen(e1, e2));
    }
    if(e2.getAnchor().getType().toLowerCase().contains("regulation") && ! e2Seen){
      System.err.println("MERGING REGULATION: " + sigSeen(e2, e1));
    }
  }

  private String sigSeen(EventMention e, EventMention other) {
    StringBuffer os = new StringBuffer();
    os.append(e.getAnchor().getType() + "(");
    for(int i = 0; i < e.getArgNames().size(); i ++){
      os.append(e.getArgNames().get(i));
      boolean found = false;
      ExtractionObject arg = e.getArg(i);
      for(int j = 0; j < other.getArgs().size(); j ++){
        if(other.getArg(j).getObjectId().equals(arg.getObjectId())){
          found = true;
          break;
        }
      }
      os.append("{" + found + "}");
      os.append(" ");
    }
    os.append(")");
    return os.toString();
  }
  */

  private static Set<EventMention> removeEventsInOtherSentences(Set<EventMention> events, CoreMap sent) {
    Set<EventMention> sameSent = new IdentityHashSet<EventMention>();
    for(EventMention e: events){
      if(e.getSentence() == sent){
        sameSent.add(e);
      }
    }
    return sameSent;
  }

  public String annotationsSummaryAsString(List<CoreMap> sentences, String header){
    StringBuffer buf = new StringBuffer();
    buf.append(header + "\n");
    buf.append("Entity summary: ");
    for(CoreMap sent: sentences) {
      List<EntityMention> ents = sent.get(EntityMentionsAnnotation.class);
      if(ents != null){
        for(EntityMention e: ents){
          buf.append(e.getObjectId() + " ");
        }
      }
    }
    buf.append("\n");
    buf.append("Event summary: ");
    for(CoreMap sent: sentences) {
      List<EventMention> events = sent.get(EventMentionsAnnotation.class);
      if(events != null){
        for(EventMention e: events){
          buf.append(e.getObjectId() + "-" + e.getAnchor().getObjectId() + "(");
          for(int i = 0; i < e.getArgs().size(); i ++){
            if(i > 0) buf.append(",");
            buf.append(e.getArg(i).getObjectId());
          }
          buf.append(") ");
        }
      }
    }
    buf.append("\n");
    return buf.toString();
  }

  private static boolean checkArgsForSameAnchor(EventMention parent, ExtractionObject anchor){
    for(ExtractionObject arg: parent.getArgs()){
      if(arg instanceof EventMention){
        if(((EventMention) arg).getAnchor() == anchor) return true;
        if(checkArgsForSameAnchor((EventMention) arg, anchor)) return true;
      }
    }
    return false;
  }

  public void annotationsSummary(List<CoreMap> sentences, String header){
    logger.info(header);
    StringBuffer buf = new StringBuffer();
    for(CoreMap sent: sentences) {
      List<EntityMention> ents = sent.get(EntityMentionsAnnotation.class);
      if(ents != null){
        for(EntityMention e: ents){
          buf.append(e.getObjectId() + " ");
        }
      }
    }
    logger.info("Entity summary: " + buf.toString());

    buf = new StringBuffer();
    for(CoreMap sent: sentences) {
      List<EventMention> events = sent.get(EventMentionsAnnotation.class);
      if(events != null){
        for(EventMention e: events){
          buf.append(e.getObjectId() + "-" + e.getAnchor().getObjectId());
          for(ExtractionObject p: e.getParents()){
            buf.append("^" + p.getObjectId());
          }
          buf.append("(");
          for(int i = 0; i < e.getArgs().size(); i ++){
            if(i > 0) buf.append(",");
            buf.append(e.getArg(i).getObjectId());
          }
          buf.append(") ");
        }
      }
    }
    logger.info("Event summary: " + buf.toString());
  }

  void reportBindingMultipleThemes(EventMention e1, EventMention e2) {
    if(e1.getAnchor().getType().equals("Binding")){
      System.err.println("=======================================================================");
      System.err.println("Merging two binding events:");
      System.err.println("\tAnchor: " + e1.getAnchor());
      System.err.println("First arguments:");
      for(int k = 0; k < e1.getArgs().size(); k ++){
        EntityMention a = (EntityMention) e1.getArg(k);
        String an = e1.getArgNames().get(k);
        System.err.println("\t" + an + ":" + a.getFullValue());
      }
      System.err.println("Second arguments:");
      for(int k = 0; k < e2.getArgs().size(); k ++){
        EntityMention a = (EntityMention) e2.getArg(k);
        String an = e2.getArgNames().get(k);
        System.err.println("\t" + an + ":" + a.getFullValue());
      }
      List<CoreLabel> tokens = e1.getSentence().get(TokensAnnotation.class);
      for(int k = 0; k < tokens.size(); k ++){
        if(k == e1.getAnchor().getExtentTokenStart()){
          System.err.print("<<");
        }
        System.err.print(tokens.get(k).word());
        if(k == e1.getAnchor().getExtentTokenEnd() - 1){
          System.err.print(">>");
        }
        System.err.print(" ");
      }
      System.err.println("\n=======================================================================");
    }
  }

  private static ExtractionObject [] sortById(Set<? extends ExtractionObject> unsorted) {
    ExtractionObject [] sorted = new ExtractionObject[unsorted.size()];
    unsorted.toArray(sorted);
    Arrays.sort(sorted, new ByIdComparator());
    return sorted;
  }

  static class ByIdComparator implements Comparator<ExtractionObject> {

    public int compare(ExtractionObject o1, ExtractionObject o2) {
      if(o1.getObjectId().charAt(0) != o2.getObjectId().charAt(0)){
        if(o1.getObjectId().charAt(0) == 'T') return -1;
        return 1;
      }
      int v1 = Integer.parseInt(o1.getObjectId().substring(1));
      int v2 = Integer.parseInt(o2.getObjectId().substring(1));
      if(v1 < v2) return -1;
      if(v1 == v2) return 0;
      return 1;
    }

  }

  @SuppressWarnings("unused")
  private static void addSharing(List<Pair<EventMention, EventMention>> sharingEvents, EventMention e1, EventMention e2) {
    if(e1.getSentence() != e2.getSentence()) return;
    EventMention first = e1;
    EventMention second = e2;
    if(e2.getAnchor().getExtentTokenEnd() <= e1.getAnchor().getExtentTokenStart()){
      first = e2;
      second = e1;
    }
    for(Pair<EventMention, EventMention> p: sharingEvents){
      if(p.first == first && p.second == second) return;
    }
    sharingEvents.add(new Pair<EventMention, EventMention>(first, second));
  }

  @SuppressWarnings("unused")
  private static List<String> countShared(EventMention e1, EventMention e2){
    List<String> names = new ArrayList<String>();
    for(int i = 0; i < e1.getArgs().size(); i ++){
      ExtractionObject a1 = e1.getArgs().get(i);
      String n1 = e1.getArgNames().get(i);
      for(int j = 0; j < e2.getArgs().size(); j ++){
        ExtractionObject a2 = e2.getArgs().get(j);
        String n2 = e2.getArgNames().get(j);
        if(a1 == a2 && n1.equalsIgnoreCase(n2)){
          names.add(n1);
          break;
        }
      }
    }
    return names;
  }

  @SuppressWarnings("unused")
  private static boolean coordinated(ExtractionObject a1, ExtractionObject a2){
    if(a1.getSentence() != a2.getSentence()) return false;
    ExtractionObject first = a1;
    ExtractionObject second = a2;
    if(a2.getExtentTokenEnd() <= a1.getExtentTokenStart()){
      first = a2;
      second = a1;
    }
    if(first.getExtentTokenEnd() + 1 == second.getExtentTokenStart()){
      List<CoreLabel> tokens = first.getSentence().get(TokensAnnotation.class);
      if(tokens.get(first.getExtentTokenEnd() + 1).word().equalsIgnoreCase("and") ||
          tokens.get(first.getExtentTokenEnd() + 1).word().equalsIgnoreCase("or")){
        return true;
      }
    }
    return false;
  }

  private void removeEntities(List<CoreMap> sentences, Set<EntityMention> entitiesToRemove, MutableInteger count) {
    //
    // Do not remove entities if they are arguments of existing events
    // This happens in very few files:
    //   due to incorrect tokenization, anchors and valid arguments are merged
    //   so when removing unused anchors you may end up removing a valid argument as well
    //
    Set<String> args = new HashSet<String>();
    for(CoreMap sent: sentences){
      List<EventMention> events = sent.get(EventMentionsAnnotation.class);
      if(events == null) continue;
      for(EventMention ev: events){
        for(ExtractionObject e: ev.getArgs()){
          args.add(e.getObjectId());
        }
      }
    }

    logger.fine("Will remove the following entities:");
    for(EntityMention e: entitiesToRemove){
      if(! args.contains(e.getObjectId())){
        logger.fine(e.getObjectId() + ": " + e);
      }
    }
    for(CoreMap sent: sentences){
      List<EntityMention> entities = sent.get(EntityMentionsAnnotation.class);
      if(entities == null) continue;
      List<EntityMention> newEntities = new ArrayList<EntityMention>();

      for(EntityMention e: entities) {
        if(entitiesToRemove.contains(e) && ! args.contains(e.getObjectId())){
          logger.fine("REMOVING " + e);
          count.incValue(1);
        } else {
          newEntities.add(e);
        }
      }

      sent.set(EntityMentionsAnnotation.class, newEntities);
    }
  }

  private void removeEvents(List<CoreMap> sentences, Set<EventMention> eventsToRemove, MutableInteger count) {
    removeEvents(sentences, eventsToRemove, count, false, null);
  }

  private void removeEvents(List<CoreMap> sentences, Set<EventMention> eventsToRemove, MutableInteger count, boolean verbose) {
    removeEvents(sentences, eventsToRemove, count, verbose, null);
  }

  private void removeEvents(
      List<CoreMap> sentences,
      Set<EventMention> eventsToRemove,
      MutableInteger count,
      boolean verbose,
      Counter<String> eventStats) {
    Set<EventMention> noArgEvents = new IdentityHashSet<EventMention>();

    for(CoreMap sent: sentences){
      List<EventMention> events = sent.get(EventMentionsAnnotation.class);
      if(events == null) continue;
      List<EventMention> newEvents = new ArrayList<EventMention>();

      for(EventMention event: events) {
        if(eventsToRemove.contains(event) || event.getArgs().size() == 0){
          // will remove the event
          count.incValue(1);
          if(verbose) System.err.println("REMOVING " + event.getObjectId());
          if(eventStats != null) eventStats.incrementCount(event.getType());

          // remove it also from its parent arguments
          Set<ExtractionObject> parents = event.getParents();
          event.removeFromParents();

          // remove event from the parent set of all its args
          for(ExtractionObject arg: event.getArgs()){
            if(arg instanceof EventMention){
              ((EventMention) arg).removeParent(event);
            }
          }

          // attach my children to all my parents
          for(ExtractionObject parent: parents){
            if(parent instanceof EventMention){
              List<ExtractionObject> myArgs = event.getArgs();
              List<String> myArgNames = event.getArgNames();
              if(myArgs != null && myArgs.size() > 0){
                ((EventMention) parent).addArgs(myArgs, myArgNames, true);
                logger.fine("Removing one event from a chain > 2:");
                logger.fine("Parent event: " + parent);
                logger.fine("Current event to be removed: " + event);
                for(int i = 0; i < myArgs.size(); i ++){
                  logger.fine("Child #" + i + ": " + myArgNames.get(i) + " " + myArgs.get(i));
                }
              }
            }
          }

          // check if parent is left wo any children
          for(ExtractionObject parent: parents){
            if(parent instanceof EventMention &&
                ! eventsToRemove.contains(parent) &&
                ((EventMention) parent).getArgs().size() == 0){
              noArgEvents.add((EventMention) parent);
            }
          }

          if(verbose) annotationsSummary(sentences, "Annotations after removing event " + event.getObjectId());
        } else {
          // keep this event
          newEvents.add(event);
        }
      }

      sent.set(EventMentionsAnnotation.class, newEvents);
    }

    // remove all events that have no arguments
    if(noArgEvents.size() > 0){
      removeEvents(sentences, noArgEvents, count, verbose, eventStats);
    }
  }

  @SuppressWarnings("unused")
  private static boolean intersect(Span s1, Span s2) {
    if((s1.start() >= s2.start() && s1.start() < s2.end()) ||
        (s2.start() >= s1.start() && s2.start() < s1.end())){
      return true;
    }
    return false;
  }

  /**
   * Takes syntactic and POS annotations from the given tree
   * @param sentence
   * @param tree
   */
  private void generateSentenceAnnotations(CoreMap sentence, Tree tree) {
    // store the tree
    sentence.set(TreeAnnotation.class, tree);

    // set the POS tags from the tree pre-terminals
    List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
    List<Label> tags = tree.preTerminalYield();
    assert tags.size() == tokens.size() : "The number of tokens does not match the number of tree leaves in sentence: " + tokens + " and tree: " + tree.pennString();
    for(int i = 0; i < tokens.size(); i ++){
      CoreLabel token = tokens.get(i);
      Label tag = tags.get(i);
      // System.err.println(tag.value());
      token.set(PartOfSpeechAnnotation.class, tag.value());
    }
  }

  private List<Tree> readTrees(String fn) throws IOException {
    List<Tree> trees = new ArrayList<Tree>();
    for(String line: IOUtils.readLines(fn)){
      Tree t = (new PennTreeReader(new StringReader(line), new LabeledScoredTreeFactory(new StringLabelFactory()))).readTree();
      logger.finest("Read tree: " + t.pennString());
      trees.add(t);
    }
    return trees;
  }

  private Quadruple<String, EntityMention, Double, Double> getPredictedTrigger(String line, List<CoreMap> sentences) {
    assert line.startsWith(ENTITY_TYPE_PREFIX) : "Line \"" + line + "\" should be an entity annotation!";
    // example: T1 Protein 87 92 c-Fos  0.99 0
    String[] bigParts = line.split("\t");
    assert(bigParts.length == 3);
    String id = bigParts[0];
    String [] parts = bigParts[1].split("\\s+", 4);
    assert(parts.length == 4);
    String type = parts[0];
    int charStart = Integer.valueOf(parts[1]);
    int charEnd = Integer.valueOf(parts[2]);
    String text = parts[3];
    String [] probs = bigParts[2].split("\\s+");
    assert(probs.length == 2);
    double myProb = Double.valueOf(probs[0]);
    double firstProb = Double.valueOf(probs[1]);

    logger.finest("line: " + line);
    Pair<Span, CoreMap> spanAndSentence = convertCharacterToTokenSpan(sentences, charStart, charEnd);
    Span headSpan = spanAndSentence.first();
    CoreMap sentence = spanAndSentence.second();

    EntityMention entityMention = new EntityMention(
        id, sentence, headSpan, headSpan, type, null, null);

    // basic sanity check to make sure we read the right tokens
    String matched = entityMention.getValue().replaceAll("\\s+", "").replaceAll("-", "");
    String gold = text.replaceAll("\\s+", "").replaceAll("-", "");
    if (!matched.equals(gold)) {
      logger.severe("tokenization mismatch: extracted \"" + entityMention.getValue() + "\" but gold has \"" + text + "\"");
      throw new RuntimeException("tokenization mismatch: extracted \"" + entityMention.getValue() + "\" but gold has \"" + text + "\"");
    } else {
      logger.finest("perfect token match for entity \"" + entityMention.getValue() + "\" and gold has \"" + text + "\"");
    }

    // note that headSpan is used for extentSpan as well
    return new Quadruple<String, EntityMention, Double, Double>(id, entityMention, myProb, firstProb);
  }

  public static class ProbAnnotation implements CoreAnnotation<Double> {
    public Class<Double> getType() {
      return Double.class;
    }
  }
  public static class FirstProbAnnotation implements CoreAnnotation<Double> {
    public Class<Double> getType() {
      return Double.class;
    }
  }
  /**
   * In cases where predicted triggers are used, this will include the original gold annotation even if it differs from the predicted type (getType() will give you the predicted type only)
   */
  public static class OriginalGoldAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }
  public void addPredictedTriggers(List<CoreMap> sentences, File annotationFile)
    throws IOException {
    logger.fine("Reading predicted triggers from file: " + annotationFile.getAbsolutePath());

    BufferedReader in = new BufferedReader(new FileReader(annotationFile));
    String line;
    while ((line = in.readLine()) != null) {
      assert(line.startsWith(ENTITY_TYPE_PREFIX));
      Quadruple<String, EntityMention, Double, Double> trigInfo = getPredictedTrigger(line, sentences);
      String eid = trigInfo.first;
      EntityMention trigger = trigInfo.second;
      double myProb = trigInfo.third;
      double firstProb = trigInfo.fourth;

      // check if we have another entity with the same span
      // this means that the predicted trigger was correct
      // in this case, just assign the probs to the gold trigger
      // if necessary change the type to the predicted one
      EntityMention similar = findSameSpan(trigger);
      if(similar != null){
        similar.attributeMap().set(OriginalGoldAnnotation.class, similar.getType());

        // change the type of the gold entity
        if(! similar.getType().equals(trigger.getType())){
          similar.setType(trigger.getType());
          logger.fine("Found gold trigger with same span but different type: " + similar);
        } else {
          logger.fine("Found similar gold trigger: " + similar);
        }
        similar.attributeMap().set(ProbAnnotation.class, myProb);
        similar.attributeMap().set(FirstProbAnnotation.class, firstProb);
      } else {
        logger.fine("Found new trigger: " + trigger);
        trigger.attributeMap().set(ProbAnnotation.class, myProb);
        trigger.attributeMap().set(FirstProbAnnotation.class, firstProb);
        trigger.attributeMap().set(OriginalGoldAnnotation.class, "O");
        idsToEntities.put(eid, trigger);
        // add to the list of entities for this sentence
        CoreMap sent = trigger.getSentence();
        if(sent.get(EntityMentionsAnnotation.class) == null){
          List<EntityMention> ents = new ArrayList<EntityMention>();
          ents.add(trigger);
          sent.set(EntityMentionsAnnotation.class, ents);
        } else {
          sent.get(EntityMentionsAnnotation.class).add(trigger);
        }
      }
    }
  }

	private void addEntitiesAndEvents(List<CoreMap> sentences, File annotationFile, String rawText)
	    throws IOException {
		Pair<List<EntityMention>, List<EventMention>> entitiesAndEvents =
		  getEntitiesAndEvents(annotationFile, sentences);
		List<EntityMention> entities = entitiesAndEvents.first;
		List<EventMention> events = entitiesAndEvents.second;

		for (EntityMention entity : entities) {
			AnnotationUtils.addEntityMention(entity.getSentence(), entity);
		}
		for (EventMention event : events) {
			AnnotationUtils.addEventMention(event.getSentence(), event);
		}
	}

	/** if true, save our online tokenization to a file */
	private static final boolean SAVE_ONLINE_TOKENIZATION = false;

  private List<CoreMap> getSentencesFromText(String docId, String pathPrefix, String text) {
    List<CoreMap> tokenizedSents = new ArrayList<CoreMap>();

    if(useOfflineAnnotations){
      try {
        if(! text.endsWith(".")) text += ". ";
        BufferedReader is = new BufferedReader(new FileReader(pathPrefix + TOKENIZED_EXTENSION));
        String line;
        int textOffset = 0;
        while((line = is.readLine()) != null){
          Annotation sent = new Annotation(line);
          String [] stringToks = line.split("[ \t]+");
          List<CoreLabel> tokens = new ArrayList<CoreLabel>();
          for (String stringTok : stringToks) {
            CoreLabel token = new CoreLabel();
            token.setWord(stringTok);
            token.setOriginalText(stringTok);

            // get character offsets in the original text (needed to align mentions with text!)
            Pair<Integer, Integer> charPositions = new Pair<Integer, Integer>();
            textOffset = alignTokenToText(textOffset, text, stringTok, charPositions);
            token.set(CharacterOffsetBeginAnnotation.class, charPositions.first);
            token.set(CharacterOffsetEndAnnotation.class, charPositions.second);

            tokens.add(token);
          }
          sent.set(DocIDAnnotation.class, docId);
          sent.set(CoreAnnotations.TokensAnnotation.class, tokens); // AnnotationUtils.wordsToCoreLabels(tokens));
          tokenizedSents.add(sent);
        }
        is.close();
      } catch(IOException e) {
        System.err.println("ERROR: cannot read offline tokenization from " + pathPrefix + TOKENIZED_EXTENSION);
        e.printStackTrace();
        System.exit(1);
      }
    } else {
      String[] sentences = text.split("\\. ");
      int sentenceCharacterOffset = 0;
      PrintStream os = null;
      if(SAVE_ONLINE_TOKENIZATION){
        try {
          os = new PrintStream(new FileOutputStream(pathPrefix + "tokenized.stanford"));
        } catch(IOException e) {
          System.err.println("ERROR: cannot save online tokenization to " + pathPrefix + "tokenized.stanford");
          e.printStackTrace();
          System.exit(1);
        }
      }
      for (String sentence : sentences) {
        BioNLPTokenizer tokenizer = new BioNLPTokenizer(sentence + ". ");
        List<CoreLabel> tokens = tokenizer.tokenize();
        if(SAVE_ONLINE_TOKENIZATION) {
          for(CoreLabel l: tokens) {
            os.print(l.word() + " ");
          }
          os.println();
        }
        logger.fine("tokens: " + tokens.size() + " " + tokens);
        AnnotationUtils.updateOffsetsInCoreLabels(tokens, sentenceCharacterOffset);
        Annotation sent = new Annotation(sentence);
        sent.set(DocIDAnnotation.class, docId);
        sent.set(CoreAnnotations.TokensAnnotation.class, tokens); // AnnotationUtils.wordsToCoreLabels(tokens));
        tokenizedSents.add(sent);

        sentenceCharacterOffset += sentence.length() + 2; // 2 because of the period and space
      }
      if(SAVE_ONLINE_TOKENIZATION) {
        os.close();
      }
    }

    // assign SentenceIndex before returning
    for(int i = 0; i < tokenizedSents.size(); i ++){
      tokenizedSents.get(i).set(SentenceIndexAnnotation.class, i);
    }

    return tokenizedSents;
  }

  private static int alignTokenToText(int offset, String text, String token, Pair<Integer, Integer> charPositions) {
    String searchable = token;
    if(searchable.equals("-LRB-")) searchable = "(";
    else if(searchable.equals("-RRB-")) searchable = ")";
    else if(searchable.equals("-LSB-")) searchable = "[";
    else if(searchable.equals("-RSB-")) searchable = "]";

    int start = text.indexOf(searchable, offset);
    assert start >= offset : "Could not align token \"" + token + "\" to text \"" + text + "\"";
    charPositions.first = start;
    int end = start + searchable.length();
    charPositions.second = end;
    // System.err.println("Aligned token \"" + token + "\" to positions: " + start + ", " + end);
    return end;
  }

  private EntityMention findSameSpan(EntityMention e) {
    assert(e.getSentence() != null);
    List<CoreLabel> tokens = e.getSentence().get(TokensAnnotation.class);
    int eh = headPosition(e, tokens);
    for(EntityMention similar: idsToEntities.values()){
      if(similar.getSentence() == e.getSentence()){
        int sh = headPosition(similar, tokens);
        if(eh == sh){
          return similar;
        }
      }
    }
    return null;
  }

  private Pair<List<EntityMention>, List<EventMention>> getEntitiesAndEvents(
      File annotationFile,
      List<CoreMap> sentences)
      throws IOException {
    assert annotationFile.exists()
        && (annotationFile.getName().endsWith(ANNOTATIONS1) || annotationFile.getName().endsWith(ANNOTATIONS2))
        : "File " + annotationFile.getAbsolutePath() + " is not an annotation file!" ;
    logger.fine("Reading annotations from file: " + annotationFile.getAbsolutePath());

    List<EntityMention> entities = new ArrayList<EntityMention>();
    List<EventMention> events = new ArrayList<EventMention>();
    BufferedReader in = new BufferedReader(new FileReader(annotationFile));
    Map<String, String> eventLinesById = new HashMap<String, String>();
    Map<String, String> modificationLinesById = new HashMap<String, String>();
    String line = in.readLine();
    while (line != null) {
      if (line.startsWith(ENTITY_TYPE_PREFIX)) {
        Pair<String, EntityMention> idAndEntity = getEntityMention(line, sentences);

        // check if we have another entity with the same span
        // it may happen that the same span is marked as anchor for different events
        EntityMention similar = findSameSpan(idAndEntity.second);
        if(similar != null){
          // concatenate the types of the two similar entities
          similar.setType(ExtractionObject.concatenateTypes(similar.getType(), idAndEntity.second.getType()));
          idsToEntities.put(idAndEntity.first, similar);
          logger.fine("Found same span: " + similar);
        } else {
          logger.fine("Found new entity mention: " + idAndEntity.second);
          // accumulate ids and entities so we can match them to relations
          idsToEntities.put(idAndEntity.first, idAndEntity.second);
          entities.add(idAndEntity.second);
        }
      } else if (line.startsWith(EVENT_TYPE_PREFIX)) {
        // since events have forward references we first read in all the event
        // lines and then recursively create events
        Pair<String, String> idAndEventLine = getIdAndContent(line);
        eventLinesById.put(idAndEventLine.first, idAndEventLine.second);
      } else if (line.startsWith(EVENT_MODIFICATIONS)) {
        // since event modifications refer to events we need to first process events
        Pair<String, String> idAndEventLine = getIdAndContent(line);
        modificationLinesById.put(idAndEventLine.first, idAndEventLine.second);
      }

      line = in.readLine();
    }

    // now process events
    for (Map.Entry<String, String> entry : eventLinesById.entrySet()) {
      Pair<String, EventMention> idAndEvent = getEvent(entry.getKey(), entry.getValue(), eventLinesById);
      if (idAndEvent != null) {
        idsToEvents.put(idAndEvent.first, idAndEvent.second);
        events.add(idAndEvent.second);
      }
    }
    // now we can process event modifications
    for (Map.Entry<String, String> entry : modificationLinesById.entrySet()) {
      modifyEvent(entry.getKey(), entry.getValue());
    }

    in.close();

    for(EventMention em: events){
      logger.fine("Found event mention in reader: " + em);
    }

    return new Pair<List<EntityMention>, List<EventMention>>(entities, events);
  }

	private Pair<Span, CoreMap> convertCharacterToTokenSpan(List<CoreMap> sentences, int start, int end) {
		int tokenStart = -1;
		int tokenEnd = -1;
		int currentTokenIndex;
		CoreMap containingSentence = null; // the sentence that contains this span
		// we're going to iterate over all sentences and all tokens within them
		// until we find the tokens enclosed by start and end
		for (CoreMap sentence : sentences) {
			currentTokenIndex = 0;
			// check if we're done
  		if (tokenStart != -1 && tokenEnd != -1) {
  			break;
  		}

    	List<CoreLabel> currentTokens = sentence.get(TokensAnnotation.class);
    	for (CoreLabel currentToken : currentTokens) {
    		// check if we're done
    		if (tokenStart != -1 && tokenEnd != -1) {
    			break;
    		}

  			int currentStart = currentToken.get(CharacterOffsetBeginAnnotation.class);
  			int currentEnd = currentToken.get(CharacterOffsetEndAnnotation.class);
  			logger.finest("current token: " + currentTokenIndex + " " + currentToken);

    		// set start if appropriate
    		if (tokenStart == -1) {
    			// if the current token includes start (inclusive) set tokenStart
    			if (currentStart <= start && start < currentEnd) {
    				tokenStart = currentTokenIndex;
    				logger.fine("set start to " + currentTokenIndex);
    			}
    		}

    		// set end if appropriate
    		if (tokenEnd == -1) {
    			// if the current token includes start (inclusive) set tokenStart
    			if (currentStart < end && end <= currentEnd) {
    				tokenEnd = currentTokenIndex + 1;
					logger.fine("set end to " + tokenEnd);
					logger.fine("set sentence to " + sentence);
    				containingSentence = sentence;
    			}
    		}

    		currentTokenIndex += 1;
    	}
		}
		assert containingSentence != null;
    assert tokenStart >= 0 : "Could not find token start for span [" + start + ", " + end + ")";
    assert tokenEnd >= 0 : "Could not find token end for span [" + start + ", " + end + ")";
		return new Pair<Span, CoreMap>(new Span(tokenStart, tokenEnd), containingSentence);
	}

  private Pair<String, EntityMention> getEntityMention(String line, List<CoreMap> sentences) {
    assert line.startsWith(ENTITY_TYPE_PREFIX) : "Line \"" + line + "\" should be an entity annotation!";
    // example: T1 Protein 87 92 c-Fos
    // split on white space characters
    String[] parts = line.split("\\s+", 5);

    logger.fine("line: " + line);
		Pair<Span, CoreMap> spanAndSentence = convertCharacterToTokenSpan(sentences,
		    Integer.valueOf(parts[2]), Integer.valueOf(parts[3]));
		Span headSpan = spanAndSentence.first();
    CoreMap sentence = spanAndSentence.second();

    EntityMention entityMention = new EntityMention(
        parts[0], sentence, headSpan, headSpan, parts[1], null, null);

		// basic sanity check to make sure we read the right tokens
    String matched = entityMention.getValue().replaceAll("\\s+", "").replaceAll("-", "");
    String gold = parts[4].replaceAll("\\s+", "").replaceAll("-", "");
		if (!matched.equals(gold)) {
			logger.warning("tokenization mismatch: extracted \"" + entityMention.getValue() + "\" but gold has \"" + parts[4] + "\"");
		} else {
		  logger.fine("perfect token match for entity \"" + entityMention.getValue() + "\" and gold has \"" + parts[4] + "\"");
		}

		// note that headSpan is used for extentSpan as well
    return new Pair<String, EntityMention>(parts[0], entityMention);
  }

  private static Pair<String, String> getIdAndContent(String line) {
    String[] parts = line.split("\\s+");

    return new Pair<String, String>(parts[0], line);
  }

  private Pair<String, EventMention> getEvent(String eventId, String line, Map<String, String> eventLinesById) {
    assert line.startsWith(EVENT_TYPE_PREFIX) : "should be event annotation";

    EventMention existingEvent = this.idsToEvents.get(eventId);
    // we could have already parsed this since this is recursive
    if (existingEvent != null) {
      return new Pair<String, EventMention>(eventId, existingEvent);
    }

    // white space characters
    String[] parts = line.split("\\s+");
    // E12 Gene_expression:T23 Theme:T15

    List<ExtractionObject> mentions = new ArrayList<ExtractionObject>();
    List<String> mentionRoles = new ArrayList<String>();
    for (int ind = 1; ind < parts.length; ind++) {
      String[] entity = parts[ind].split(":");
      if (entity.length == 2) {
        String role = simplifyEdgeLabel(entity[0]);
        String entityId = entity[1];
        ExtractionObject mention = null;
        if (entityId.startsWith(ENTITY_TYPE_PREFIX)) {
          mention = idsToEntities.get(entityId);
        } else if (entityId.startsWith(EVENT_TYPE_PREFIX)) {
          // call recursively
          // if the parameter has been parsed it will be returned from the cache
          // otherwise it will be parsed in that call
          mention = getEvent(entityId, eventLinesById.get(entityId), eventLinesById).second;
        } else {
          // TODO: allow equivalences
          return null;
        }

        assert mention != null;
        mentions.add(mention);
        mentionRoles.add(role);
      } else {
        System.err.println("ERROR: invalid event line: " + line);
        System.exit(1);
      }
    }

    // start out with the extent of the first mention (which is really the anchor) and
    // extend it as needed
    Span span = new Span(mentions.get(0).getExtentTokenStart(),
                         mentions.get(0).getExtentTokenEnd());

    // (but first remove the anchor)
    ExtractionObject anchor = mentions.remove(0);
    String eventType = mentionRoles.remove(0);

    // now extend the span to include all mentions
    for (ExtractionObject mention : mentions) {
      span.setStart(Math.min(span.start(), mention.getExtentTokenStart()));
      span.setEnd(Math.max(span.end(), mention.getExtentTokenEnd()));
    }

    CoreMap sentence = anchor.getSentence();
    EventMention eventMention = new EventMention(eventId, sentence, span, eventType, null, anchor, mentions, mentionRoles);

    // add to cache
    this.idsToEvents.put(eventId, eventMention);
    return new Pair<String, EventMention>(eventId, eventMention);
  }

  private static String simplifyEdgeLabel(String l) {
    if(SIMPLIFY_EDGE_LABELS){
      if(l.equalsIgnoreCase("Theme2")) return "Theme";
    }
    return l;
  }

  private void modifyEvent(String modificationId, String line) {
    assert line.startsWith(EVENT_MODIFICATIONS) : "should be event annotaion";
    String[] parts = line.split("\\s");
    String eventModification = parts[1];
    String eventId = parts[2];

    EventMention em = idsToEvents.get(eventId);

    assert em != null;

    em.setModification(eventModification);
  }

  public static void main(String[] args) throws Exception {
    // simple testing code
    Properties props = StringUtils.argsToProperties(args);
    boolean useOfflineAnnotations = true;
    BioNLP09Reader reader = new BioNLP09Reader(useOfflineAnnotations, 0, true, false, true);
    reader.setProcessor(new StanfordCoreNLP(props, false));
    Level logLevel = Level.FINE;
    MachineReading.setConsoleLevel(logLevel);
    reader.setLoggerLevel(logLevel);
    // interesting docs:
    // 1537389 - has events spanning multiple sentences
    // 7927175 - has anchors with different ids but with the same span
    // 7858491 - has different events sharing the same anchor
    @SuppressWarnings("unused")
    Annotation doc = reader.parse("/u/nlp7/data/bioNLP09/development/");
    //Annotation doc = reader.parse("/Users/Mihai/corpora/bioNLP09/training/");
    // System.out.println(AnnotationUtils.datasetToString(doc));
    System.out.println("DONE!");
  }

  public static boolean isTrigger(ExtractionObject extractionObject) {
    if (!(extractionObject instanceof EntityMention)) {
      return false;
    }

    return isTrigger(extractionObject.getType());
  }

  public static boolean isTrigger(String s) {
    return !s.equalsIgnoreCase("protein");
  }
  /**
   * Returns true if an entity mention is in the gold data set.
   */
  public static boolean isGoldTrigger(ExtractionObject extractionObject) {
    if (!isTrigger(extractionObject)) {
      return false;
    }
    Double prob = extractionObject.attributeMap().get(ProbAnnotation.class);
    if (prob == null) {
      return true;
    }
    String goldTag = extractionObject.attributeMap().get(OriginalGoldAnnotation.class);
    return !goldTag.equals("O");
  }

  /**
   * Returns true if a gold trigger but not predicted by the trigger classifier
   */
  public static boolean isGoldTriggerOnly(ExtractionObject mention) {
    return isTrigger(mention) && mention.attributeMap().get(ProbAnnotation.class) == null;
  }

  /**
   * Computes the position of the head word in the original sentence
   */
  public static int headPosition(ExtractionObject obj, List<CoreLabel> tokens) {
    if (obj instanceof EntityMention) {
      if(isTrigger((EntityMention) obj)){
        return triggerHead((EntityMention) obj, tokens);
      } else {
        return entityHead((EntityMention) obj, tokens);
      }
    } else if (obj instanceof EventMention) {
      return triggerHead(((EventMention) obj).getAnchor(), tokens);
    } else {
      throw new RuntimeException("Unknown argument/anchor type.");
    }
  }

  private static String removeNonLetters(String s) {
    StringBuffer ns = new StringBuffer();
    for(int i = 0; i < s.length(); i ++){
      if(Character.isLetter(s.charAt(i))){
        ns.append(s.charAt(i));
      }
    }
    return ns.toString();
  }

  private static Set<String> greeks = new HashSet<String>(Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega"));

  public static int entityHead(ExtractionObject o, List<CoreLabel> tokens) {
    // entity of length 1
    if(o.getExtentTokenStart() + 1 == o.getExtentTokenEnd()){
      return o.getExtentTokenStart();
    }

    // find the right most token that obbeys several constraints
    for(int i = o.getExtentTokenEnd() - 1; i >= o.getExtentTokenStart(); i --){
      String w = tokens.get(i).word();

      // no parens, e.g., -LRB-
      if(w.startsWith("-") && w.endsWith("-") && w.length() > 2) continue;

      String lw = removeNonLetters(w).toLowerCase();

      // no no-letter tokens
      if(lw.length() < 1) continue;

      // no single letters unless they are the first token
      if(lw.length() == 1 && i > o.getExtentTokenStart()) continue;

      // no greek letters. these are clear modifiers
      if(greeks.contains(lw)) continue;

      // found something
      return i;
    }

    // if nothing works return right most token
    return o.getExtentTokenEnd() - 1;
  }

  public static int triggerHead(ExtractionObject o, List<CoreLabel> tokens) {
    // trigger of length 1
    if(o.getExtentTokenStart() + 1 == o.getExtentTokenEnd()){
      return o.getExtentTokenStart();
    }

    // if PP return the first IN
    if(tokens.get(o.getExtentTokenStart()).tag().equals("IN")){
      return o.getExtentTokenStart();
    }

    // return the right-most VB*
    for(int i = o.getExtentTokenEnd() - 1; i >= o.getExtentTokenStart(); i --){
      if(tokens.get(i).tag().startsWith("VB")){
        return i;
      }
    }

    // return the right-most NN*
    for(int i = o.getExtentTokenEnd() - 1; i >= o.getExtentTokenStart(); i --){
      if(tokens.get(i).tag().startsWith("NN")){
        return i;
      }
    }

    // most common multi-word triggers are nouns -> return right most token
    return o.getExtentTokenEnd() - 1;
  }

  /**
   * Implements BioNLP-specific tokenization rules while maintaining the original character offsets
   * Note: it is crucial to maintain the character offsets of the original tokens because they are used for aligning the annotations against text
   *
   * This is related to the NFL tokenizer and we could potentially unify the two.
   *
   * @author Mihai (original NFLTokenizer)
   * @author David McClosky
   *
   */
  private static class BioNLPTokenizer {
    AbstractTokenizer<CoreLabel> tokenizer;
    CoreLabelTokenFactory tokenFactory;
    /** If true, remove tokens that are standalone dashes. They are likely to hurt the parser. */
    private static final boolean DISCARD_STANDALONE_DASHES = true;

    // this is a list of all suffixes which we'll split off if they follow a dash
    // e.g. "X-induced" will be split into the three tokens "X", "-", and "induced"
		private static final HashSet<String> VALID_DASH_SUFFIXES = new HashSet<String>(
		    Arrays.asList(new String[] { "\\w+ed", "\\w+ing", "(in)?dependent",
		        "deficient", "response", "protein", "by", "specific", "like",
		        "inducible", "responsive", "gene", "mRNA", "transcription",
		        "sensitive", "bound", "driven", "positive", "negative", "dominant",
		        "family", "resistant", "activity", "proximal", "defective" }));
    private final Pattern dashSuffixes;

		// matches a word followed by a slash followed by a word
    private static final Pattern ANYSLASH_PATTERN = Pattern.compile("(\\w+)(/)(\\w+)");

    public BioNLPTokenizer(String buffer) {
      StringReader sr = new StringReader(buffer);
      String options = "ptb3Escaping=false";
      tokenFactory = new CoreLabelTokenFactory();
      tokenizer = new PTBTokenizer<CoreLabel>(sr, tokenFactory, options);

      String allSuffixes = makeRegexOr(VALID_DASH_SUFFIXES);
      String allSuffixesRegex = "(\\w+)(-)(" + allSuffixes + ")";
			dashSuffixes = Pattern.compile(allSuffixesRegex, Pattern.CASE_INSENSITIVE);

    }

    private String makeRegexOr(Iterable<String> pieces) {
      StringBuilder suffixBuilder = new StringBuilder();
      for (String suffix : pieces) {
      	if (suffixBuilder.length() > 0) {
      		suffixBuilder.append("|");
      	}
      	suffixBuilder.append("(" + suffix + ")");
      }
      return suffixBuilder.toString();
    }

    public List<CoreLabel> tokenize() {
      List<CoreLabel> tokens = tokenizer.tokenize();
      return postprocess(tokens);
    }

    protected List<CoreLabel> postprocess(List<CoreLabel> tokens) {
			// we can't use the regex "(anti)|(non)" since that will create an extra
			// group and confuse breakOnPattern, thus we do an extra pass
			tokens = breakOnPattern(tokens, Pattern.compile("(anti)(-)(\\w+)",
			    Pattern.CASE_INSENSITIVE));
			tokens = breakOnPattern(tokens, Pattern.compile("(non)(-)(\\w+)",
			    Pattern.CASE_INSENSITIVE));
      tokens = breakOnPattern(tokens, dashSuffixes);
      tokens = breakOnPattern(tokens, ANYSLASH_PATTERN);

      // re-join trailing or preceding - or + to previous digit
      tokens = joinSigns(tokens);

      // convert parens to normalized forms, e.g., -LRB-. better for parsing
      tokens = normalizeParens(tokens);

      return tokens;
    }

    private List<CoreLabel> joinSigns(List<CoreLabel> tokens) {
      List<CoreLabel> output = new ArrayList<CoreLabel>();
      for (int i = 0; i < tokens.size(); i++) {
        // -/-
        if(i < tokens.size() - 3 &&
            tokens.get(i).endPosition() == tokens.get(i + 1).beginPosition() &&
            tokens.get(i + 1).word().equals("-") &&
            tokens.get(i + 2).word().equals("/") &&
            tokens.get(i + 3).word().equals("-")){
          String word = tokens.get(i).word() +
            tokens.get(i + 1).word() +
            tokens.get(i + 2).word() +
            tokens.get(i + 3).word();
          output.add(tokenFactory.makeToken(word, tokens.get(i).beginPosition(), word.length()));
          i += 3;
          continue;
        }

        // - or +
        if(i < tokens.size() - 1){
          CoreLabel crt = tokens.get(i);
          CoreLabel nxt = tokens.get(i + 1);

          // trailing +
          if(crt.endPosition() == nxt.beginPosition() &&
              ! isParen(crt.word()) &&
              nxt.word().equals("+")){
            String word = crt.word() + nxt.word();
            output.add(tokenFactory.makeToken(word, crt.beginPosition(), word.length()));
            i ++;
            continue;
          }

          // trailing -
          if(crt.endPosition() == nxt.beginPosition() &&
              (i + 2 >= tokens.size() || nxt.endPosition() != tokens.get(i + 2).beginPosition()) &&
              ! isParen(crt.word()) &&
              nxt.word().equals("-")){
            String word = crt.word() + nxt.word();
            output.add(tokenFactory.makeToken(word, crt.beginPosition(), word.length()));
            i ++;
            continue;
          }

          // preceding -
          if(crt.endPosition() == nxt.beginPosition() &&
              (i == 0 || crt.beginPosition() != tokens.get(i - 1).endPosition()) &&
              ! isParen(nxt.word()) &&
              crt.word().equals("-")){
            String word = crt.word() + nxt.word();
            output.add(tokenFactory.makeToken(word, crt.beginPosition(), word.length()));
            i ++;
            continue;
          }
        }

        output.add(tokens.get(i));
      }
      return output;
    }

    private static final String [][] PARENS = {
      { "(", "-LRB-" },
      { ")", "-RRB-" },
      { "[", "-LSB-" },
      { "]", "-RSB-" }
    };

    private static boolean isParen(String s) {
      for(int j = 0; j < PARENS.length; j ++){
        if(s.equals(PARENS[j][0])){
          return true;
        }
      }
      return false;
    }

    private static List<CoreLabel> normalizeParens(List<CoreLabel> tokens) {
      for (int i = 0; i < tokens.size(); i++) {
        CoreLabel token = tokens.get(i);
        for(int j = 0; j < PARENS.length; j ++){
          if(token.word().equals(PARENS[j][0])){
            token.set(TextAnnotation.class, PARENS[j][1]);
            }
        }
      }
      return tokens;
    }

		private List<CoreLabel> breakOnPattern(List<CoreLabel> tokens, Pattern pattern) {
			List<CoreLabel> output = new ArrayList<CoreLabel>();
			for (int i = 0; i < tokens.size(); i++) {
				CoreLabel token = tokens.get(i);
				Matcher matcher = pattern.matcher(token.word());
				if (matcher.find()) {
					int sepPos = matcher.start(2);
					String s1 = token.word().substring(0, sepPos);
					if(! DISCARD_STANDALONE_DASHES || ! s1.equals("-")){
					  output.add(tokenFactory.makeToken(s1, token.beginPosition(), sepPos));
					}
					String sep = matcher.group(2);
					if(! DISCARD_STANDALONE_DASHES || ! sep.equals("-")){
					  output.add(tokenFactory.makeToken(sep, token.beginPosition() + sepPos, 1));
					}
					String s3 = token.word().substring(sepPos + 1);
					if(! DISCARD_STANDALONE_DASHES || ! s3.equals("-")){
					  output.add(tokenFactory.makeToken(s3, token.beginPosition() + sepPos + 1,
					    token.endPosition() - token.beginPosition() - sepPos - 1));
					}

				} else {
				  output.add(token);
				}
			}
			return output;
		}
  }
}