package old.edu.stanford.nlp.ling;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import old.edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import old.edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import old.edu.stanford.nlp.util.CoreMap;

/**
 * Version of CoreLabel that allows for cycles in values/keys.
 *
 * Equals is defined as object equality, hashcode is defined on
 * object address, and toString will not print cycles.
 *
 * TODO: This class may be removable if it is the case that
 * TreeGraphNode (it's main user) doesn't actually need the
 * cyclic semantics (because we fixed a bug in its lack of
 * hashCode).
 *
 * @author rafferty
 *
 */
public class CyclicCoreLabel extends CoreLabel {

  private static final long serialVersionUID = 1L;

  /**
   * Based on MapLabel printing structure
   */
  private static String printOptions = "value-index";

  /** Default constructor, calls super() */
  public CyclicCoreLabel() {
    super();
  }

  /** Copy constructor from any CoreMap. */
  public CyclicCoreLabel(Label label) {
    super(label);
  }

  /** Copy constructor from any CoreMap. */
  public CyclicCoreLabel(CoreMap label) {
    super(label);
  }

  /** Copy constructor from any CoreMap. */
  public CyclicCoreLabel(CoreLabel label) {
    super(label);
  }

  /** Copy constructor from any CoreMap. */
  public CyclicCoreLabel(CyclicCoreLabel label) {
    this((CoreMap) label);
  }

  /**
   * Two CoreMaps are equal iff all keys and values are equal.
   */
  @Override
  public boolean equals(Object obj) {
    return this.cyclicEquals(obj);
  }

  /**
   * Returns a composite hashcode over all the keys and values currently
   * stored in the map.  Because they may change over time, this class
   * is not appropriate for use as map keys.
   */
  @Override
  public int hashCode() {
    return this.cyclicHashCode();
  }

  private static final Comparator<Class<?>> asClassComparator = new Comparator<Class<?>>() {
    public int compare(Class<?> o1, Class<?> o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };
  /**
   * Return a <code>String</code> containing the value (and index,
   * if any) of this label.  This is equivalent to
   * toString("value-index").
   */
  @Override
  public String toString() {
    return toString(printOptions);
  }

  /**
   * Returns a formatted string representing this label.  The
   * desired format is passed in as a <code>String</code>.
   * Currently supported formats include:
   * <ul>
   * <li>"value": just prints the value</li>
   * <li>"{map}": prints the complete map</li>
   * <li>"value{map}": prints the value followed by the contained
   * map (less the map entry containing key <code>CATEGORY_KEY</code>)</li>
   * <li>"value-index": extracts a value and an integer index from
   * the contained map using keys  <code>INDEX_KEY</code>,
   * respectively, and prints them with a hyphen in between</li>
   * <li>"value-index{map}": a combination of the above; the index is
   * displayed first and then not shown in the map that is displayed</li>
   * <li>"word": Just the value of HEAD_WORD_KEY in the map</li>
   * </ul>
   * <p/>
   * Map is printed in alphabetical order of keys.
   */
  @SuppressWarnings("unchecked")
  public String toString(String format) {
    StringBuilder buf = new StringBuilder();
    if (format.equals("value")) {
      buf.append(value());
    } else if (format.equals("{map}")) {
      Map map2 = new TreeMap(asClassComparator);
      for(Class<?> key : this.keySet()) {
        map2.put(key, get((Class<? extends CoreAnnotation>) key));
      }
      buf.append(map2);
    } else if (format.equals("value{map}")) {
      buf.append(value());
      Map map2 = new TreeMap(asClassComparator);
      for(Class<?> key : this.keySet()) {
        map2.put(key, get((Class<? extends CoreAnnotation>) key));
      }
      map2.remove(ValueAnnotation.class);
      buf.append(map2);
    } else if (format.equals("value-index")) {
      buf.append(value());
      Integer index = this.get(IndexAnnotation.class);
      if (index != null) {
        buf.append("-").append((index).intValue());
      }
    } else if (format.equals("value-index{map}")) {
      buf.append(value());
      Integer index = this.get(IndexAnnotation.class);
      if (index != null) {
        buf.append("-").append((index).intValue());
      }
      Map map2 = new TreeMap(asClassComparator);
      for(Class<?> key : this.keySet()) {
        map2.put(key, this.get((Class<? extends CoreAnnotation>) key));
      }
      map2.remove(IndexAnnotation.class);
      map2.remove(ValueAnnotation.class);
      if (!map2.isEmpty()) {
        buf.append(map2);
      }
    } else if (format.equals("word")) {
      buf.append(word());
    }
    return buf.toString();
  }

  public static LabelFactory factory() {
    return new LabelFactory() {

      public Label newLabel(String labelStr) {
        CyclicCoreLabel label = new CyclicCoreLabel();
        label.setValue(labelStr);
        return label;
      }

      public Label newLabel(String labelStr, int options) {
        return newLabel(labelStr);
      }

      public Label newLabel(Label oldLabel) {
        return new CyclicCoreLabel(oldLabel);
      }

      public Label newLabelFromString(String encodedLabelStr) {
        throw new UnsupportedOperationException("This code branch left blank" +
            " because we do not understand what this method should do.");
      }
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LabelFactory labelFactory() {
    return CyclicCoreLabel.factory();
  }

  /**
   * To string method that works regardless of whether cycles
   * exist
   */
  public String cyclicToString() {
    return toString();
  }

  /**
   * Hashcode function that works on object address
   * equality.  Compatible with cyclicEquals
   */
  @SuppressWarnings("unchecked")
  public int cyclicHashCode() {
    int keyscode = 0;
    int valuescode = 0;
    for (Class key : keySet()) {
      keyscode += key.hashCode();
      valuescode += (get(key) != null ? System.identityHashCode(get(key)) : 0);
    }
    return keyscode * 37 + valuescode;
  }

  /**
   * Equals method that is well defined even if there
   * are cycles in keys/values.  Checks for object address
   * equality for key-value pairs.
   */
  @SuppressWarnings("unchecked")
  public boolean cyclicEquals(Object obj) {
    if (!(obj instanceof CoreLabel)) {
      return false;
    }

    CoreLabel other = (CoreLabel) obj;

    if (!this.keySet().equals(other.keySet())) {
      return false;
    }

    for (Class key : this.keySet()) {
      if (this.get(key) != other.get(key)) {
        return false;
      }
    }

    return true;
  }
}
