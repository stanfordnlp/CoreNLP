package edu.stanford.nlp.time;

import java.util.Properties;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.ReflectionLoading;

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

  public TimeExpressionExtractor create(String name, Properties props) {
    return create(timeExpressionExtractorClass, name, props);
  }

  public static TimeExpressionExtractor createExtractor() {
    return create(DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS);
  }

  public static TimeExpressionExtractor createExtractor(String name, Properties props) {
    return create(DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS, name, props);
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
    return ReflectionLoading.loadByReflection(className);
  }

  public static TimeExpressionExtractor create(String className, String name, Properties props) {
    return ReflectionLoading.loadByReflection(className, name, props);
  }
}
