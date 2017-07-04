package edu.stanford.nlp.time;

import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.time.SUTime.Temporal;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * Simple wrapper around SUTime for parsing lots of strings outside of Annotation objects.
 *
 * @author David McClosky
 */
public class SUTimeSimpleParser {

  private SUTimeSimpleParser() {} // static methods

  /**
   * Indicates that any exception occurred inside the TimeAnnotator.  This should only be caused by bugs in SUTime.
   */
  public static class SUTimeParsingError extends Exception {
    private static final long serialVersionUID = 1L;
    public String timeExpression;

    public SUTimeParsingError(String timeExpression) {
      this.timeExpression = timeExpression;
    }

    public String getLocalizedMessage() {
      return "Error while parsing '" + timeExpression + "'";
    }

  }

  private static AnnotationPipeline pipeline;
  private static Map<String, Temporal> cache;
  public static int calls = 0;
  public static int misses = 0;

  static {
    pipeline = makeNumericPipeline();
    cache = Generics.newHashMap();
  }

  private static AnnotationPipeline makeNumericPipeline() {
    AnnotationPipeline pipeline = new AnnotationPipeline();
    pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
    pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    pipeline.addAnnotator(new POSTaggerAnnotator(false));
    pipeline.addAnnotator(new TimeAnnotator(true));

    return pipeline;
  }

  public static Temporal parseOrNull(String str) {
    Annotation doc = new Annotation(str);
    pipeline.annotate(doc);
    if (doc.get(CoreAnnotations.SentencesAnnotation.class) == null) {
      return null;
    }
    if (doc.get(CoreAnnotations.SentencesAnnotation.class).size() == 0) {
      return null;
    }

    List<CoreMap> timexAnnotations = doc.get(TimeAnnotations.TimexAnnotations.class);
    if (timexAnnotations.size() > 1) {
      return null;
    } else if (timexAnnotations.size() == 0) {
      return null;
    }

    CoreMap timex = timexAnnotations.get(0);

    if (timex.get(TimeExpression.Annotation.class) == null) {
      return null;
    } else {
      return timex.get(TimeExpression.Annotation.class).getTemporal();
    }
  }

  /**
   * Parse a string with SUTime.
   *
   * @throws SUTimeParsingError if anything goes wrong
   */
  public static Temporal parse(String str) throws SUTimeParsingError {
    try {
      Annotation doc = new Annotation(str);
      pipeline.annotate(doc);

      assert doc.get(CoreAnnotations.SentencesAnnotation.class) != null;
      assert doc.get(CoreAnnotations.SentencesAnnotation.class).size() > 0;
      List<CoreMap> timexAnnotations = doc.get(TimeAnnotations.TimexAnnotations.class);
      if (timexAnnotations.size() > 1) {
        throw new RuntimeException("Too many timexes for '" + str + "'");
      }
      CoreMap timex = timexAnnotations.get(0);

      return timex.get(TimeExpression.Annotation.class).getTemporal();
    } catch (Exception e) {
      SUTimeSimpleParser.SUTimeParsingError parsingError = new SUTimeSimpleParser.SUTimeParsingError(str);
      parsingError.initCause(e);
      throw parsingError;
    }
  }

  /**
   * Cached wrapper of parse method.
   */
  public static Temporal parseUsingCache(String str) throws SUTimeParsingError {
    calls++;
    if (!cache.containsKey(str)) {
      misses++;
      cache.put(str, parse(str));
    }

    return cache.get(str);
  }

  public static void main(String[] args) throws SUTimeParsingError {
    for (String s : new String[] {"1972", "1972-07-05", "0712", "1972-04"}) {
      System.out.println("String: " + s);
      Temporal timeExpression = parse(s);
      System.out.println("Parsed: " + timeExpression);
      System.out.println();
    }
  }

}