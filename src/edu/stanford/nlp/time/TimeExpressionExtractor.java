package edu.stanford.nlp.time;

import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Properties;

/**
 * A TimeExpressionExtractor extracts a list of time expression from a document annotation
 *
 * @author Angel Chang
 */
public interface TimeExpressionExtractor {
  void init(String name, Properties props);

  void init(Options options);

  /**
   * Returns list of CoreMaps indicating what the time expressions are
   * @param annotation - Annotation holding tokenized text from which the time expressions are to be extracted
   *
   * @param docAnnotation - Annotation for the entire document
   *                        Uses the following annotations:
   *                          CoreAnnotations.DocDateAnnotation.class (String representing document date)
   *                          TimeExpression.TimeIndexAnnotation.class (Holds index used to generated tids)
   * @return List of CoreMaps
   */
  List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, CoreMap docAnnotation);

  /**
   * Returns list of CoreMaps indicating what the time expressions are
   * @param annotation - Annotation holding tokenized text from which the time expressions are to be extracted
   * @param docDate - String representing document date
   * @return List of CoreMaps
   */
  List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, String docDate);

  /**
   * Indicates that all annotations on the document has been completed
   * Performs cleanup on the document annotation
   * @param docAnnotation
   */
  void finalize(CoreMap docAnnotation);

}
