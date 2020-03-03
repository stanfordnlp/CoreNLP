package edu.stanford.nlp.ie.machinereading.domains.ace; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import edu.stanford.nlp.ie.machinereading.GenericDataSetReader;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceCharSeq;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceDocument;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceEntity;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceEntityMention;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceEventMention;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceRelationMention;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceRelationMentionArgument;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceToken;
import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.EventMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 *
 * Simple wrapper of Mihai's ACE code to ie.machinereading.structure objects.
 *
 * @author David McClosky
 *
 */
public class AceReader extends GenericDataSetReader  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AceReader.class);

  private final Counter<String> entityCounts;
  private final Counter<String> adjacentEntityMentions;
  private final Counter<String> relationCounts;
  private final Counter<String> nameRelationCounts;
  private final Counter<String> eventCounts;
  private final Counter<String> mentionTypeCounts;
  private final String aceVersion;
  private static final boolean VERBOSE = false;

  /**
   * Make an AceReader.
   */
  public AceReader() {
    this(null, true);
  }

  public AceReader(StanfordCoreNLP processor, boolean preprocess) {
    this(processor, preprocess, "ACE2005");
  }

  public AceReader(StanfordCoreNLP processor, boolean preprocess, String version) {
    super(processor, preprocess, false, true);

    entityCounts = new ClassicCounter<>();
    adjacentEntityMentions = new ClassicCounter<>();
    nameRelationCounts = new ClassicCounter<>();
    relationCounts = new ClassicCounter<>();
    eventCounts = new ClassicCounter<>();
    mentionTypeCounts = new ClassicCounter<>();

    logger = Logger.getLogger(AceReader.class.getName());
    // run quietly by default
    logger.setLevel(Level.SEVERE);

    aceVersion = version;
  }

  /**
   * Reads in ACE*.apf.xml files and converts them to RelationSentence objects.
   * Note that you probably should call parse() instead.
   *
   * Currently, this ignores document boundaries (the list returned will include
   * sentences from all documents).
   *
   * @param path directory containing ACE files to read (e.g.
   *          "/home/mcclosky/scr/data/ACE2005/english_test"). This can also be
   *          the path to a single file. *
   * @return list of RelationSentence objects
   */
  @Override
  public Annotation read(String path) throws IOException, SAXException, ParserConfigurationException {
    List<CoreMap> allSentences = new ArrayList<>();
    File basePath = new File(path);
    assert basePath.exists();
    Annotation corpus = new Annotation("");

    if (basePath.isDirectory()) {
      for (File aceFile : IOUtils.iterFilesRecursive(basePath, ".apf.xml")) {
        if (aceFile.getName().endsWith(".UPC1.apf.xml")) {
          continue;
        }
        allSentences.addAll(readDocument(aceFile, corpus));
      }
    } else {
      // in case it's a file
      allSentences.addAll(readDocument(basePath, corpus));
    }

    AnnotationUtils.addSentences(corpus, allSentences);

    // quick stats
    if (VERBOSE) {
      printCounter(entityCounts, "entity mention");
      printCounter(relationCounts, "relation mention");
      printCounter(eventCounts, "event mention");
    }


    for(CoreMap sent: allSentences){
      // check for entity mentions of the same type that are adjacent
      countAdjacentMentions(sent);
      // count relations between two proper nouns
      countNameRelations(sent);
      // count types of mentions
      countMentionTypes(sent);
    }
    if (VERBOSE) {
      printCounter(adjacentEntityMentions, "adjacent entity mention");
      printCounter(nameRelationCounts, "name relation mention");
      printCounter(mentionTypeCounts, "mention type counts");
    }

    return corpus;
  }

  private void countMentionTypes(CoreMap sent) {
    List<EntityMention> mentions = sent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    if(mentions != null){
      for(EntityMention m: mentions){
        mentionTypeCounts.incrementCount(m.getMentionType());
      }
    }
  }

  private void countNameRelations(CoreMap sent) {
    List<RelationMention> mentions = sent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    if(mentions != null){
      for(RelationMention m: mentions) {
        List<EntityMention> args = m.getEntityMentionArgs();
        if(args.size() == 2 && args.get(0).getMentionType().equals("NAM") && args.get(1).getMentionType().equals("NAM")){
          nameRelationCounts.incrementCount(m.getType() + "." + m.getSubType());
        }
      }
    }
  }

  private void countAdjacentMentions(CoreMap sent) {
    List<EntityMention> mentions = sent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    if(mentions != null){
      for(EntityMention m1: mentions){
        for(EntityMention m2: mentions){
          if(m1 == m2) continue;
          if(m1.getHeadTokenEnd() == m2.getHeadTokenStart() && m1.getType().equals(m2.getType())){
            adjacentEntityMentions.incrementCount(m1.getType());
          }
        }
      }
    }
  }

  // todo: Change to use a counters print method (get sorting for free!)
  private void printCounter(Counter<String> c, String h) {
    StringBuilder b = new StringBuilder();
    b.append(h).append(" counts:\n");
    Set<String> keys = c.keySet();
    for(String k: keys){
      b.append("\t").append(k).append(": ").append(c.getCount(k)).append("\n");
    }
    logger.info(b.toString());
  }

   /**
   * Reads in a single ACE*.apf.xml file and convert it to RelationSentence
   * objects. However, you probably should call parse() instead.
   *
   * @param file A file object of an ACE file
   * @return list of RelationSentence objects
   */
  private List<CoreMap> readDocument(File file, Annotation corpus) throws IOException, SAXException,
      ParserConfigurationException {
    // remove the extension to make it into a prefix
    String aceFilename = file.getAbsolutePath().replace(".apf.xml", "");
    List<CoreMap> sentencesFromFile = readDocument(aceFilename, corpus);
    return sentencesFromFile;
  }

  /**
   * Reads in a single ACE*.apf.xml file and convert it to RelationSentence
   * objects. However, you probably should call parse() instead.
   *
   * @param prefix prefix of ACE filename to read (e.g.
   *          "/u/mcclosky/scr/data/ACE2005/english_test/bc/CNN_CF_20030827.1630.01"
   *          ) (no ".apf.xml" extension)
   * @return list of RelationSentence objects
   */
  private List<CoreMap> readDocument(String prefix, Annotation corpus) throws IOException, SAXException,
      ParserConfigurationException {
    logger.info("Reading document: " + prefix);
    List<CoreMap> results = new ArrayList<>();
    AceDocument aceDocument;
    if(aceVersion.equals("ACE2004")){
      aceDocument = AceDocument.parseDocument(prefix, false, aceVersion);
    } else {
      aceDocument = AceDocument.parseDocument(prefix, false);
    }
    String docId = aceDocument.getId();

    // map entity mention ID strings to their EntityMention counterparts
    Map<String, EntityMention> entityMentionMap = Generics.newHashMap();

    /*
    for (int sentenceIndex = 0; sentenceIndex < aceDocument.getSentenceCount(); sentenceIndex++) {
      List<AceToken> tokens = aceDocument.getSentence(sentenceIndex);
      StringBuilder b = new StringBuilder();
      for(AceToken t: tokens) b.append(t.getLiteral() + " " );
      logger.info("SENTENCE: " + b.toString());
    }
    */

    int tokenOffset = 0;
    for (int sentenceIndex = 0; sentenceIndex < aceDocument.getSentenceCount(); sentenceIndex++) {
      List<AceToken> tokens = aceDocument.getSentence(sentenceIndex);

      List<CoreLabel> words = new ArrayList<>();
      StringBuilder textContent = new StringBuilder();
      for(int i = 0; i < tokens.size(); i ++){
        CoreLabel l = new CoreLabel();
        l.setWord(tokens.get(i).getLiteral());
        l.set(CoreAnnotations.ValueAnnotation.class, l.word());
        l.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, tokens.get(i).getByteStart());
        l.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, tokens.get(i).getByteEnd());
        words.add(l);
        if(i > 0) textContent.append(" ");
        textContent.append(tokens.get(i).getLiteral());
      }

      // skip "sentences" that are really just SGML tags (which come from using the RobustTokenizer)
      if (words.size() == 1) {
        String word = words.get(0).word();
        if (word.startsWith("<") && word.endsWith(">")) {
          tokenOffset += tokens.size();
          continue;
        }
      }

      CoreMap sentence = new Annotation(textContent.toString());
      sentence.set(CoreAnnotations.DocIDAnnotation.class, docId);
      sentence.set(CoreAnnotations.TokensAnnotation.class, words);
      logger.info("Reading sentence: \"" + textContent + "\"");

      List<AceEntityMention> entityMentions = aceDocument.getEntityMentions(sentenceIndex);
      List<AceRelationMention> relationMentions = aceDocument.getRelationMentions(sentenceIndex);
      List<AceEventMention> eventMentions = aceDocument.getEventMentions(sentenceIndex);

      // convert entity mentions
      for (AceEntityMention aceEntityMention : entityMentions) {
        String corefID="";
        for(String entityID : aceDocument.getKeySetEntities()){
          AceEntity e = aceDocument.getEntity(entityID);
          if(e.getMentions().contains(aceEntityMention)){
            corefID = entityID;
            break;
          }
        }
        EntityMention convertedMention = convertAceEntityMention(aceEntityMention, docId, sentence, tokenOffset, corefID);
//        EntityMention convertedMention = convertAceEntityMention(aceEntityMention, docId, sentence, tokenOffset);
        entityCounts.incrementCount(convertedMention.getType());
        logger.info("CONVERTED MENTION HEAD SPAN: " + convertedMention.getHead());
        logger.info("CONVERTED ENTITY MENTION: " + convertedMention);
        AnnotationUtils.addEntityMention(sentence, convertedMention);
        entityMentionMap.put(aceEntityMention.getId(), convertedMention);

        // TODO: make Entity objects as needed
      }

      // convert relation mentions
      for (AceRelationMention aceRelationMention : relationMentions) {
        RelationMention convertedMention = convertAceRelationMention(aceRelationMention, docId, sentence, entityMentionMap);
        if(convertedMention != null){
          relationCounts.incrementCount(convertedMention.getType());
          logger.info("CONVERTED RELATION MENTION: " + convertedMention);
          AnnotationUtils.addRelationMention(sentence, convertedMention);
        }

        // TODO: make Relation objects
      }

      // convert EventMentions
      for(AceEventMention aceEventMention: eventMentions){
        EventMention convertedMention = convertAceEventMention(aceEventMention, docId, sentence, entityMentionMap, tokenOffset);
        if(convertedMention != null){
          eventCounts.incrementCount(convertedMention.getType());
          logger.info("CONVERTED EVENT MENTION: " + convertedMention);
          AnnotationUtils.addEventMention(sentence, convertedMention);
        }

        // TODO: make Event objects
      }

      results.add(sentence);
      tokenOffset += tokens.size();
    }
    return results;
  }

  private EventMention convertAceEventMention(
      AceEventMention aceEventMention, String docId,
      CoreMap sentence, Map<String, EntityMention> entityMap,
      int tokenOffset) {
    Set<String> roleSet = aceEventMention.getRoles();
    List<String> roles = new ArrayList<>();
    for(String role: roleSet) roles.add(role);
    List<ExtractionObject> convertedArgs = new ArrayList<>();

    int left = Integer.MAX_VALUE;
    int right = Integer.MIN_VALUE;
    for(String role: roles){
      AceEntityMention arg = aceEventMention.getArg(role);
      ExtractionObject o = entityMap.get(arg.getId());
      if(o == null){
        logger.severe("READER ERROR: Failed to find event argument with id " + arg.getId());
        logger.severe("This happens because a few event mentions illegally span multiple sentences. Will ignore this mention.");
        return null;
      }
      convertedArgs.add(o);
      if(o.getExtentTokenStart() < left) left = o.getExtentTokenStart();
      if(o.getExtentTokenEnd() > right) right = o.getExtentTokenEnd();
    }

    AceCharSeq anchor = aceEventMention.getAnchor();
    ExtractionObject anchorObject = new ExtractionObject(
        aceEventMention.getId() + "-anchor",
        sentence,
        new Span(anchor.getTokenStart() - tokenOffset, anchor.getTokenEnd() + 1 - tokenOffset),
        "ANCHOR",
        null);

    EventMention em = new EventMention(
        aceEventMention.getId(),
        sentence,
        new Span(left, right),
        aceEventMention.getParent().getType(),
        aceEventMention.getParent().getSubtype(),
        anchorObject,
        convertedArgs,
        roles);
    return em;
  }

  private RelationMention convertAceRelationMention(AceRelationMention aceRelationMention, String docId,
      CoreMap sentence, Map<String, EntityMention> entityMap) {
    List<AceRelationMentionArgument> args = Arrays.asList(aceRelationMention.getArgs());
    List<ExtractionObject> convertedArgs = new ArrayList<>();
    List<String> argNames = new ArrayList<>();

    // the arguments are already stored in semantic order. Make sure we preserve the same ordering!
    int left = Integer.MAX_VALUE;
    int right = Integer.MIN_VALUE;
    for (AceRelationMentionArgument arg : args) {
      ExtractionObject o = entityMap.get(arg.getContent().getId());
      if(o == null){
        logger.severe("READER ERROR: Failed to find relation argument with id " + arg.getContent().getId());
        logger.severe("This happens because a few relation mentions illegally span multiple sentences. Will ignore this mention.");
        return null;
      }
      convertedArgs.add(o);
      argNames.add(arg.getRole());
      if(o.getExtentTokenStart() < left) left = o.getExtentTokenStart();
      if(o.getExtentTokenEnd() > right) right = o.getExtentTokenEnd();
    }

    if(argNames.size() != 2 || ! argNames.get(0).equalsIgnoreCase("arg-1") || ! argNames.get(1).equalsIgnoreCase("arg-2")){
      logger.severe("READER ERROR: Invalid succession of arguments in relation mention: " + argNames);
      logger.severe("ACE relations must have two arguments. Will ignore this mention.");
      return null;
    }

    RelationMention relation = new RelationMention(
        aceRelationMention.getId(),
        sentence,
        new Span(left, right),
        aceRelationMention.getParent().getType(),
        aceRelationMention.getParent().getSubtype(),
        convertedArgs,
        null);
    return relation;
  }

  /**
   * Convert an {@link AceEntityMention} to an {@link EntityMention}.
   *
   * @param entityMention {@link AceEntityMention} to convert
   * @param docId ID of the document containing this entity mention
   * @param sentence
   * @param tokenOffset An offset in the calculations of position of the extent to sentence boundary
   *                    (the ace.reader stores absolute token offset from the beginning of the document, but
   *                    we need token offsets from the beginning of the sentence => adjust by tokenOffset)
   * @return entity as an {@link EntityMention}
   */
  private EntityMention convertAceEntityMention(AceEntityMention entityMention, String docId, CoreMap sentence, int tokenOffset) {
    //log.info("TYPE is " + entityMention.getParent().getType());
    //log.info("SUBTYPE is " + entityMention.getParent().getSubtype());
    //log.info("LDCTYPE is " + entityMention.getLdctype());

    AceCharSeq ext = entityMention.getExtent();
    AceCharSeq head = entityMention.getHead();

    int extStart = ext.getTokenStart() - tokenOffset;
    int extEnd = ext.getTokenEnd() - tokenOffset + 1;
    if (extStart < 0) {
      logger.severe("READER ERROR: Invalid extent start " + extStart + " for entity mention " + entityMention.getId() + " in document " + docId + " in sentence " + sentence);
      logger.severe("This may happen due to incorrect EOS detection. Adjusting entity extent.");
      extStart = 0;
    }
    if (extEnd > sentence.get(CoreAnnotations.TokensAnnotation.class).size()) {
      logger.severe("READER ERROR: Invalid extent end " + extEnd + " for entity mention " + entityMention.getId() + " in document " + docId + " in sentence " + sentence);
      logger.severe("This may happen due to incorrect EOS detection. Adjusting entity extent.");
      extEnd = sentence.get(CoreAnnotations.TokensAnnotation.class).size();
    }

    int headStart = head.getTokenStart() - tokenOffset;
    int headEnd = head.getTokenEnd() - tokenOffset + 1;
    if (headStart < 0) {
      logger.severe("READER ERROR: Invalid head start " + headStart + " for entity mention " + entityMention.getId() + " in document " + docId + " in sentence " + sentence);
      logger.severe("This may happen due to incorrect EOS detection. Adjusting entity head span.");
      headStart = 0;
    }
    if(headEnd > sentence.get(CoreAnnotations.TokensAnnotation.class).size()){
      logger.severe("READER ERROR: Invalid head end " + headEnd + " for entity mention " + entityMention.getId() + " in document " + docId + " in sentence " + sentence);
      logger.severe("This may happen due to incorrect EOS detection. Adjusting entity head span.");
      headEnd = sentence.get(CoreAnnotations.TokensAnnotation.class).size();
    }

    // must adjust due to possible incorrect EOS detection
    if(headStart < extStart){
      headStart = extStart;
    }
    if(headEnd > extEnd){
      headEnd = extEnd;
    }
    assert(headStart < headEnd);

    // note: the ace.reader stores absolute token offset from the beginning of the document, but
    //       we need token offsets from the beginning of the sentence => adjust by tokenOffset
    // note: in ace.reader the end token position is inclusive, but
    //       in our setup the end token position is exclusive => add 1 to end
    EntityMention converted = new EntityMention(
        entityMention.getId(),
        sentence,
        new Span(extStart, extEnd),
        new Span(headStart, headEnd),
        entityMention.getParent().getType(),
        entityMention.getParent().getSubtype(),
        entityMention.getLdctype());
    return converted;
  }

  private EntityMention convertAceEntityMention(AceEntityMention entityMention, String docId, CoreMap sentence, int tokenOffset, String corefID) {
    EntityMention converted = convertAceEntityMention(entityMention, docId, sentence, tokenOffset);
    converted.setCorefID(corefID);
    return converted;
  }

  // simple testing code
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    AceReader r = new AceReader(new StanfordCoreNLP(props, false), false);
    r.setLoggerLevel(Level.INFO);
    r.parse("/u/scr/nlp/data/ACE2005/");
    // Annotation a = r.parse("/user/mengqiu/scr/twitter/nlp/corpus_prep/standalone/ar/data");
    // BasicEntityExtractor.saveCoNLLFiles("/tmp/conll", a, false, false);
    log.info("done");
  }

}
