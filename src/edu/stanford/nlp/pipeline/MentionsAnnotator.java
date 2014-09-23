package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Annotator that marks entity mentions in a document.
 * Entity mentions are
 * - Named entities (identified by NER)
 * - Quantifiable entities
 *   - Times (identified by TimeAnnotator)
 *   - Measurements (identified by ???)
 *
 * Each sentence is annotated with a list of the mentions
 *  (MentionsAnnotation as a list of CoreMap)
 *
 * @author Angel Chang
 */
public class MentionsAnnotator implements Annotator {
  // Currently relies on NER annotations being okay
  // - Replace with calling NER classifiers and timeAnnotator directly
  LabeledChunkIdentifier chunkIdentifier;

  public MentionsAnnotator() {
    chunkIdentifier = new LabeledChunkIdentifier();
  }

  // note: used in annotate.properties
  @SuppressWarnings("UnusedDeclaration")
  public MentionsAnnotator(String name, Properties props) {
    this();
  }

  @Override
  public void annotate(Annotation annotation) {
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    Integer annoTokenBegin = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (annoTokenBegin == null) { annoTokenBegin = 0; }
    List<CoreMap> chunks = chunkIdentifier.getAnnotatedChunks(tokens, annoTokenBegin,
            CoreAnnotations.TextAnnotation.class, CoreAnnotations.NamedEntityTagAnnotation.class);
    annotation.set(CoreAnnotations.MentionsAnnotation.class, chunks);

    // By now entity mentions have been annotated and TextAnnotation and NamedEntityAnnotation marked
    // Some additional annotations
    List<CoreMap> mentions = annotation.get(CoreAnnotations.MentionsAnnotation.class);
    if (mentions != null) {
      for (CoreMap mention: mentions) {
        String name = (String) CoreMapAttributeAggregator.FIRST_NON_NIL.aggregate(
                CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, mention.get(CoreAnnotations.TokensAnnotation.class));
        if (name == null) {
          name = mention.get(CoreAnnotations.TextAnnotation.class);
        } else {
          mention.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, name);
        }
        //mention.set(CoreAnnotations.EntityNameAnnotation.class, name);
        String type = mention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        mention.set(CoreAnnotations.EntityTypeAnnotation.class, type);
      }
    }
  }


  @Override
  public Set<Requirement> requires() {
    return new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, NER_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    // TODO: figure out what this produces
    return Collections.emptySet();
  }
}
