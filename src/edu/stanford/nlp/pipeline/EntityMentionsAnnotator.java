package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;

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

  public EntityMentionsAnnotator() {
    chunkIdentifier = new LabeledChunkIdentifier();
    doAcronyms = false;
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

  private static final Function<Pair<CoreLabel,CoreLabel>, Boolean> IS_TOKENS_COMPATIBLE = in -> {
    // First argument is the current token
    CoreLabel cur = in.first;
    // Second argument the previous token
    CoreLabel prev = in.second;

    if (cur == null || prev == null) {
      return false;
    }

    // Get NormalizedNamedEntityTag and say two entities are incompatible if they are different
    String v1 = cur.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
    String v2 = prev.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
    if ( ! checkStrings(v1,v2)) return false;

    // This duplicates logic in the QuantifiableEntityNormalizer (but maybe we will get rid of that class)
    String nerTag = cur.get(CoreAnnotations.NamedEntityTagAnnotation.class);
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
              CoreAnnotations.TextAnnotation.class, CoreAnnotations.NamedEntityTagAnnotation.class, IS_TOKENS_COMPATIBLE);
      sentence.set(CoreAnnotations.MentionsAnnotation.class, chunks);

      // By now entity mentions have been annotated and TextAnnotation and NamedEntityAnnotation marked
      // Some additional annotations
      List<CoreMap> mentions = sentence.get(CoreAnnotations.MentionsAnnotation.class);
      if (mentions != null) {
        for (CoreMap mention : mentions) {
          List<CoreLabel> mentionTokens = mention.get(CoreAnnotations.TokensAnnotation.class);
          String name = (String) CoreMapAttributeAggregator.FIRST_NON_NIL.aggregate(
                  CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, mentionTokens);
          if (name == null) {
            name = mention.get(CoreAnnotations.TextAnnotation.class);
          } else {
            mention.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, name);
          }
          //mention.set(CoreAnnotations.EntityNameAnnotation.class, name);
          String type = mention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
          mention.set(CoreAnnotations.EntityTypeAnnotation.class, type);

          // set sentence index annotation for mention
          mention.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);
          // Take first non nil as timex for the mention
          Timex timex = (Timex) CoreMapAttributeAggregator.FIRST_NON_NIL.aggregate(
                  TimeAnnotations.TimexAnnotation.class, mentionTokens);
          if (timex != null) {
            mention.set(TimeAnnotations.TimexAnnotation.class, timex);
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

    annotation.set(CoreAnnotations.MentionsAnnotation.class, allMentions);
  }


  private void addAcronyms(Annotation ann, List<CoreMap> mentions) {
  }


  @Override
  public Set<Requirement> requires() {
    return new ArraySet<>(TOKENIZE_REQUIREMENT, NER_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    // TODO: figure out what this produces
    return Collections.emptySet();
  }

}
