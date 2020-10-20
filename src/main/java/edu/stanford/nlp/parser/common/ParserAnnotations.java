package edu.stanford.nlp.parser.common;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.ErasureUtils;

/**
 * Parse time options for the Stanford lexicalized parser.  For
 * example, you can set a ConstraintAnnotation and the parser
 * annotator will extract that annotation and apply the constraints
 * when parsing.
 */

public class ParserAnnotations {
  
  private ParserAnnotations() {} // only static members



  /**
   * This CoreMap key represents a regular expression which the parser
   * will try to match when assigning tags.
   *
   * This key is typically set on token annotations.
   */
  public static class CandidatePartOfSpeechAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key for getting a list of constraints to apply when parsing.
   */
  public static class ConstraintAnnotation 
    implements CoreAnnotation<List<ParserConstraint>> 
  {
    public Class<List<ParserConstraint>> getType() {
      return ErasureUtils.<Class<List<ParserConstraint>>> uncheckedCast(List.class);
    }
  }


}
