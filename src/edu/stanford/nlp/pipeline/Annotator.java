package edu.stanford.nlp.pipeline;

/**
 * This is an interface for adding annotations
 * to a fully annotated Annotation.  In some ways,
 * it is just a glorified Function, except that
 * it explicitly operates on Annotation objects.
 * Annotators should be given to an
 * AnnotationPipeline
 * in order to make annotation pipelines
 * (the whole motivation of this package),
 * and therefore
 * implementers of this interface should be
 * designed to play well with other Annotators
 * and in their javadocs they should explicitly
 * state what annotations they are assuming already
 * exist in the annotation (like parse, POS tag,
 * etc), what field they are expecting them
 * under (Annotation.WORDS_KEY, Annotation.PARSE_KEY,
 * etc) and what annotations they will add (or
 * modify) and the keys for them as well.   If
 * you would like to look at the code for a relatively
 * simple Annotator, I recommend NERAnnotator.
 * For a lot of code you could just add the
 * implements directly, but I recommend wrapping instead
 * because I believe that it will help to keep
 * the pipeline code more manageable.
 *
 * @author Jenny Finkel
 */
public interface Annotator {
  public void annotate(Annotation annotation) ;
}
