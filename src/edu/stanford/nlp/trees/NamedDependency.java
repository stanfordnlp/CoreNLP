package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.XMLUtils;

/**
 * An individual dependency between a head and a dependent.
 * The head and dependent are represented as a Label.
 * For example, these can be a
 * Word or a WordTag.  If one wishes the dependencies to preserve positions
 * in a sentence, then each can be a NamedConstituent.
 *
 * @author Christopher Manning
 * @author Spence Green
 * 
 */
public class NamedDependency extends UnnamedDependency {

  private static final long serialVersionUID = -1635646451505721133L;

  private final Object name;

  public NamedDependency(String regent, String dependent, Object name) {
    super(regent, dependent);
    this.name = name;
  }

  public NamedDependency(Label regent, Label dependent, Object name) {
    super(regent, dependent);
    this.name = name;
  }

  @Override
  public Object name() {
    return name;
  }

  @Override
  public int hashCode() {
    return regentText.hashCode() ^ dependentText.hashCode() ^ name.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( !(o instanceof NamedDependency)) {
      return false;
    }
    NamedDependency d = (NamedDependency) o;
    return equalsIgnoreName(o) && name.equals(d.name);
  }

  @Override
  public String toString() {
    return String.format("%s --%s--> %s", regentText, name.toString(), dependentText);
  }
  
  /**
   * Provide different printing options via a String keyword.
   * The recognized options are currently "xml", and "predicate".
   * Otherwise the default toString() is used.
   */
  @Override
  public String toString(String format) {
    switch (format) {
      case "xml":
        return "  <dep>\n    <governor>" + XMLUtils.escapeXML(governor().value()) + "</governor>\n    <dependent>" + XMLUtils.escapeXML(dependent().value()) + "</dependent>\n  </dep>";
      case "predicate":
        return "dep(" + governor() + "," + dependent() + "," + name() + ")";
      default:
        return toString();
    }
  }

  @Override
  public DependencyFactory dependencyFactory() {
    return DependencyFactoryHolder.df;
  }
  
  public static DependencyFactory factory() {
    return DependencyFactoryHolder.df;
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class DependencyFactoryHolder {
    private static final DependencyFactory df = new NamedDependencyFactory();
  }

  /**
   * A <code>DependencyFactory</code> acts as a factory for creating objects
   * of class <code>Dependency</code>
   */
  private static class NamedDependencyFactory implements DependencyFactory {
    /**
     * Create a new <code>Dependency</code>.
     */
    public Dependency<Label, Label, Object> newDependency(Label regent, Label dependent) {
      return newDependency(regent, dependent, null);
    }

    /**
     * Create a new <code>Dependency</code>.
     */
    public Dependency<Label, Label, Object> newDependency(Label regent, Label dependent, Object name) {
      return new NamedDependency(regent, dependent, name);
    }
  }
}
