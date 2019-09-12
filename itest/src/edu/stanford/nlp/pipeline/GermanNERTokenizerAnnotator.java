package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.international.german.process.GermanNERCoreLabelProcessor;
import edu.stanford.nlp.ling.*;

import java.util.*;

/**
 * An Annotator that matches tokenization when German text is sent to CRFClassifier.
 * Note, this annotator just alters the document tokens, and is only meant for a
 * benchmarking test.  This won't alter the list of sentence tokens.
 */

public class GermanNERTokenizerAnnotator implements Annotator {

  public GermanNERCoreLabelProcessor germanCoreLabelProcessor = new GermanNERCoreLabelProcessor();

  @Override
  public void annotate(Annotation ann) {
    ann.set(CoreAnnotations.TokensAnnotation.class,
        germanCoreLabelProcessor.process(ann.get(CoreAnnotations.TokensAnnotation.class)));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.emptySet();
  }



}
