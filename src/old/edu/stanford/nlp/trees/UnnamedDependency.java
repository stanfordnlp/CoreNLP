package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.StringLabel;
import old.edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import old.edu.stanford.nlp.util.CoreMap;
import old.edu.stanford.nlp.util.ErasureUtils;
import old.edu.stanford.nlp.util.XMLUtils;


/**
 * An individual dependency between a head and a dependent.
 * The head and dependent are represented as a Label.
 * For example, these can be a
 * Word or a WordTag.  If one wishes the dependencies to preserve positions
 * in a sentence, then each can be a LabeledConstituent.
 *
 * @author Christopher Manning
 */
public class UnnamedDependency implements Dependency<Label, Label, Object> {

  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private Label regent;
  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private Label dependent;

  @Override
  public int hashCode() {
    return regent.hashCode() ^ dependent.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof UnnamedDependency) {
      UnnamedDependency d = (UnnamedDependency) o;
      return governor().equals(d.governor()) && dependent().equals(d.dependent());
    }
    return false;
  }


  public boolean equalsIgnoreName(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Dependency<?, ?, ?>) {
      Dependency<Label, Label, Object> d = ErasureUtils.<Dependency<Label, Label, Object>>uncheckedCast(o);
      return governor().equals(d.governor()) && dependent().equals(d.dependent());
    }
    return false;
  }

  @Override
  public String toString() {
    return regent + " --> " + dependent;
  }

  private static String getIndexStrOrEmpty(Label lab) {
    String ans = "";
    if (lab instanceof CoreMap) {
      CoreMap aml = (CoreMap) lab;
      int idx = aml.get(IndexAnnotation.class);
      if (idx >= 0) {
        ans = " idx=\"" + idx + "\"";
      }
    }
    return ans;
  }

  /**
   * Provide different printing options via a String keyword.
   * The recognized options are currently "xml", and "predicate".
   * Otherwise the default toString() is used.
   */
  public String toString(String format) {
    if ("xml".equals(format)) {
      String govIdxStr = getIndexStrOrEmpty(governor());
      String depIdxStr = getIndexStrOrEmpty(dependent());
      return "  <dep>\n    <governor" + govIdxStr + ">" + XMLUtils.escapeXML(governor().value()) + "</governor>\n    <dependent" + depIdxStr + ">" + XMLUtils.escapeXML(dependent().value()) + "</dependent>\n  </dep>";
    } else if ("predicate".equals(format)) {
      return "dep(" + governor() + "," + dependent() + ")";
    } else {
      return toString();
    }
  }


  public UnnamedDependency(String regent, String dependent) {
    this(new StringLabel(regent), new StringLabel(dependent));
  }

  public UnnamedDependency(String regent, int regentIndex, String dependent, int dependentIndex) {
    this(regent, regentIndex, regentIndex + 1, dependent, dependentIndex, dependentIndex + 1);
  }

  public UnnamedDependency(String regent, int regentStartIndex, int regentEndIndex, String dependent, int depStartIndex, int depEndIndex) {
    this(new LabeledConstituent(regentStartIndex, regentEndIndex, regent), new LabeledConstituent(depStartIndex, depEndIndex, dependent));
  }

  public UnnamedDependency(Label regent, Label dependent) {
    if (regent == null || dependent == null) {
      throw new IllegalArgumentException("governor or dependent cannot be null");
    }
    this.regent = regent;
    this.dependent = dependent;
  }

  public Label governor() {
    return regent;
  }

  public Label dependent() {
    return dependent;
  }

  public Object name() {
    return null;
  }

  public DependencyFactory dependencyFactory() {
    return DependencyFactoryHolder.df;
  }

  public static DependencyFactory factory() {
    return DependencyFactoryHolder.df;
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class DependencyFactoryHolder {

    private static final DependencyFactory df = new UnnamedDependencyFactory();


    private DependencyFactoryHolder() {
    }
  }


  /**
   * A <code>DependencyFactory</code> acts as a factory for creating objects
   * of class <code>Dependency</code>
   */
  private static class UnnamedDependencyFactory implements DependencyFactory {

    public UnnamedDependencyFactory() {
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
      return new UnnamedDependency(regent, dependent);
    }

  }


  private static final long serialVersionUID = 5;

}
