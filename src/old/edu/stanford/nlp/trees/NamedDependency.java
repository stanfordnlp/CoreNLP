package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.StringLabel;
import old.edu.stanford.nlp.util.ErasureUtils;
import old.edu.stanford.nlp.util.XMLUtils;


/**
 * An individual dependency between a head and a dependent.
 * The head and dependent are represented as a Label.
 * For example, these can be a
 * Word or a WordTag.  If one wishes the dependencies to preserve positions
 * in a sentence, then each can be a NamedConstituent.
 *
 * @author Christopher Manning
 */
public class NamedDependency implements Dependency<Label, Label, Object> {

  private Label regent;
  private Label dependent;
  private Object name;


  @Override
  public int hashCode() {
    return regent.hashCode() ^ dependent.hashCode() ^ name.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof NamedDependency) {
      NamedDependency d = (NamedDependency) o;
      return governor().equals(d.governor()) && dependent().equals(d.dependent());
    }
    return false;
  }

  public boolean equalsIgnoreName(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Dependency) {
      Dependency<Label, Label, Object> d = ErasureUtils.<Dependency<Label, Label, Object>>uncheckedCast(o);
      return governor().equals(d.governor()) && dependent().equals(d.dependent());
    }
    return false;
  }


  @Override
  public String toString() {
    return regent + " --" + name + "--> " + dependent;
  }

  /**
   * Provide different printing options via a String keyword.
   * The recognized options are currently "xml", and "predicate".
   * Otherwise the default toString() is used.
   */
  public String toString(String format) {
    if ("xml".equals(format)) {
      return "<dep type=\"" + XMLUtils.escapeXML(name().toString()) + "\">\n    <governor>" + XMLUtils.escapeXML(governor().value()) + "</governor>\n    <dependent>" + XMLUtils.escapeXML(dependent().value()) + "</dependent>\n  </dep>";
    } else if ("predicate".equals(format)) {
      return name() + "(" + governor() + "," + dependent() + ")";
    } else {
      return toString();
    }
  }


  public NamedDependency(String regent, String dependent, Object name) {
    this(new StringLabel(regent), new StringLabel(dependent), name);
  }

  public NamedDependency(String regent, int regentIndex, String dependent, int dependentIndex, Object name) {
    this(regent, regentIndex, regentIndex + 1, dependent, dependentIndex, dependentIndex + 1, name);
  }

  public NamedDependency(String regent, int regentStartIndex, int regentEndIndex, String dependent, int depStartIndex, int depEndIndex, Object name) {
    this(new LabeledConstituent(regentStartIndex, regentEndIndex, regent), new LabeledConstituent(depStartIndex, depEndIndex, dependent), name);
  }

  public NamedDependency(Label regent, Label dependent, Object name) {
    if (regent == null || dependent == null) {
      throw new IllegalArgumentException("governor or dependent cannot be null");
    }
    this.regent = regent;
    this.dependent = dependent;
    this.name = name;
  }

  public Label governor() {
    return regent;
  }

  public Label dependent() {
    return dependent;
  }

  public Object name() {
    return name;
  }

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

    public NamedDependencyFactory() {
    }


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


  private static final long serialVersionUID = 5;

}
