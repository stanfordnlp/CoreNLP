package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.paragraphs.ParagraphAnnotator;
import edu.stanford.nlp.quoteattribution.ChapterAnnotator;
import edu.stanford.nlp.quoteattribution.Person;
import edu.stanford.nlp.quoteattribution.QuoteAttributionUtils;
import edu.stanford.nlp.quoteattribution.Sieves.MSSieves.BaselineTopSpeakerSieve;
import edu.stanford.nlp.quoteattribution.Sieves.MSSieves.DeterministicSpeakerSieve;
import edu.stanford.nlp.quoteattribution.Sieves.MSSieves.LooseConversationalSpeakerSieve;
import edu.stanford.nlp.quoteattribution.Sieves.MSSieves.MSSieve;
import edu.stanford.nlp.quoteattribution.Sieves.MSSieves.MajoritySpeakerSieve;
import edu.stanford.nlp.quoteattribution.Sieves.QMSieves.*;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;


/**
 * @author Grace Muzny, Michael Fang
 */
public class QuoteAttributionAnnotator implements Annotator {

  public static class MentionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MentionBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  public static class MentionEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }
  public static class MentionTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MentionSieveAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }
  public static class SpeakerAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() { return String.class; }
  }
  public static class SpeakerSieveAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() { return String.class; }
  }

  private static Redwood.RedwoodChannels log = Redwood.channels(QuoteAttributionAnnotator.class);

  // settings
  public static final String DEFAULT_QMSIEVES = "tri,dep,onename,voc,paraend,conv,sup,loose";
  public static final String DEFAULT_MSSIEVES = "det,top";
  public static final String DEFAULT_MODEL_PATH = "model.ser";

  // these paths go in the props file
  public static String FAMILY_WORD_LIST = "";
  public static String ANIMACY_WORD_LIST = "";
  public static String GENDER_WORD_LIST = "";
  public static String COREF_PATH = "";
  public static String MODEL_PATH = "";
  public static String CHARACTERS_FILE = "";

  public static final Boolean VERBOSE = true;

  // fields
  private Set<String> animacyList;
  private Set<String> familyRelations;
  private Map<String, Person.Gender> genderMap;
  private Map<String, List<Person>> characterMap;
  private String qmSieveList;
  private String msSieveList;

  public QuoteAttributionAnnotator(Properties props) {
    Timing timer = null;
    COREF_PATH = props.getProperty("booknlpCoref", null);
    if(COREF_PATH == null) {
      log.err("Warning: no coreference map!");
    }
    MODEL_PATH = props.getProperty("modelPath", DEFAULT_MODEL_PATH);
    CHARACTERS_FILE = props.getProperty("charactersPath", null);
    if(CHARACTERS_FILE == null) {
      log.err("Warning: no characters file!");
    }
    qmSieveList = props.getProperty("QMSieves", DEFAULT_QMSIEVES);
    msSieveList = props.getProperty("MSSieves", DEFAULT_MSSIEVES);

    if (VERBOSE) {
      timer = new Timing();
      log.info("Loading QuoteAttribution coref [" + COREF_PATH + "]...");
      log.info("Loading QuoteAttribution characters [" + CHARACTERS_FILE + "]...");
    }
    // loading all our word lists
    FAMILY_WORD_LIST = props.getProperty("familyWordsFile", null);
    ANIMACY_WORD_LIST = props.getProperty("animacyWordsFile", null);
    GENDER_WORD_LIST = props.getProperty("genderNamesFile", null);
    familyRelations = QuoteAttributionUtils.readFamilyRelations(FAMILY_WORD_LIST);
    genderMap = QuoteAttributionUtils.readGenderedNounList(GENDER_WORD_LIST);
    animacyList = QuoteAttributionUtils.readAnimacyList(ANIMACY_WORD_LIST);
    characterMap = QuoteAttributionUtils.readPersonMap(CHARACTERS_FILE);
    if (VERBOSE) {
      timer.stop("done.");
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    // 0. pre-preprocess the text with paragraph annotations
    // TODO: maybe move this out, definitely make it so that you can set paragraph breaks
    Properties propsPara = new Properties();
    propsPara.setProperty("paragraphBreak", "one");
    ParagraphAnnotator pa = new ParagraphAnnotator(propsPara, false);
    pa.annotate(annotation);

    // 1. preprocess the text
    // a) setup coref
    Map<Integer, String> pronounCorefMap =
        QuoteAttributionUtils.setupCoref(COREF_PATH, characterMap, annotation);

    //annotate chapter numbers in sentences. Useful for denoting chapter boundaries
    new ChapterAnnotator().annotate(annotation);
    // to incorporate sentences across paragraphs
    QuoteAttributionUtils.addEnhancedSentences(annotation);
    //annotate depparse of quote-removed sentences
    QuoteAttributionUtils.annotateForDependencyParse(annotation);
    Annotation preprocessed = annotation;

    // 2. Quote->Mention annotation
    Map<String, QMSieve> qmSieves = getQMMapping(preprocessed, pronounCorefMap);
    for(String sieveName : qmSieveList.split(",")) {
      qmSieves.get(sieveName).doQuoteToMention(preprocessed);
    }

    // 3. Mention->Speaker annotation
    Map<String, MSSieve> msSieves = getMSMapping(preprocessed, pronounCorefMap);
    for(String sieveName : msSieveList.split(",")) {
      msSieves.get(sieveName).doMentionToSpeaker(preprocessed);
    }
  }

  private Map<String, QMSieve> getQMMapping(Annotation doc, Map<Integer, String> pronounCorefMap) {
    Map<String, QMSieve> map = new HashMap<>();
    map.put("tri", new TrigramSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("dep", new DependencyParseSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("onename", new OneNameSentenceSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("voc", new VocativeSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("paraend", new ParagraphEndQuoteClosestSieve(doc, characterMap, pronounCorefMap, animacyList));
    SupervisedSieve ss =  new SupervisedSieve(doc, characterMap, pronounCorefMap, animacyList);
    ss.loadModel(MODEL_PATH);
    map.put("sup", ss);
    map.put("conv", new ConversationalSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("loose", new LooseConversationalSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("closest", new ClosestMentionSieve(doc, characterMap, pronounCorefMap, animacyList));
    return map;
  }

  private Map<String, MSSieve> getMSMapping(Annotation doc, Map<Integer, String> pronounCorefMap) {
    Map<String, MSSieve> map = new HashMap<>();
    map.put("det", new DeterministicSpeakerSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("loose", new LooseConversationalSpeakerSieve(doc, characterMap, pronounCorefMap, animacyList));
    map.put("top", new BaselineTopSpeakerSieve(doc, characterMap, pronounCorefMap, animacyList, genderMap,
        familyRelations));
    map.put("maj", new MajoritySpeakerSieve(doc, characterMap, pronounCorefMap, animacyList));
    return map;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return new HashSet<>(Arrays.asList(
      MentionAnnotation.class,
      MentionBeginAnnotation.class,
      MentionEndAnnotation.class,
      MentionTypeAnnotation.class,
      MentionSieveAnnotation.class,
      SpeakerAnnotation.class,
      SpeakerSieveAnnotation.class,
      CoreAnnotations.ParagraphIndexAnnotation.class
    ));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return new HashSet<>(Arrays.asList(
      CoreAnnotations.TextAnnotation.class,
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class,
      CoreAnnotations.CharacterOffsetBeginAnnotation.class,
      CoreAnnotations.CharacterOffsetEndAnnotation.class,
      CoreAnnotations.PartOfSpeechAnnotation.class,
      CoreAnnotations.LemmaAnnotation.class,
      CoreAnnotations.BeforeAnnotation.class,
      CoreAnnotations.AfterAnnotation.class,
      CoreAnnotations.TokenBeginAnnotation.class,
      CoreAnnotations.TokenEndAnnotation.class,
      CoreAnnotations.IndexAnnotation.class,
      CoreAnnotations.OriginalTextAnnotation.class
//      CoreAnnotations.ParagraphIndexAnnotation.class
    ));
  }

}
