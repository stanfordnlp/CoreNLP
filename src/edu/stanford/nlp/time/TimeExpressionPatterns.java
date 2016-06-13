package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.util.CoreMap;

/**
 * Interface for rules/patterns for transforming
 * time related natural language expressions
 * into temporal representations.
 *
 * Patterns use the TokensRegex language.
 *
 * @author Angel Chang
 */
public interface TimeExpressionPatterns {

  /**
   * Creates a CoreMapExpressionExtractor that knows how
   * to extract time related expressions from text into CoreMaps
   * @return CoreMapExpressionExtractor
   */
  CoreMapExpressionExtractor createExtractor();

  /**
   * Determine how date/times should be resolved for the given temporal
   * expression and its context
   * @param annotation Annotation from which the temporal express was extracted (context)
   * @param te Temporal expression
   * @return flag indicating what resolution scheme to use
   */
  int determineRelFlags(CoreMap annotation, TimeExpression te);

}
