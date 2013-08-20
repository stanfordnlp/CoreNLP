package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.XMLUtils;

/**
 * A labeled dependency between a head and a dependent. The dependency
 * is associated with the token indices of the lexical items.
 *
 * @author Spence Green
 * 
 */
public class NamedConcreteDependency extends NamedDependency {

  private static final long serialVersionUID = 4694393388619235531L;

  private final int headIndex;
  private final int depIndex;

  public NamedConcreteDependency(String regent, int regentIndex, String dependent, int dependentIndex, Object name) {
    super(regent, dependent, name);

    headIndex = regentIndex;
    depIndex = dependentIndex;
  }

  public NamedConcreteDependency(Label regent, Label dependent, Object name) {
    super(regent, dependent, name);

    if (governor() instanceof HasIndex) {
      headIndex = ((HasIndex) governor()).index();
    } else {
      throw new IllegalArgumentException("Label argument lacks IndexAnnotation.");
    }
    if (dependent() instanceof HasIndex) {
      depIndex = ((HasIndex) dependent()).index();
    } else {
      throw new IllegalArgumentException("Label argument lacks IndexAnnotation.");
    }
  }
  
  public int getGovernorIndex() { return headIndex; }
  
  public int getDependentIndex() { return depIndex; }

  @Override
  public int hashCode() {
    return headIndex * (name().hashCode() ^ (depIndex << 16));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( !(o instanceof NamedConcreteDependency)) {
      return false;
    }
    NamedConcreteDependency d = (NamedConcreteDependency) o;
    return equalsIgnoreName(o) && name().equals(d.name());
  }

  @Override
  public boolean equalsIgnoreName(Object o) {
    if (this == o) {
      return true;
    } else if ( !(o instanceof NamedConcreteDependency)) {
      return false;
    }
    NamedConcreteDependency d = (NamedConcreteDependency) o;
    return headIndex == d.headIndex && depIndex == d.depIndex;
  }

  @Override
  public String toString() {
    return String.format("%s [%d] --%s--> %s [%d]", regentText, headIndex, name().toString(), dependentText, depIndex);
  }

  /**
   * Provide different printing options via a String keyword.
   * The recognized options are currently "xml", and "predicate".
   * Otherwise the default toString() is used.
   */
  @Override
  public String toString(String format) {
    if ("xml".equals(format)) {
      String govIdxStr = " idx=\"" + headIndex + "\"";
      String depIdxStr = " idx=\"" + depIndex + "\"";
      return "  <dep>\n    <governor" + govIdxStr + ">" + XMLUtils.escapeXML(governor().value()) + "</governor>\n    <dependent" + depIdxStr + ">" + XMLUtils.escapeXML(dependent().value()) + "</dependent>\n  </dep>";
    } else if ("predicate".equals(format)) {
      return "dep(" + governor() + "," + dependent() + "," + name() + ")";
    } else {
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
    private static final DependencyFactory df = new NamedConcreteDependencyFactory();
  }

  /**
   * A <code>DependencyFactory</code> acts as a factory for creating objects
   * of class <code>Dependency</code>
   */
  private static class NamedConcreteDependencyFactory implements DependencyFactory {
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
      return new NamedConcreteDependency(regent, dependent, name);
    }
  }
}
