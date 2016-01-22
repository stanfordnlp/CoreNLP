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

  List<CoreMap> extractTimeExpressionCoreMaps(CoreMap annotation, String docDate);

//  List<TimeExpression> extractTimeExpressions(CoreMap annotation, String docDateStr);

}
