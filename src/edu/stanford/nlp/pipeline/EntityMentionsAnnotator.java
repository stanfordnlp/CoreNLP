package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.function.Function;

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
 *   </li>
 * </ul>
 *
 * Each sentence is annotated with a list of the mentions
 * (MentionsAnnotation as a list of CoreMap).
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

  private Class<? extends CoreAnnotation<String>> nerNormalizedAnnotation;
  private Class<? extends CoreAnnotation<String>> nerAnnotation;
  private Class<? extends CoreAnnotation<List<CoreMap>>> mentionsAnnotation;

  public EntityMentionsAnnotator(
          Class<? extends CoreAnnotation<String>> nerNormalizedAnnotation, Class<? extends CoreAnnotation<String>> nerAnnotation,
	  Class<? extends CoreAnnotation<List<CoreMap>>> mentionsAnnotation) {
      this.nerNormalizedAnnotation = nerNormalizedAnnotation;
      this.nerAnnotation = nerAnnotation;
      this.mentionsAnnotation = mentionsAnnotation;
      chunkIdentifier = new LabeledChunkIdentifier();
      doAcronyms = false;
  }

  public EntityMentionsAnnotator() {
      this(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class,
	   CoreAnnotations.NamedEntityTagAnnotation.class,
	   CoreAnnotations.MentionsAnnotation.class);
  }

  // note: used in annotate.properties
  @SuppressWarnings("UnusedDeclaration")
  public EntityMentionsAnnotator(String name, Properties props) {
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

  private final Function<Pair<CoreLabel,CoreLabel>, Boolean> IS_TOKENS_COMPATIBLE = in -> {
    // First argument is the current token
    CoreLabel cur = in.first;
    // Second argument the previous token
    CoreLabel prev = in.second;

    if (cur == null || prev == null) {
      return false;
    }

    // Get NormalizedNamedEntityTag and say two entities are incompatible if they are different
    String v1 = cur.get(nerNormalizedAnnotation);
    String v2 = prev.get(nerNormalizedAnnotation);
    if ( ! checkStrings(v1,v2)) return false;

    // This duplicates logic in the QuantifiableEntityNormalizer (but maybe we will get rid of that class)
    String nerTag = cur.get(nerAnnotation);
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
              CoreAnnotations.TextAnnotation.class, nerAnnotation, IS_TOKENS_COMPATIBLE);
      sentence.set(mentionsAnnotation, chunks);

      // By now entity mentions have been annotated and TextAnnotation and NamedEntityAnnotation marked
      // Some additional annotations
      List<CoreMap> mentions = sentence.get(mentionsAnnotation);
      if (mentions != null) {
        for (CoreMap mention : mentions) {
          List<CoreLabel> mentionTokens = mention.get(CoreAnnotations.TokensAnnotation.class);
          String name = (String) CoreMapAttributeAggregator.FIRST_NON_NIL.aggregate(
                  nerNormalizedAnnotation, mentionTokens);
          if (name == null) {
            name = mention.get(CoreAnnotations.TextAnnotation.class);
          } else {
            mention.set(nerNormalizedAnnotation, name);
          }
          //mention.set(CoreAnnotations.EntityNameAnnotation.class, name);
          String type = mention.get(nerAnnotation);
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
      sentenceIndex++;
    }

    // Post-process with acronyms
    if (doAcronyms) {
      addAcronyms(annotation, allMentions);
    }

    annotation.set(mentionsAnnotation, allMentions);
  }


  private static void addAcronyms(Annotation ann, List<CoreMap> mentions) {
    // Find all the organizations in a document
    List<List<CoreLabel>> organizations = new ArrayList<>();
    for (CoreMap mention : mentions) {
      if ("ORGANIZATION".equals(mention.get(CoreAnnotations.NamedEntityTagAnnotation.class))) {
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
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
	nerAnnotation
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(mentionsAnnotation);
  }

}
