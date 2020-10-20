package edu.stanford.nlp.time;

import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Properties;

/**
 * A TimeExpressionExtractor extracts a list of time expressions from a document annotation.
 *
 * @author Angel Chang
 */
public interface TimeExpressionExtractor {

  void init(String name, Properties props);

  void init(Options options);

  /**
   * Extract time expressions from a sentence in a document.  The document is assumed to contain the document date.
   * The document is also used to hold stateful information (e.g. the index used by SUTime to generate timex ids).
   * Both the sentence and document are provided as a CoreMap Annotation.
   *
   * @param annotation - Annotation holding tokenized text from which the time expressions are to be extracted
   *
   * @param docAnnotation - Annotation for the entire document
   *                        Uses the following annotations:
   *                          CoreAnnotations.DocDateAnnotation.class (String representing document date)
   *                          TimeExpression.TimeIndexAnnotation.class (Holds index used to generate tids)
   * @return List of CoreMaps
   */
  List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, CoreMap docAnnotation);

  /**
   * Extract time expressions in a document (provided as a CoreMap Annotation).
   *
   * @param annotation The annotation to run time expression extraction over
   * @param docDate A date for the document to be used as a reference time.
   * @return A list of CoreMap.  Each CoreMap represents a detected temporal
   *     expression.  Each CoreMap is a pipeline.Annotation, and you can get
   *     various attributes of the temporal expression out of it. For example,
   *     you can get the list of tokens with:
   *     <pre>
   *     {@code
   *     List<CoreMap> cm = extractTimeExpressionCoreMaps(annotation, docDate);
   *     List<CoreLabel> tokens = cm.get(CoreAnnotations.TokensAnnotation.class); }
   *     </pre>
   */
  List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, String docDate);

  /**
   * Indicates that all annotations on the document has been completed
   * Performs cleanup on the document annotation.
   *
   * @param docAnnotation A document annotation.
   */
  void finalize(CoreMap docAnnotation);

}