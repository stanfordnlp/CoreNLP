package edu.stanford.nlp.pipeline;

import static edu.stanford.nlp.util.logging.Redwood.Util.logf;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static edu.stanford.nlp.util.logging.Redwood.Util.logf;

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
  }

  private static boolean checkStrings(String s1, String s2) {
    if (s1 == null || s2 == null) {
      return Objects.equals(s1, s2);
    } else {
      return s1.equals(s2);
    }
  }

  private static boolean checkNumbers(Number n1, Number n2) {
    if (n1 == null || n2 == null) {
      return Objects.equals(n1, n2);
    } else {
      return n1.equals(n2);
    }
  }

  private List<CoreLabel> tokensForCharacters(List<CoreLabel> tokens, int charBegin, int charEnd) {
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
    if ( ! checkStrings(v1,v2)) return false;

    // This duplicates logic in the QuantifiableEntityNormalizer (but maybe we will get rid of that class)
    String nerTag = cur.get(nerCoreAnnotationClass);
    if ("NUMBER".equals(nerTag) || "ORDINAL".equals(nerTag)) {
      // Get NumericCompositeValueAnnotation and say two entities are incompatible if they are different
      Number n1 = cur.get(CoreAnnotations.NumericCompositeValueAnnotation.class);
      Number n2 = prev.get(CoreAnnotations.NumericCompositeValueAnnotation.class);
      if ( ! checkNumbers(n1,n2)) return false;
    }

    // Check timex...
    if ("TIME".equals(nerTag) || "SET".equals(nerTag) || "DATE".equals(nerTag) || "DURATION".equals(nerTag)) {
      Timex timex1 = cur.get(TimeAnnotations.TimexAnnotation.class);
      Timex timex2 = prev.get(TimeAnnotations.TimexAnnotation.class);
      String tid1 = (timex1 != null)? timex1.tid():null;
      String tid2 = (timex2 != null)? timex2.tid():null;
      if ( ! checkStrings(tid1,tid2)) return false;
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

  @Override
  public void annotate(Annotation annotation) {
    List<CoreMap> allMentions = new ArrayList<>();
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
                mention.set(CoreAnnotations.WikipediaEntityAnnotation.class, token.get(CoreAnnotations.WikipediaEntityAnnotation.class));
              }
            }
          }
        }
      }
      if (mentions != null) {
        allMentions.addAll(mentions);
      }

      // And finally any Timex annotations (from e.g. HeidelTime).
      List<CoreMap> timexes = sentence.get(TimeAnnotations.TimexAnnotations.class);
      if (timexes != null) {
        List<CoreMap> timexMentions = new ArrayList<>();

        for (CoreMap cmap : timexes) {
          // Get character information.
          int charBegin = cmap.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
          int charEnd = cmap.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
          Timex timex = cmap.get(TimeAnnotations.TimexAnnotation.class);
          List<CoreLabel> timexTokens = cmap.get(CoreAnnotations.TokensAnnotation.class);
          String type = "DATE";

          if (timexTokens.isEmpty()) continue;

          int tokenBegin = timexTokens.get(0).index();
          int tokenEnd = timexTokens.get(timexTokens.size()-1).index();

          // Create a mention from this data.
          CoreMap mention = new ArrayCoreMap();
          // Text
          mention.set(CoreAnnotations.TextAnnotation.class, timex.text());
          // Character offset
          mention.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, charBegin);
          mention.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, charEnd);
          // Tokens
          mention.set(CoreAnnotations.TokensAnnotation.class, timexTokens);
          // TokenBegin, End
          mention.set(CoreAnnotations.TokenBeginAnnotation.class, tokenBegin);
          mention.set(CoreAnnotations.TokenEndAnnotation.class, tokenEnd);
          // SentenceIndex
          mention.set(CoreAnnotations.SentenceIndexAnnotation.class, sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
          // NamedEntityTag
          mention.set(CoreAnnotations.NamedEntityTagAnnotation.class, type);

          // Run mention postprocessing.
          mention.set(nerNormalizedCoreAnnotationClass, timex.value());
          mention.set(CoreAnnotations.EntityTypeAnnotation.class, type);
          // set sentence index annotation for mention
          mention.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);
          mention.set(TimeAnnotations.TimexAnnotation.class, timex);

          Optional<CoreMap> existingMention = overlapsWithMention(mention, timexMentions);
          if (existingMention.isPresent()) {
            logf("WARNING: Timex mention %s collides with existing mention %s; skipping.", mention, existingMention.get());
          } else {
            timexMentions.add(mention);
          }
        }

        if (sentence.get(mentionsCoreAnnotationClass) == null) {
          sentence.set(mentionsCoreAnnotationClass, new ArrayList<>());
        } else if ( ! timexMentions.isEmpty()) {
          // Remove any mentions that overlap with this one.
          ListIterator<CoreMap> it = sentence.get(mentionsCoreAnnotationClass).listIterator();
          while (it.hasNext()) {
            CoreMap mention = it.next();

            Optional<CoreMap> newMention = overlapsWithMention(mention, timexMentions);
            if (newMention.isPresent()) {
              logf("WARNING: Mention %s collides with new timex mention %s; removing.", mention, newMention.get());
              it.remove();
            }
          }
        }
        sentence.get(mentionsCoreAnnotationClass).addAll(timexMentions);
        allMentions.addAll(timexMentions);
      }

      sentenceIndex++;
    }

    // Post-process with acronyms
    if (doAcronyms) {
      addAcronyms(annotation, allMentions);
    }

    annotation.set(mentionsCoreAnnotationClass, allMentions);
  }

  private void addAcronyms(Annotation ann, List<CoreMap> mentions) {
    // Find all the organizations in a document
    List<List<CoreLabel>> organizations = new ArrayList<>();
    for (CoreMap mention : mentions) {
      if ("ORGANIZATION".equals(mention.get(nerCoreAnnotationClass))) {
        organizations.add(mention.get(CoreAnnotations.TokensAnnotation.class));
      }
    }
    // Skip very long documents
    if (organizations.size() > 100) { return; }

    // Iterate over tokens...
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
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
              mentions.add(chunk);

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
