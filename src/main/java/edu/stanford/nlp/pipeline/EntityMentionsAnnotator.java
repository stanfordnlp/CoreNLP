package edu.stanford.nlp.pipeline;

import java.util.*;
import java.util.function.Predicate;

import edu.stanford.nlp.coref.data.WordLists;
import edu.stanford.nlp.ie.KBPRelationExtractor;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Annotator that marks entity mentions in a document.
 * Entity mentions are:
 * <ul>
 * <li> Named entities (identified by NER) </li>
 * <li> Quantifiable entities
 *   <ul>
 *   <li> Times (identified by TimeAnnotator) </li>
 *   <li> Measurements (identified by ???) </li>
 *   </ul>
 * </li>
 * </ul>
 *
 * Each sentence is annotated with a list of the mentions
 * (MentionsAnnotation is a list of CoreMap).
 *
 * @author Angel Chang
 */
public class EntityMentionsAnnotator implements Annotator {

  // Currently relies on NER annotations being okay
  // - Replace with calling NER classifiers and timeAnnotator directly
  private final LabeledChunkIdentifier chunkIdentifier;

  /**
   * If true, heuristically search for organization acronyms, even if they are not marked
   * explicitly by an NER tag.
   * This is super useful (+20% recall) for KBP.
   */
  private final boolean doAcronyms;

  private static final boolean matchTokenText = false;

  private final LanguageInfo.HumanLanguage entityMentionsLanguage;

  // TODO: Provide properties
  public static PropertiesUtils.Property[] SUPPORTED_PROPERTIES = new PropertiesUtils.Property[]{};

  /** The CoreAnnotation keys to use for this entity mentions annotator. */
  private Class<? extends CoreAnnotation<String>> nerCoreAnnotationClass =
      CoreAnnotations.NamedEntityTagAnnotation.class;
  private Class<? extends CoreAnnotation<String>> nerNormalizedCoreAnnotationClass =
      CoreAnnotations.NormalizedNamedEntityTagAnnotation.class;
  private Class<? extends CoreAnnotation<List<CoreMap>>> mentionsCoreAnnotationClass =
      CoreAnnotations.MentionsAnnotation.class;

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(EntityMentionsAnnotator.class);

  public EntityMentionsAnnotator() {
    // defaults
    chunkIdentifier = new LabeledChunkIdentifier();
    doAcronyms = false;
    entityMentionsLanguage = LanguageInfo.getLanguageFromString("en");
  }

  // note: used in annotate.properties
  @SuppressWarnings({"UnusedDeclaration", "unchecked"})
  public EntityMentionsAnnotator(String name, Properties props) {
    // if the user has supplied custom CoreAnnotations for the ner tags and entity mentions override the default keys
    try {
      if (props.containsKey(name + ".nerCoreAnnotation")) {
        nerCoreAnnotationClass =
            (Class<? extends CoreAnnotation<String>>)
                Class.forName(props.getProperty(name + ".nerCoreAnnotation"));
      }
      if (props.containsKey(name + ".nerNormalizedCoreAnnotation")) {
        nerNormalizedCoreAnnotationClass =
            (Class<? extends CoreAnnotation<String>>)
                Class.forName(props.getProperty(name + ".nerNormalizedCoreAnnotation"));
      }
      if (props.containsKey(name + ".mentionsCoreAnnotation")) {
        mentionsCoreAnnotationClass =
            (Class<? extends CoreAnnotation<List<CoreMap>>>)
                Class.forName(props.getProperty(name + ".mentionsCoreAnnotation"));
      }
    } catch (ClassNotFoundException e) {
      log.error(e.getMessage());
    }
    chunkIdentifier = new LabeledChunkIdentifier();
    doAcronyms = Boolean.parseBoolean(props.getProperty(name + ".acronyms", props.getProperty("acronyms", "false")));
    // set up language info, this is needed for handling creating pronominal mentions
    entityMentionsLanguage = LanguageInfo.getLanguageFromString(props.getProperty(name+".language", "en"));
  }

  private static List<CoreLabel> tokensForCharacters(List<CoreLabel> tokens, int charBegin, int charEnd) {
    assert charBegin >= 0;
    List<CoreLabel> segment = Generics.newArrayList();
    for(CoreLabel token: tokens) {
      if (token.endPosition() < charBegin || token.beginPosition() >= charEnd) {
        continue;
      }
      segment.add(token);
    }
    return segment;
  }

