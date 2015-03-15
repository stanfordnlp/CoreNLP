package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.pipeline.Annotation;

import java.io.IOException;

/**
 * Creates an annotation from text
 *
 * @author Angel Chang
 */
public class TextAnnotationCreator extends AbstractTextAnnotationCreator {
  @Override
  public Annotation createFromText(String text) throws IOException {
    return new Annotation(text);
  }
}
