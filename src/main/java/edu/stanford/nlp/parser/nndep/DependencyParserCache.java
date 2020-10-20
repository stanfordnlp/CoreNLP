package edu.stanford.nlp.parser.nndep;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.lang.ref.WeakReference;

/**
 * Implements a cache for DependencyParsers, uniquing them by name and options.
 *<br>
 * TODO: this could be generalized into a more general class
 * <br>
 * @author John Bauer
 */

class DependencyParserCache {

  private static class DependencyParserSpecification {
    private final Properties props;
    private final String modelFile;

    DependencyParserSpecification(String modelFile, Properties extraProperties) {
      this.modelFile = modelFile;
      // Copy, in case the passed in Properties changes later
      this.props = new Properties();
      if (extraProperties != null) {
        for (String key : extraProperties.stringPropertyNames()) {
          this.props.setProperty(key, extraProperties.getProperty(key));
        }
      }
    }

    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof DependencyParserSpecification)) {
        return false;
      }
      DependencyParserSpecification other = (DependencyParserSpecification) obj;
      return this.modelFile.equals(other.modelFile) && this.props.equals(other.props);
    }

    public int hashCode() {
      return this.modelFile.hashCode() + this.props.hashCode();
    }

    DependencyParser loadModelFile() {
      DependencyParser parser = new DependencyParser(props);
      parser.loadModelFile(modelFile, false);
      return parser;
    }

    public String toString() {
      return this.modelFile + " ... " + this.props;
    }
  }

  private static Map<DependencyParserSpecification, WeakReference<DependencyParser>> modelCache = new HashMap<>();

  public static DependencyParser loadFromModelFile(String modelFile, Properties extraProperties) {
    DependencyParserSpecification spec = new DependencyParserSpecification(modelFile, extraProperties);
    DependencyParser parser;
    synchronized(modelCache) {
      WeakReference<DependencyParser> ref = modelCache.getOrDefault(spec, null);
      if (ref != null) {
        parser = ref.get();
        if (parser != null) {
          return parser;
        }
      }

      parser = spec.loadModelFile();
      modelCache.put(spec, new WeakReference<>(parser));
      return parser;
    }
  }
}