  private final Predicate<Pair<CoreLabel, CoreLabel>> IS_TOKENS_COMPATIBLE = in -> {
    // First argument is the current token
    CoreLabel cur = in.first;
    // Second argument the previous token
    CoreLabel prev = in.second;

    if (cur == null || prev == null) {
      return false;
    }

    // Get NormalizedNamedEntityTag and say two entities are incompatible if they are different
    String v1 = cur.get(nerNormalizedCoreAnnotationClass);
    String v2 = prev.get(nerNormalizedCoreAnnotationClass);
    if ( ! Objects.equals(v1, v2)) return false;

    // This duplicates logic in the QuantifiableEntityNormalizer (but maybe we will get rid of that class)
    String nerTag = cur.get(nerCoreAnnotationClass);
    if ("NUMBER".equals(nerTag) || "ORDINAL".equals(nerTag)) {
      // Get NumericCompositeValueAnnotation and say two entities are incompatible if they are different
      Number n1 = cur.get(CoreAnnotations.NumericCompositeValueAnnotation.class);
      Number n2 = prev.get(CoreAnnotations.NumericCompositeValueAnnotation.class);
      if ( ! Objects.equals(n1, n2)) return false;
    }

    // Check timex...
    if ("TIME".equals(nerTag) || "SET".equals(nerTag) || "DATE".equals(nerTag) || "DURATION".equals(nerTag)) {
      Timex timex1 = cur.get(TimeAnnotations.TimexAnnotation.class);
      Timex timex2 = prev.get(TimeAnnotations.TimexAnnotation.class);
      String tid1 = (timex1 != null)? timex1.tid():null;
      String tid2 = (timex2 != null)? timex2.tid():null;
      if ( ! Objects.equals(tid1, tid2)) return false;
    }

    return true;
  };

  private static Optional<CoreMap> overlapsWithMention(CoreMap needle, List<CoreMap> haystack) {
    List<CoreLabel> tokens = needle.get(CoreAnnotations.TokensAnnotation.class);
    int charBegin = tokens.get(0).beginPosition();
    int charEnd = tokens.get(tokens.size()-1).endPosition();

    return (haystack.stream().filter(mention_ -> {
      List<CoreLabel> tokens_ = mention_.get(CoreAnnotations.TokensAnnotation.class);
      int charBegin_ = tokens_.get(0).beginPosition();
      int charEnd_ = tokens_.get(tokens_.size()-1).endPosition();
      // Check overlap
      return !(charBegin_ > charEnd || charEnd_ < charBegin);
    }).findFirst());
  }

  /**
   * Returns whether the given token counts as a valid pronominal mention for KBP.
   * This method (at present) works for either Chinese or English.
   *
   * @param word The token to classify.
   * @return true if this token is a pronoun that KBP should recognize.
   */
  private static boolean kbpIsPronominalMention(CoreLabel word) {
    return WordLists.isKbpPronominalMention(word.word());
  }

