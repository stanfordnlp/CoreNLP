package edu.stanford.nlp.pipeline;

/**
 * Data source from which annotations comes from
 *
 * @author Angel Chang
 */
public interface AnnotationSource {
  /**
   * Returns a iterable of annotations given input string (i.e. filename, lucene query, etc)
   * @param selector - selector of what annotations to return
   * @param limit - limit on the number of annotations to return (0 or less for unlimited)
   * @return iterable of annotations
   */
  public Iterable<Annotation> getAnnotations(String selector, int limit);

  /**
   * Returns a iterable of annotations given input string (i.e. filename, lucene query, etc)
   * @param selector - selector of what annotations to return
   * @return iterable of annotations
   */
  public Iterable<Annotation> getAnnotations(String selector);

}
