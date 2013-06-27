package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.XMLUtils;

/**
 * An individual dependency between a head and a dependent. The dependency
 * is associated with the token indices of the lexical items.
 * <p>
 * A key difference between this class and UnnamedDependency is the equals()
 * method. Equality of two UnnamedConcreteDependency objects is defined solely
 * with respect to the indices. The surface forms are not considered. This permits 
 * a use case in which dependencies in two different parse trees have slightly different 
 * pre-processing, possibly due to pre-processing.
 * 
 * @author Spence Green
 * 
 */
public class UnnamedConcreteDependency extends UnnamedDependency {

  private static final long serialVersionUID = -8836949694741145222L;

  private final int headIndex;
  private final int depIndex;

  public UnnamedConcreteDependency(String regent, int regentIndex, String dependent, int dependentIndex) {
    super(regent, dependent);

    headIndex = regentIndex;
    depIndex = dependentIndex;
  }
  
  public UnnamedConcreteDependency(Label regent, int regentIndex, Label dependent, int dependentIndex) {
    super(regent, dependent);
    
    headIndex = regentIndex;
    depIndex = dependentIndex;
  }

  public UnnamedConcreteDependency(Label regent, Label dependent) {
    super(regent, dependent);

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
    return headIndex * (depIndex << 16);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if( !(o instanceof UnnamedConcreteDependency)) {
      return false;
    }
    UnnamedConcreteDependency d = (UnnamedConcreteDependency) o;
    return headIndex == d.headIndex && depIndex == d.depIndex;
  }

  @Override
  public String toString() {
    String headWord = getText(governor());
    String depWord = getText(dependent());
    return String.format("%s [%d] --> %s [%d]", headWord, headIndex, depWord, depIndex);
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
      return "dep(" + governor() + "," + dependent() + ")";
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
    private static final DependencyFactory df = new UnnamedConcreteDependencyFactory();
  }

  /**
   * A <code>DependencyFactory</code> acts as a factory for creating objects
   * of class <code>Dependency</code>
   */
  private static class UnnamedConcreteDependencyFactory implements DependencyFactory {
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
      return new UnnamedConcreteDependency(regent, dependent);
    }
  }
}