  /**
   * Annotate all the pronominal mentions in the document.
   * @param ann The document.
   * @return The list of pronominal mentions in the document.
   */
  private static List<CoreMap> annotatePronominalMentions(Annotation ann) {
    List<CoreMap> pronouns = new ArrayList<>();
    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
      CoreMap sentence = sentences.get(sentenceIndex);
      Integer annoTokenBegin = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
      if (annoTokenBegin == null) {
        annoTokenBegin = 0;
      }

      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
        CoreLabel token = tokens.get(tokenIndex);
        if (kbpIsPronominalMention(token)) {
          CoreMap pronoun = ChunkAnnotationUtils.getAnnotatedChunk(tokens, tokenIndex, tokenIndex + 1,
              annoTokenBegin, null, CoreAnnotations.TextAnnotation.class, null);
          pronoun.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);
          pronoun.set(CoreAnnotations.NamedEntityTagAnnotation.class, KBPRelationExtractor.NERTag.PERSON.name);
          pronoun.set(CoreAnnotations.EntityTypeAnnotation.class, KBPRelationExtractor.NERTag.PERSON.name);
          // set gender
          String pronounGender = null;
          if (pronoun.get(CoreAnnotations.TextAnnotation.class).toLowerCase().equals("she")) {
            pronounGender = "FEMALE";
            pronoun.set(CoreAnnotations.GenderAnnotation.class, pronounGender);
          }
          else if (pronoun.get(CoreAnnotations.TextAnnotation.class).toLowerCase().equals("he")) {
            pronounGender = "MALE";
            pronoun.set(CoreAnnotations.GenderAnnotation.class, pronounGender);
          }
          if (pronounGender != null) {
            for (CoreLabel pronounToken : pronoun.get(CoreAnnotations.TokensAnnotation.class)) {
              pronounToken.set(CoreAnnotations.GenderAnnotation.class, pronounGender);
            }
          }
          sentence.get(CoreAnnotations.MentionsAnnotation.class).add(pronoun);
          pronouns.add(pronoun);
        }
      }
    }

    return pronouns;
  }

  public static HashMap<String,Double> determineEntityMentionConfidences(CoreMap entityMention) {
    // get a list of labels that have probability values from the first token
    Map<String,Double> tagProbs = entityMention.get(CoreAnnotations.TokensAnnotation.class).get(0).get(
            CoreAnnotations.NamedEntityTagProbsAnnotation.class);
    if (tagProbs == null) {
      return null;
    }
    Set<String> labelsWithProbs = tagProbs.keySet();
    // build the label values hash map for the entity mention
    HashMap<String,Double> entityLabelProbVals = new HashMap<>();
    // initialize to 1.1
    for (String labelWithProb : labelsWithProbs) {
      entityLabelProbVals.put(labelWithProb, 1.1);
    }

    // go through each token, see if you can find a smaller prob value for that label
    for (CoreLabel token : entityMention.get(CoreAnnotations.TokensAnnotation.class)) {
      Map<String,Double> labelProbsForToken = token.get(CoreAnnotations.NamedEntityTagProbsAnnotation.class);
      for (String label : labelProbsForToken.keySet()) {
        if (entityLabelProbVals.containsKey(label) && labelProbsForToken.get(label) < entityLabelProbVals.get(label))
          entityLabelProbVals.put(label, labelProbsForToken.get(label));
      }
    }
    // if anything is still at 1.1, set it to -1.0
    for (String label : entityLabelProbVals.keySet()) {
      if (entityLabelProbVals.get(label) >= 1.1) {
        entityLabelProbVals.put(label, -1.0);
      }
    }
    // return the hash map of label probs
    return entityLabelProbVals;
  }

  @Override
  public void annotate(Annotation annotation) {
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    int sentenceIndex = 0;
    for (CoreMap sentence : sentences) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      Integer annoTokenBegin = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
      if (annoTokenBegin == null) {
        annoTokenBegin = 0;
      }
      List<CoreMap> chunks = chunkIdentifier.getAnnotatedChunks(tokens, annoTokenBegin,
              CoreAnnotations.TextAnnotation.class, nerCoreAnnotationClass, IS_TOKENS_COMPATIBLE);
      sentence.set(mentionsCoreAnnotationClass, chunks);

      // By now entity mentions have been annotated and TextAnnotation and NamedEntityAnnotation marked
      // Some additional annotations
      List<CoreMap> mentions = sentence.get(mentionsCoreAnnotationClass);
      if (mentions != null) {
        for (CoreMap mention : mentions) {
          List<CoreLabel> mentionTokens = mention.get(CoreAnnotations.TokensAnnotation.class);
          String name = (String) CoreMapAttributeAggregator.FIRST_NON_NIL.aggregate(
                  nerNormalizedCoreAnnotationClass, mentionTokens);
          if (name == null) {
            name = mention.get(CoreAnnotations.TextAnnotation.class);
          } else {
            mention.set(nerNormalizedCoreAnnotationClass, name);
          }
          //mention.set(CoreAnnotations.EntityNameAnnotation.class, name);
          String type = mention.get(nerCoreAnnotationClass);
          mention.set(CoreAnnotations.EntityTypeAnnotation.class, type);

          // set sentence index annotation for mention
          mention.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);
          // Take first non nil as timex for the mention
          Timex timex = (Timex) CoreMapAttributeAggregator.FIRST_NON_NIL.aggregate(
                  TimeAnnotations.TimexAnnotation.class, mentionTokens);
          if (timex != null) {
            mention.set(TimeAnnotations.TimexAnnotation.class, timex);
          }

          // Set the entity link from the tokens
          if (mention.get(CoreAnnotations.WikipediaEntityAnnotation.class) == null) {
            for (CoreLabel token : mentionTokens) {
              if ( (mention.get(CoreAnnotations.WikipediaEntityAnnotation.class) == null ||
                    "O".equals(mention.get(CoreAnnotations.WikipediaEntityAnnotation.class))) &&
                  ( token.get(CoreAnnotations.WikipediaEntityAnnotation.class) != null &&
                    !"O".equals(token.get(CoreAnnotations.WikipediaEntityAnnotation.class))) ) {
                mention.set(CoreAnnotations.WikipediaEntityAnnotation.class,
                        token.get(CoreAnnotations.WikipediaEntityAnnotation.class));
              }
            }
          }

          if (!matchTokenText) {
            if (annotation.get(CoreAnnotations.TextAnnotation.class) != null
                            && mention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) != null
                            && mention.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) != null) {
              String entityMentionText =
                      annotation.get(CoreAnnotations.TextAnnotation.class).substring(
                              mention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                              mention.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)
                      );
              mention.set(CoreAnnotations.TextAnnotation.class, entityMentionText);
            }
          }
        }
      }

      sentenceIndex++;
    }

    // Post-process with acronyms
    if (doAcronyms) { addAcronyms(annotation); }

    // Post-process add in KBP pronominal mentions, (English only for now)
    if (LanguageInfo.HumanLanguage.ENGLISH.equals(entityMentionsLanguage))
      annotatePronominalMentions(annotation);

    // build document wide entity mentions list
    List<CoreMap> allEntityMentions = new ArrayList<>();
    int entityMentionIndex = 0;
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreMap entityMention : sentence.get(mentionsCoreAnnotationClass)) {
        entityMention.set(CoreAnnotations.EntityMentionIndexAnnotation.class, entityMentionIndex);
        entityMention.set(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class, entityMentionIndex);
        for (CoreLabel entityMentionToken : entityMention.get(CoreAnnotations.TokensAnnotation.class)) {
          entityMentionToken.set(CoreAnnotations.EntityMentionIndexAnnotation.class, entityMentionIndex);
        }
        allEntityMentions.add(entityMention);
        entityMentionIndex++;
      }
    }

    // set the entity mention confidence
    for (CoreMap entityMention : allEntityMentions) {
      HashMap<String,Double> entityMentionLabelProbVals = determineEntityMentionConfidences(entityMention);
      entityMention.set(CoreAnnotations.NamedEntityTagProbsAnnotation.class, entityMentionLabelProbVals);
    }

    annotation.set(mentionsCoreAnnotationClass, allEntityMentions);
  }

  private void addAcronyms(Annotation ann) {
    // Find all the organizations in a document
    List<CoreMap> allMentionsSoFar = new ArrayList<>();
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      allMentionsSoFar.addAll(sentence.get(CoreAnnotations.MentionsAnnotation.class));
    }
    List<List<CoreLabel>> organizations = new ArrayList<>();
    for (CoreMap mention : allMentionsSoFar) {
      if ("ORGANIZATION".equals(mention.get(nerCoreAnnotationClass))) {
        organizations.add(mention.get(CoreAnnotations.TokensAnnotation.class));
      }
    }
    // Skip very long documents
    if (organizations.size() > 100) { return; }

    // Iterate over tokens...
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreMap> sentenceMentions = new ArrayList<>();
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      Integer totalTokensOffset = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
      for (int i = 0; i < tokens.size(); ++i) {
        // ... that look like they might be an acronym and are not already a mention
        CoreLabel token = tokens.get(i);
        if ("O".equals(token.ner()) && token.word().toUpperCase().equals(token.word()) && token.word().length() >= 3) {
          for (List<CoreLabel> org : organizations) {
            // ... and actually are an acronym
            if (AcronymMatcher.isAcronym(token.word(), org)) {
              // ... and add them.
              // System.out.println("found ACRONYM ORG");
              token.setNER("ORGANIZATION");
              CoreMap chunk = ChunkAnnotationUtils.getAnnotatedChunk(tokens,
                  i, i + 1, totalTokensOffset, null, null, null);
              chunk.set(CoreAnnotations.NamedEntityTagAnnotation.class,"ORGANIZATION");
              sentenceMentions.add(chunk);
            }
          }
        }
      }
    }
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    //TODO(jb) for now not fully enforcing pipeline if user customizes keys
    if (!nerCoreAnnotationClass.getCanonicalName().
        equals(CoreAnnotations.NamedEntityTagAnnotation.class.getCanonicalName())) {
      return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class
      )));
    } else {
      return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.NamedEntityTagAnnotation.class
      )));
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(mentionsCoreAnnotationClass);
  }

}
