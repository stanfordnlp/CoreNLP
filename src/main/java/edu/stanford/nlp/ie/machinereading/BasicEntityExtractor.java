package edu.stanford.nlp.ie.machinereading; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.machinereading.structure.*;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;

/**
 * Uses parsed files to train classifier and test on data set.
 *
 * @author Andrey Gusev
 * @author Mason Smith
 * @author David McClosky (mcclosky@stanford.edu)
 */
public class BasicEntityExtractor implements Extractor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(BasicEntityExtractor.class);

  private static final long serialVersionUID = -4011478706866593869L;

  // non-final so we can do cross validation
  private CRFClassifier<CoreLabel> classifier;

  private static final Class<? extends CoreAnnotation<String>> annotationForWord = TextAnnotation.class;

  private static final boolean SAVE_CONLL_2003 = false;

  protected String gazetteerLocation;

  protected Set<String> annotationsToSkip;

  protected boolean useSubTypes;

  protected boolean useBIO;

  protected EntityMentionFactory entityMentionFactory;

  public final Logger logger;
  
  protected boolean useNERTags;
  
  public BasicEntityExtractor(
		  String gazetteerLocation,
		  boolean useSubTypes,
		  Set<String> annotationsToSkip,
		  boolean useBIO,
		  EntityMentionFactory factory, boolean useNERTags) {
    this.annotationsToSkip = annotationsToSkip;
    this.gazetteerLocation = gazetteerLocation;
    this.logger = Logger.getLogger(BasicEntityExtractor.class.getName());
    this.useSubTypes = useSubTypes;
    this.useBIO = useBIO;
    this.entityMentionFactory = factory;
    this.useNERTags = useNERTags;
  }

  /**
   * Annotate an ExtractionDataSet with entities. This will modify the
   * ExtractionDataSet in place.
   *
   * @param doc The dataset to label
   */
  @Override
  public void annotate(Annotation doc) {
    if(SAVE_CONLL_2003) {
      // dump a file in CoNLL-2003 format
      try {
        PrintStream os = new PrintStream(new FileOutputStream("test.conll"));
        List<List<CoreLabel>> labels = AnnotationUtils.entityMentionsToCoreLabels(doc, annotationsToSkip, useSubTypes, useBIO);
        BasicEntityExtractor.saveCoNLL(os, labels, true);
        // saveCoNLLFiles("/tmp/ace/test", doc, useSubTypes, useBIO);
        os.close();
      } catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    List<CoreMap> sents = doc.get(CoreAnnotations.SentencesAnnotation.class);
    int sentCount = 1;
    for (CoreMap sentence : sents) {
      if(useNERTags){
        this.makeAnnotationFromAllNERTags(sentence);
      } 
      else
        extractEntities(sentence, sentCount);
      sentCount ++;
    }

    /*
    if(SAVE_CONLL_2003){
      try {
        saveCoNLLFiles("test_output/", doc, useSubTypes, useBIO);
        log.info("useBIO = " + useBIO);
      } catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }
    */
  }

  public String getEntityTypeForTag(String tag){
    //need to be overridden by the extending class;
    return tag;
  }
  
  
  /**
   * Label entities in an ExtractionSentence. Assumes the classifier has already
   * been trained.
   *
   * @param sentence
   *          ExtractionSentence that we want to extract entities from
   *
   * @return an ExtractionSentence with text content, tree and entities set.
   *         Relations will not be set.
   */
  private CoreMap extractEntities(CoreMap sentence, int sentCount) {
    // don't add answer annotations
    List<CoreLabel> testSentence = AnnotationUtils.sentenceEntityMentionsToCoreLabels(sentence, false, annotationsToSkip, null, useSubTypes, useBIO);

    // now label the sentence
    List<CoreLabel> annotatedSentence = this.classifier.classify(testSentence);
    logger.finest("CLASSFIER OUTPUT: " + annotatedSentence);

    List<EntityMention> extractedEntities = new ArrayList<>();
    int i = 0;

    // variables which keep track of partially seen entities (i.e. we've seen
    // some but not all the words in them so far)
    String lastType = null;
    int startIndex = -1;

    //
    // note that labels may be in the BIO or just the IO format. we must handle both transparently
    //
    for (CoreLabel label : annotatedSentence) {
      String type = label.get(AnswerAnnotation.class);
      if (type.equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL)) {
        type = null;
      }

      // this is an entity end boundary followed by O
      if (type == null && lastType != null) {
        makeEntityMention(sentence, startIndex, i, lastType, extractedEntities, sentCount);
        logger.info("Found entity: " + extractedEntities.get(extractedEntities.size() - 1));
        startIndex = -1;
      }

      // entity start preceded by an O
      else if(lastType == null && type != null){
        startIndex = i;
      }

      // entity end followed by another entity of different type
      else if(lastType != null && type != null &&
          (type.startsWith("B-") ||
          (lastType.startsWith("I-") && type.startsWith("I-") && ! lastType.equals(type)) ||
          (notBIO(lastType) && notBIO(type) && ! lastType.equals(type)))){
        makeEntityMention(sentence, startIndex, i, lastType, extractedEntities, sentCount);
        logger.info("Found entity: " + extractedEntities.get(extractedEntities.size() - 1));
        startIndex = i;
      }

      lastType = type;
      i++;
    }

    // replace the original annotation with the predicted entities
    sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, extractedEntities);
    logger.finest("EXTRACTED ENTITIES: ");
    for(EntityMention e: extractedEntities){
      logger.finest("\t" + e);
    }

    postprocessSentence(sentence, sentCount);

    return sentence;
  }

  /*
   * Called by extractEntities after extraction is done. Override this method if
   * there are some cleanups you want to implement.
   */
  public void postprocessSentence(CoreMap sentence, int sentCount) {
    // nothing to do by default
  }

  /**
   * Converts NamedEntityTagAnnotation tags into {@link EntityMention}s. This
   * finds the longest sequence of NamedEntityTagAnnotation tags of the matching
   * type.
   *
   * @param sentence
   *          A sentence, ideally annotated with NamedEntityTagAnnotation
   * @param nerTag
   *          The name of the NER tag to copy, e.g. "DATE".
   * @param entityType
   *          The type of the {@link EntityMention} objects created
   */
  public void makeAnnotationFromGivenNERTag(CoreMap sentence, String nerTag, String entityType) {
    List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<EntityMention> mentions = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    assert words != null;
    assert mentions != null;
    
    for (int start = 0; start < words.size(); start ++) {
      int end;
      // find the first token after start that isn't of nerType
      for (end = start; end < words.size(); end ++) {
        String ne = words.get(end).get(NamedEntityTagAnnotation.class);
        if(! ne.equals(nerTag)){
          break;
        }
      }

      if (end > start) {
        
        // found a match!
        EntityMention m = entityMentionFactory.constructEntityMention(
            EntityMention.makeUniqueId(),
            sentence,
            new Span(start, end),
            new Span(start, end),
            entityType, null, null);
        logger.info("Created " + entityType + " entity mention: " + m);
        start = end - 1;
        mentions.add(m);
      }
    }

    sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, mentions);
  }

  
  /**
   * Converts NamedEntityTagAnnotation tags into {@link EntityMention}s. This
   * finds the longest sequence of NamedEntityTagAnnotation tags of the matching
   * type.
   *
   * @param sentence
   *          A sentence annotated with NamedEntityTagAnnotation
   */
  public void makeAnnotationFromAllNERTags(CoreMap sentence) {
    List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<EntityMention> mentions = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    assert words != null;
    if(mentions == null)
    {  
      this.logger.info("mentions are null");
      mentions = new ArrayList<>();
    }

    for (int start = 0; start < words.size(); start ++) {
      
      int end;
      // find the first token after start that isn't of nerType
      String lastneTag = null;
      String ne= null;
      for (end = start; end < words.size(); end ++) {
        ne = words.get(end).get(NamedEntityTagAnnotation.class);
        if(ne.equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL) || (lastneTag != null && !ne.equals(lastneTag))){
          break;
        }
        lastneTag = ne;
      }

      if (end > start) {
        
        // found a match!
        String entityType = this.getEntityTypeForTag(lastneTag);
        EntityMention m = entityMentionFactory.constructEntityMention(
            EntityMention.makeUniqueId(),
            sentence,
            new Span(start, end),
            new Span(start, end),
            entityType, null, null);
        //TODO: changed entityType in the above sentence to nerTag - Sonal
        logger.info("Created " + entityType + " entity mention: " + m);
        start = end - 1;
        mentions.add(m);
      }
    }

    sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, mentions);
  }

  private static boolean notBIO(String label) {
    return !(label.startsWith("B-") || label.startsWith("I-"));
  }

  public void makeEntityMention(CoreMap sentence, int start, int end, String label, List<EntityMention> entities, int sentCount) {
    assert(start >= 0);
    String identifier = makeEntityMentionIdentifier(sentence, sentCount, entities.size());
    EntityMention entity = makeEntityMention(sentence, start, end, label, identifier);
    entities.add(entity);
  }

  public static String makeEntityMentionIdentifier(CoreMap sentence, int sentCount, int entId) {
    String docid = sentence.get(CoreAnnotations.DocIDAnnotation.class);
    if(docid == null) docid = "EntityMention";
    String identifier = docid + "-" + entId + "-" + sentCount;
    return identifier;
  }

  public EntityMention makeEntityMention(CoreMap sentence, int start, int end, String label, String identifier) {
    Span span = new Span(start, end);
    String type = null, subtype = null;
    if(! label.startsWith("B-") && ! label.startsWith("I-")){
      type = label;
      subtype = null; // TODO: add support for subtypes! (needed at least in ACE)
    } else {
      type = label.substring(2);
      subtype = null; // TODO: add support for subtypes! (needed at least in ACE)
    }
    EntityMention entity = entityMentionFactory.constructEntityMention(identifier, sentence, span, span, type, subtype, null);
    Counter<String> probs = new ClassicCounter<>();
    probs.setCount(entity.getType(), 1.0);
    entity.setTypeProbabilities(probs);
    return entity;
  }

  // TODO not called any more, but possibly useful as a reference
  /**
   * This should be called after the classifier has been trained and
   * parseAndTrain has been called to accumulate test set
   *
   * This will return precision,recall and F1 measure
   */
  public void runTestSet(List<List<CoreLabel>> testSet) {
    Counter<String> tp = new ClassicCounter<>();
    Counter<String> fp = new ClassicCounter<>();
    Counter<String> fn = new ClassicCounter<>();

    Counter<String> actual = new ClassicCounter<>();

    for (List<CoreLabel> labels : testSet) {
      List<CoreLabel> unannotatedLabels = new ArrayList<>();
      // create a new label without answer annotation
      for (CoreLabel label : labels) {
        CoreLabel newLabel = new CoreLabel();
        newLabel.set(annotationForWord, label.get(annotationForWord));
        newLabel.set(PartOfSpeechAnnotation.class, label
            .get(PartOfSpeechAnnotation.class));
        unannotatedLabels.add(newLabel);
      }

      List<CoreLabel> annotatedLabels = this.classifier.classify(unannotatedLabels);

      int ind = 0;
      for (CoreLabel expectedLabel : labels) {

        CoreLabel annotatedLabel = annotatedLabels.get(ind);
        String answer = annotatedLabel.get(AnswerAnnotation.class);
        String expectedAnswer = expectedLabel.get(AnswerAnnotation.class);

        actual.incrementCount(expectedAnswer);

        // match only non background symbols
        if (!SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL
            .equals(expectedAnswer)
            && expectedAnswer.equals(answer)) {
          // true positives
          tp.incrementCount(answer);
          System.out.println("True Positive:" + annotatedLabel);
        } else if (!SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL.equals(answer)) {
          // false positives
          fp.incrementCount(answer);
          System.out.println("False Positive:" + annotatedLabel);
        } else if (!SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL
            .equals(expectedAnswer)) {
          // false negatives
          fn.incrementCount(expectedAnswer);
          System.out.println("False Negative:" + expectedLabel);
        } // else true negatives

        ind++;
      }
    }

    actual.remove(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
  }

  // XXX not called any more -- maybe lose annotationsToSkip entirely?
  /**
   *
   * @param annotationsToSkip
   *          The type of annotation to skip in assigning answer annotations
   */
  public void setAnnotationsToSkip(Set<String> annotationsToSkip) {
    this.annotationsToSkip = annotationsToSkip;
  }

  /*
   *  Model creation, saving, loading, and saving
   */
  public void train(Annotation doc) {
    List<List<CoreLabel>> trainingSet = AnnotationUtils.entityMentionsToCoreLabels(doc, annotationsToSkip, useSubTypes, useBIO);

    if(SAVE_CONLL_2003){
      // dump a file in CoNLL-2003 format
      try {
        PrintStream os = new PrintStream(new FileOutputStream("train.conll"));
        // saveCoNLLFiles("/tmp/ace/train/", doc, useSubTypes, useBIO);
        saveCoNLL(os, trainingSet, useBIO);
        os.close();
      } catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    this.classifier = createClassifier();
    if (trainingSet.size() > 0) {
      this.classifier.train(Collections.unmodifiableCollection(trainingSet));
    }
  }

  public static void saveCoNLLFiles(String dir, Annotation dataset, boolean useSubTypes, boolean alreadyBIO) throws IOException {
    List<CoreMap> sentences = dataset.get(CoreAnnotations.SentencesAnnotation.class);

    String docid = null;
    PrintStream os = null;
    for (CoreMap sentence : sentences) {
    	String myDocid = sentence.get(CoreAnnotations.DocIDAnnotation.class);
    	if(docid == null || ! myDocid.equals(docid)){
    		if(os != null){
    			os.close();
    		}
    		docid = myDocid;
    		os = new PrintStream(new FileOutputStream(dir + File.separator + docid + ".conll"));
    	}
      List<CoreLabel> labeledSentence = AnnotationUtils.sentenceEntityMentionsToCoreLabels(sentence, true, null, null, useSubTypes, alreadyBIO);
      assert(labeledSentence != null);

      String prev = null;
      for(CoreLabel word: labeledSentence) {
        String w = word.word().replaceAll("[ \t\n]+", "_");
        String t = word.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        String l = word.get(CoreAnnotations.AnswerAnnotation.class);
        String nl = l;
        if(! alreadyBIO && ! l.equals("O")){
          if(prev != null && l.equals(prev)) nl = "I-" + l;
          else nl = "B-" + l;
        }
        String line = w + " " + t + " " + nl;
        String [] toks = line.split("[ \t\n]+");
        if(toks.length != 3){
          throw new RuntimeException("INVALID LINE: \"" + line + "\"");
        }
        os.printf("%s %s %s\n", w, t, nl);
        prev = l;
      }
      os.println();
    }
    if(os != null){
    	os.close();
    }
  }

  public static void saveCoNLL(PrintStream os, List<List<CoreLabel>> sentences, boolean alreadyBIO) {
    os.println("-DOCSTART- -X- O\n");
    for(List<CoreLabel> sent: sentences){
      String prev = null;
      for(CoreLabel word: sent) {
        String w = word.word().replaceAll("[ \t\n]+", "_");
        String t = word.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        String l = word.get(CoreAnnotations.AnswerAnnotation.class);
        String nl = l;
        if(! alreadyBIO && ! l.equals("O")){
          if(prev != null && l.equals(prev)) nl = "I-" + l;
          else nl = "B-" + l;
        }
        String line = w + " " + t + " " + nl;
        String [] toks = line.split("[ \t\n]+");
        if(toks.length != 3){
          throw new RuntimeException("INVALID LINE: \"" + line + "\"");
        }
        os.printf("%s %s %s\n", w, t, nl);
        prev = l;
      }
      os.println();
    }
  }

  /*
   * Create the underlying classifier.
   */
  private CRFClassifier<CoreLabel> createClassifier() {
    Properties props = new Properties();
    props.setProperty("macro", "true"); // use a generic CRF configuration
    props.setProperty("useIfInteger", "true");
    props.setProperty("featureFactory", "edu.stanford.nlp.ie.NERFeatureFactory");
    props.setProperty("saveFeatureIndexToDisk", "false");
    if (this.gazetteerLocation != null) {
      log.info("Using gazetteer: " + this.gazetteerLocation);
      props.setProperty("gazette", this.gazetteerLocation);
      props.setProperty("sloppyGazette", "true");
    }
    return new CRFClassifier<>(props);
  }

  /**
   * Loads the model from disk.
   *
   * @param path
   *          The location of model that was saved to disk
   * @throws ClassCastException
   *           if model is the wrong format
   * @throws IOException
   *           if the model file doesn't exist or is otherwise
   *           unavailable/incomplete
   * @throws ClassNotFoundException
   *           this would probably indicate a serious classpath problem
   */
  public static BasicEntityExtractor load(String path, Class<? extends BasicEntityExtractor> entityClassifier, boolean preferDefaultGazetteer) throws ClassCastException, IOException, ClassNotFoundException {


    // load the additional arguments
    // try to load the extra file from the CLASSPATH first
    InputStream is = BasicEntityExtractor.class.getClassLoader().getResourceAsStream(path + ".extra");
    // if not found in the CLASSPATH, load from the file system
    if (is == null) is = new FileInputStream(path + ".extra");
    ObjectInputStream in = new ObjectInputStream(is);
    String gazetteerLocation = ErasureUtils.<String>uncheckedCast(in.readObject());
    if(preferDefaultGazetteer) gazetteerLocation = DefaultPaths.DEFAULT_NFL_GAZETTEER;
    Set<String> annotationsToSkip = ErasureUtils.<Set<String>>uncheckedCast(in.readObject());
    Boolean useSubTypes = ErasureUtils.<Boolean>uncheckedCast(in.readObject());
    Boolean useBIO = ErasureUtils.<Boolean>uncheckedCast(in.readObject());
    in.close();
    is.close();

    BasicEntityExtractor extractor = (BasicEntityExtractor) MachineReading.makeEntityExtractor(entityClassifier, gazetteerLocation);

    // load the CRF classifier (this works from any resource, e.g., classpath or file system)
    extractor.classifier = CRFClassifier.getClassifier(path);

    // copy the extra arguments
    extractor.annotationsToSkip = annotationsToSkip;
    extractor.useSubTypes = useSubTypes;
    extractor.useBIO = useBIO;

    return extractor;
  }

  public void save(String path) throws IOException {
    // save the CRF
    this.classifier.serializeClassifier(path);

    // save the additional arguments
    FileOutputStream fos = new FileOutputStream(path + ".extra");
    ObjectOutputStream out = new ObjectOutputStream(fos);
    out.writeObject(this.gazetteerLocation);
    out.writeObject(this.annotationsToSkip);
    out.writeObject(this.useSubTypes);
    out.writeObject(this.useBIO);
    out.close();
  }

  /*
   * Other helper functions
   */

  // TODO not called any more, but possibly useful as a reference
  /**
   * for printing labeled sentence in less verbose manner
   *
   * @return string for printing
   */
  public static String labeledSentenceToString(List<CoreLabel> labeledSentence,
      boolean printNer) {
    StringBuilder sb = new StringBuilder();
    sb.append("[ ");

    for (CoreLabel label : labeledSentence) {
      String word = label.getString(annotationForWord);
      String answer = label.getString(AnswerAnnotation.class);
      String tag = label.getString(PartOfSpeechAnnotation.class);

      sb.append(word).append("(").append(tag);
      if (!SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL.equals(answer)) {
        sb.append(" ").append(answer);
      }

      if (printNer) {
        sb.append(" ner:").append(label.ner());
      }
      sb.append(") ");
    }
    sb.append("]");

    return sb.toString();
  }

  public void setLoggerLevel(Level level) {
    logger.setLevel(level);
  }
}
