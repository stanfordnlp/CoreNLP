package edu.stanford.nlp.time;

import java.util.Properties;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.ReflectionLoading;

/**
 * Factory for creating TimeExpressionExtractor.
 *
 * @author Angel Chang
 */
public class TimeExpressionExtractorFactory implements Factory<TimeExpressionExtractor> {

  private static final long serialVersionUID = 7280996573587450170L;

  public static final String DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS = "edu.stanford.nlp.time.TimeExpressionExtractorImpl";
  private final String timeExpressionExtractorClass;

  public static final boolean DEFAULT_EXTRACTOR_PRESENT = isDefaultExtractorPresent();

  public TimeExpressionExtractorFactory() {
    this(DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS);
  }

  public TimeExpressionExtractorFactory(String className) {
    this.timeExpressionExtractorClass = className;
  }

  @Override
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

  private static boolean isDefaultExtractorPresent() {
    try {
      Class clazz = Class.forName(DEFAULT_TIME_EXPRESSION_EXTRACTOR_CLASS);
    } catch (ClassNotFoundException | NoClassDefFoundError ex) {
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
