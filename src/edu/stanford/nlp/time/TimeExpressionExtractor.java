package edu.stanford.nlp.time;

import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Properties;

/**
 * A TimeExpressionExtractor extracts a list of time expression from a document annotation.
 *
 * @author Angel Chang
 */
public interface TimeExpressionExtractor {

  void init(String name, Properties props);

  void init(Options options);

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

//  List<TimeExpression> extractTimeExpressions(CoreMap annotation, String docDateStr);

}
