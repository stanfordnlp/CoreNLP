package edu.stanford.nlp.time;

import edu.stanford.nlp.util.Factory;

/**
 * Factory for creating TimeExpressionExtractor
 *
 * @author Angel Chang
 */
public class TimeExpressionExtractorFactory implements Factory<TimeExpressionExtractor> {
  public static final String DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS = "edu.stanford.nlp.time.TimeExpressionExtractorImpl";
  private String timeExpressionExtractorClass = DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS;

  public static final boolean DEFAULT_EXTRACTOR_PRESENT = isDefaultExtractorPresent();

  public TimeExpressionExtractorFactory() {
  }

  public TimeExpressionExtractorFactory(String className) {
    this.timeExpressionExtractorClass = className;
  }

  public TimeExpressionExtractor create() {
    return create(timeExpressionExtractorClass);
  }

  public static TimeExpressionExtractor createExtractor() {
    return create(DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS);
  }

  public static boolean isDefaultExtractorPresent() {
    try {
      Class clazz = Class.forName(DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS);
    } catch (ClassNotFoundException ex) {
      return false;
    } catch (NoClassDefFoundError ex) {
      return false;
    }
    return true;
  }

  public static TimeExpressionExtractor create(String className) {
    try {
      Class clazz = Class.forName(className);
      TimeExpressionExtractor extractor = (TimeExpressionExtractor) clazz.newInstance();
      return extractor;
    } catch (ClassNotFoundException ex) {
      throw new RuntimeException(ex);
    } catch (InstantiationException ex) {
      throw new RuntimeException(ex);
    } catch (IllegalAccessException ex) {
      throw new RuntimeException(ex);
    }
  }

}
