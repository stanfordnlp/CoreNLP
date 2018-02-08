package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.*;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
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
import edu.stanford.nlp.util.*;

import java.util.*;


/**
 * An annotator uses attributes quotes in a text to their speakers. It uses a two-stage process that first links quotes
 * to mentions and then mentions to speakers. Each stage consists in a series of sieves that each try to make
 * predictions on the quote or mentions that have not been linked by previous sieves.
 *
 * The annotator will add the following annotations to each QuotationAnnotation:
 * <ul>
 *   <li>MentionAnnotation : the text of the mention</li>
 *   <li>MentionBeginAnnotation : the beginning token index of the mention</li>
 *   <li>MentionEndAnnotation : the end token index of the mention</li>
 *   <li>MentionTypeAnnotation : the type of mention (pronoun, name, or animate noun)</li>
 *   <li>MentionSieveAnnotation : the sieve that made the mention prediction</li>
 *   <li>SpeakerAnnotation : the name of the speaker</li>
 *   <li>SpeakerSieveAnnotation : the name of the sieve that made the speaker prediction</li>
 * </ul>
 *
 * The annotator has the following options:
 * <ul>
 *   <li>quoteattribution.charactersPath (required): path to file containing the character names, aliases,
 *   and gender information.</li>
 *   <li>quoteattribution.booknlpCoref (required): path to tokens file generated from
 *   <a href="https://github.com/dbamman/book-nlp">book-nlp</a> containing coref information.</li>
 *   <li>quoteattribution.QMSieves: list of sieves to use in the quote to mention linking phase
 *   (default=tri,dep,onename,voc,paraend,conv,sup,loose). More information about the sieves can be found at our
 *   <a href="stanfordnlp.github.io/CoreNLP/quoteattribution.html">website</a>. </li>
 *   <li>quoteattribution.MSSieves: list of sieves to use in the mention to speaker linking phase
 *   (default=det,top).</li>
 *   <li>quoteattribution.model: path to trained model file.</li>
 *   <li>quoteattribution.familyWordsFile: path to file with family words list.</li>
 *   <li>quoteattribution.animacyWordsFile: path to file with animacy words list.</li>
 *   <li>quoteattribution.genderNamesFile: path to file with names list with gender information.</li>
 * </ul>
 *
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

  public static class CanonicalMentionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class CanonicalMentionBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  public static class CanonicalMentionEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  private static Redwood.RedwoodChannels log = Redwood.channels(QuoteAttributionAnnotator.class);

  // settings
  public static final String DEFAULT_QMSIEVES = "tri,dep,onename,voc,paraend,conv,sup,loose";
  public static final String DEFAULT_MSSIEVES = "det,top";
  public static final String DEFAULT_MODEL_PATH = "edu/stanford/nlp/models/quoteattribution/quoteattribution_model.ser";

  // these paths go in the props file
  public static String FAMILY_WORD_LIST = "edu/stanford/nlp/models/quoteattribution/family_words.txt";
  public static String ANIMACY_WORD_LIST = "edu/stanford/nlp/models/quoteattribution/animate.unigrams.txt";
  public static String GENDER_WORD_LIST = "edu/stanford/nlp/models/quoteattribution/gender_filtered.txt";
  public static String COREF_PATH = "";
  public static String MODEL_PATH = "edu/stanford/nlp/models/quoteattribution/quoteattribution_model.ser";
  public static String CHARACTERS_FILE = "";
  public boolean buildCharacterMapPerAnnotation = false;
  public boolean useCoref = true;

  public Boolean VERBOSE = false;

  // fields
  private Set<String> animacyList;
  private Set<String> familyRelations;
  private Map<String, Person.Gender> genderMap;
  private Map<String, List<Person>> characterMap;
  private String qmSieveList;
  private String msSieveList;

  public QuoteAttributionAnnotator(Properties props) {

    VERBOSE = PropertiesUtils.getBool(props, "verbose", false);

    Timing timer = null;
    COREF_PATH = props.getProperty("booknlpCoref", null);
    if(COREF_PATH == null && VERBOSE) {
      log.err("Warning: no coreference map!");
    }
    MODEL_PATH = props.getProperty("modelPath", DEFAULT_MODEL_PATH);
    CHARACTERS_FILE = props.getProperty("charactersPath", null);
    if(CHARACTERS_FILE == null && VERBOSE) {
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
    FAMILY_WORD_LIST = props.getProperty("familyWordsFile", FAMILY_WORD_LIST);
    ANIMACY_WORD_LIST = props.getProperty("animacyWordsFile", ANIMACY_WORD_LIST);
    GENDER_WORD_LIST = props.getProperty("genderNamesFile", GENDER_WORD_LIST);
    familyRelations = QuoteAttributionUtils.readFamilyRelations(FAMILY_WORD_LIST);
    genderMap = QuoteAttributionUtils.readGenderedNounList(GENDER_WORD_LIST);
    animacyList = QuoteAttributionUtils.readAnimacyList(ANIMACY_WORD_LIST);
    if (characterMap != null) {
      characterMap = QuoteAttributionUtils.readPersonMap(CHARACTERS_FILE);
    } else {
      buildCharacterMapPerAnnotation = true;
    }
    // use Stanford CoreNLP coref to map mentions to canonical mentions
    useCoref = PropertiesUtils.getBool(props, "useCoref", useCoref);
    if (VERBOSE) {
      timer.stop("done.");
    }
  }

  /** if no character list is provided, produce a list of person names from entity mentions annotation **/
  public void entityMentionsToCharacterMap(Annotation annotation) {
    characterMap = new HashMap<String, List<Person>>();
    for (CoreMap entityMention : annotation.get(CoreAnnotations.MentionsAnnotation.class)) {
      String entityMentionString = entityMention.toString();
      if (entityMention.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals("PERSON")) {
        Person newPerson = new Person(entityMentionString, "UNK", new ArrayList());
        List<Person> newPersonList = new ArrayList<Person>();
        newPersonList.add(newPerson);
        characterMap.put(entityMentionString, newPersonList);
      }
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    boolean perDocumentCharacterMap = false;
    if (buildCharacterMapPerAnnotation) {
      if (annotation.containsKey(CoreAnnotations.MentionsAnnotation.class)) {
        entityMentionsToCharacterMap(annotation);
      }
    }
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

    // see if any speaker's could be matched to a canonical entity mention
    for (CoreMap quote : QuoteAnnotator.gatherQuotes(annotation)) {
      Integer firstSpeakerTokenIndex = quote.get(MentionBeginAnnotation.class);
      if (firstSpeakerTokenIndex != null) {
        CoreLabel firstSpeakerToken =
            annotation.get(CoreAnnotations.TokensAnnotation.class).get(firstSpeakerTokenIndex);
        Integer entityMentionIndex = firstSpeakerToken.get(
            CoreAnnotations.EntityMentionIndexAnnotation.class);
        if (entityMentionIndex != null) {
          // set speaker string
          CoreMap entityMention =
              annotation.get(CoreAnnotations.MentionsAnnotation.class).get(entityMentionIndex);
          Integer canonicalEntityMentionIndex =
              entityMention.get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class);
          if (canonicalEntityMentionIndex != null) {
            CoreMap canonicalEntityMention =
                annotation.get(CoreAnnotations.MentionsAnnotation.class).get(canonicalEntityMentionIndex);
            // add canonical entity mention info to quote
            quote.set(CanonicalMentionAnnotation.class,
                canonicalEntityMention.get(CoreAnnotations.TextAnnotation.class));
            // set first and last tokens of canonical entity mention
            List<CoreLabel> canonicalEntityMentionTokens =
                canonicalEntityMention.get(CoreAnnotations.TokensAnnotation.class);
            CoreLabel canonicalEntityMentionFirstToken =
                canonicalEntityMentionTokens.get(0);
            CoreLabel canonicalEntityMentionLastToken =
                canonicalEntityMentionTokens.get(canonicalEntityMentionTokens.size() - 1);
            quote.set(CanonicalMentionBeginAnnotation.class,
                canonicalEntityMentionFirstToken.get(CoreAnnotations.TokenBeginAnnotation.class));
            quote.set(CanonicalMentionEndAnnotation.class,
                canonicalEntityMentionLastToken.get(CoreAnnotations.TokenBeginAnnotation.class));
          }
        }
      }
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
      CanonicalMentionAnnotation.class,
      CanonicalMentionBeginAnnotation.class,
      CanonicalMentionEndAnnotation.class,
      MentionTypeAnnotation.class,
      MentionSieveAnnotation.class,
      SpeakerAnnotation.class,
      SpeakerSieveAnnotation.class,
      CoreAnnotations.ParagraphIndexAnnotation.class
    ));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    Set<Class<? extends CoreAnnotation>> quoteAttributionRequirements = new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class,
        CoreAnnotations.LemmaAnnotation.class,
        CoreAnnotations.NamedEntityTagAnnotation.class,
        CoreAnnotations.MentionsAnnotation.class,
        CoreAnnotations.BeforeAnnotation.class,
        CoreAnnotations.AfterAnnotation.class,
        CoreAnnotations.TokenBeginAnnotation.class,
        CoreAnnotations.TokenEndAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class
    ));
    if (useCoref)
      quoteAttributionRequirements.add(CorefCoreAnnotations.CorefChainAnnotation.class);
    return quoteAttributionRequirements;
  }

}
