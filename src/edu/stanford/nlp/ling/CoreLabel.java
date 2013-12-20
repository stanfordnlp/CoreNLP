package edu.stanford.nlp.ling;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import edu.stanford.nlp.ling.AnnotationLookup.KeyLookup;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;


/**
 * A CoreLabel represents a single word with ancillary information
 * attached using CoreAnnotations.  If the proper annotations are set,
 * the CoreLabel also provides convenient methods to access tags,
 * lemmas, etc.
 * <p>
 * A CoreLabel is a Map from keys (which are Class objects) to values,
 * whose type is determined by the key.  That is, it is a heterogeneous
 * typesafe Map (see Josh Bloch, Effective Java, 2nd edition).
 * <p>
 * The CoreLabel class in particular bridges the gap between old-style JavaNLP
 * Labels and the new CoreMap infrastructure.  Instances of this class can be
 * used (almost) anywhere that the now-defunct FeatureLabel family could be
 * used.  This data structure is backed by an {@link ArrayCoreMap}.
 *
 * @author dramage
 * @author rafferty
 */
public class CoreLabel extends ArrayCoreMap implements Label, HasWord, HasTag, HasCategory, HasLemma, HasContext, HasIndex, HasOffset {

  private static final long serialVersionUID = 2L;


  // /**
  //  * Should warnings be printed when converting from MapLabel family.
  //  */
  // private static final boolean VERBOSE = false;


  /** Default constructor, calls super() */
  public CoreLabel() {
    super();
  }

  /**
   * Initializes this CoreLabel, pre-allocating arrays to hold
   * up to capacity key,value pairs.  This array will grow if necessary.
   *
   * @param capacity Initial capacity of object in key,value pairs
   */
  public CoreLabel(int capacity) {
    super(capacity);
  }

  /**
   * Returns a new CoreLabel instance based on the contents of the given
   * CoreLabel.  It copies the contents of the other CoreLabel.
   * <i>Implementation note:</i> this is a the same as the constructor
   * that takes a CoreMap, but is needed to ensure unique most specific
   * type inference for selecting a constructor at compile-time.
   *
   * @param label The CoreLabel to copy
   */
  public CoreLabel(CoreLabel label) {
    this((CoreMap) label);
  }

  /**
   * Returns a new CoreLabel instance based on the contents of the given
   * CoreMap.  It copies the contents of the other CoreMap.
   *
   * @param label The CoreMap to copy
   */
  @SuppressWarnings({"unchecked"})
  public CoreLabel(CoreMap label) {
    super(label.size());
    for (Class key : label.keySet()) {
      set(key, label.get(key));
    }
  }

  /**
   * Returns a new CoreLabel instance based on the contents of the given
   * label.   Warning: The behavior of this method is a bit disjunctive!
   * If label is a CoreMap (including CoreLabel), then its entire
   * contents is copied into this label.  But, otherwise, just the
   * value() and word iff it implements HasWord is copied.
   *
   * @param label Basis for this label
   */
  @SuppressWarnings("unchecked")
  public CoreLabel(Label label) {
    super(0);
    if (label instanceof CoreMap) {
      CoreMap cl = (CoreMap) label;
      setCapacity(cl.size());
      for (Class key : cl.keySet()) {
        set(key, cl.get(key));
      }
    } else {
      if (label instanceof HasWord) {
         setWord(((HasWord)label).word());
      }
      setValue(label.value());
    }
  }

  /**
   * This constructor attempts to parse the String keys
   * into Class keys.  It's mainly useful for reading from
   * a file.  A best effort attempt is made to correctly
   * parse the keys according to the String lookup function
   * in {@link CoreAnnotations}.
   *
   * @param keys Array of Strings that are class names
   * @param values Array of values (as String)
   */
  public CoreLabel(String[] keys, String[] values) {
    super(keys.length);
    //this.map = new ArrayCoreMap();
    initFromStrings(keys, values);
  }


  /**
   * Class that all "generic" annotations extend.
   * This allows you to read in arbitrary values from a file as features, for example.
   */
  public static interface GenericAnnotation<T> extends CoreAnnotation<T> {  }
  //Unchecked is below because eclipse can't handle the level of type inference if we correctly parameterize GenericAnnotation with String
  @SuppressWarnings("unchecked")
  public static final Map<String, Class<? extends GenericAnnotation>> genericKeys = Generics.newHashMap();
  @SuppressWarnings("unchecked")
  public static final Map<Class<? extends GenericAnnotation>, String> genericValues = Generics.newHashMap();


  @SuppressWarnings("unchecked")
  private void initFromStrings(String[] keys, String[] values) {
    for (int i = 0; i < Math.min(keys.length, values.length); i++) {
      String key = keys[i];
      String value = values[i];
      KeyLookup lookup = AnnotationLookup.getCoreKey(key);

      //now work with the key we got above
      if (lookup == null) {
        if (key != null) {
          throw new UnsupportedOperationException("Unknown key " + key);
        }

        // It used to be that the following code let you put unknown keys
        // in the CoreLabel.  However, you can't create classes dynamically
        // at run time, which meant only one of these classes could ever
        // exist, which meant multiple unknown keys would clobber each
        // other and be very annoying.  It's easier just to not allow
        // it at all.
        // If it becomes possible to create classes dynamically,
        // we could add this code back.
        //if(genericKeys.containsKey(key)) {
        //  this.set(genericKeys.get(key), value);
        //} else {
        //  GenericAnnotation<String> newKey = new GenericAnnotation<String>() {
        //    public Class<String> getType() { return String.class;} };
        //  this.set(newKey.getClass(), values[i]);
        //  genericKeys.put(keys[i], newKey.getClass());
        //  genericValues.put(newKey.getClass(), keys[i]);
        //}
        // unknown key; ignore
        //if (VERBOSE) {
        //  System.err.println("CORE: CoreLabel.fromAbstractMapLabel: " +
        //      "Unknown key "+key);
        //}
      } else {
        try {
          Class<?> valueClass = AnnotationLookup.getValueType(lookup.coreKey);
          if(valueClass.equals(String.class)) {
            this.set(lookup.coreKey, values[i]);
          } else if(valueClass == Integer.class) {
            this.set(lookup.coreKey, Integer.parseInt(values[i]));
          } else if(valueClass == Double.class) {
            this.set(lookup.coreKey, Double.parseDouble(values[i]));
          } else if(valueClass == Long.class) {
            this.set(lookup.coreKey, Long.parseLong(values[i]));
          }
        } catch (Exception e) {
          e.printStackTrace();
          // unexpected value type
          System.err.println("CORE: CoreLabel.initFromStrings: "
              + "Bad type for " + key
              + ". Value was: " + value
              + "; expected "+AnnotationLookup.getValueType(lookup.coreKey));
        }
      }
    }
  }


  private static class CoreLabelFactory implements LabelFactory {

    @Override
    public Label newLabel(String labelStr) {
      CoreLabel label = new CoreLabel();
      label.setValue(labelStr);
      return label;
    }

    @Override
    public Label newLabel(String labelStr, int options) {
      return newLabel(labelStr);
    }

    @Override
    public Label newLabel(Label oldLabel) {
      if (oldLabel instanceof CoreLabel) {
        return new CoreLabel((CoreLabel)oldLabel);

      } else {
        //Map the old interfaces to the correct key/value pairs
        //Don't need to worry about HasIndex, which doesn't appear in any legacy code
        CoreLabel label = new CoreLabel();
        if (oldLabel instanceof HasWord)
          label.setWord(((HasWord) oldLabel).word());
        if (oldLabel instanceof HasTag)
          label.setTag(((HasTag) oldLabel).tag());
        if (oldLabel instanceof HasOffset) {
          label.setBeginPosition(((HasOffset) oldLabel).beginPosition());
          label.setEndPosition(((HasOffset) oldLabel).endPosition());
        }
        if (oldLabel instanceof HasCategory)
          label.setCategory(((HasCategory) oldLabel).category());
        if (oldLabel instanceof HasIndex)
          label.setIndex(((HasIndex) oldLabel).index());

        label.setValue(oldLabel.value());

        return label;
      }
    }

    @Override
    public Label newLabelFromString(String encodedLabelStr) {
      throw new UnsupportedOperationException("This code branch left blank" +
      " because we do not understand what this method should do.");
    }

  }


  /**
   * Return a factory for this kind of label
   *
   * @return The label factory
   */
  public static LabelFactory factory() {
    return new CoreLabelFactory();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LabelFactory labelFactory() {
    return CoreLabel.factory();
  }

  /**
   * Return a non-null String value for a key.
   * This method is included for backwards compatibility with AbstractMapLabel.
   * It is guaranteed to not return null; if the key is not present or
   * has a null value, it returns the empty string ("").  It is only valid to
   * call this method when key is paired with a value of type String.
   *
   * @param <KEY> A key type with a String value
   * @param key The key to return the value of.
   * @return "" if the key is not in the map or has the value <code>null</code>
   *     and the String value of the key otherwise
   */
  public <KEY extends Key<String>> String getString(Class<KEY> key) {
    String value = get(key);
    if (value == null) {
      return "";
    }
    return value;
  }


  /**
   * {@inheritDoc}
   */
//  public int size() {
//    return map.size();
//  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setFromString(String labelStr) {
    throw new UnsupportedOperationException("Cannot set from string");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setValue(String value) {
    set(CoreAnnotations.ValueAnnotation.class, value);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final String value() {
    return get(CoreAnnotations.ValueAnnotation.class);
  }

  /**
   * Set the word value for the label.  Also, clears the lemma, since
   * that may have changed if the word changed.
   */
  @Override
  public void setWord(String word) {
    set(CoreAnnotations.TextAnnotation.class, word);
    // pado feb 09: if you change the word, delete the lemma.
    remove(CoreAnnotations.LemmaAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String word() {
    return get(CoreAnnotations.TextAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setTag(String tag) {
    set(CoreAnnotations.PartOfSpeechAnnotation.class, tag);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String tag() {
    return get(CoreAnnotations.PartOfSpeechAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setCategory(String category) {
    set(CoreAnnotations.CategoryAnnotation.class, category);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String category() {
    return get(CoreAnnotations.CategoryAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAfter(String after) {
    set(CoreAnnotations.AfterAnnotation.class, after);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String after() {
    return getString(CoreAnnotations.AfterAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setBefore(String before) {
    set(CoreAnnotations.BeforeAnnotation.class, before);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public String before() {
    return getString(CoreAnnotations.BeforeAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setOriginalText(String originalText) {
    set(CoreAnnotations.OriginalTextAnnotation.class, originalText);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String originalText() {
    return getString(CoreAnnotations.OriginalTextAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String docID() {
    return get(CoreAnnotations.DocIDAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setDocID(String docID) {
    set(CoreAnnotations.DocIDAnnotation.class, docID);
  }

  /**
   * Return the named entity class of the label (or null if none).
   *
   * @return String the word value for the label
   */
  public String ner() {
    return get(CoreAnnotations.NamedEntityTagAnnotation.class);
  }

  public void setNER(String ner) {
    set(CoreAnnotations.NamedEntityTagAnnotation.class, ner);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String lemma() {
    return get(CoreAnnotations.LemmaAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setLemma(String lemma) {
    set(CoreAnnotations.LemmaAnnotation.class, lemma);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public int index() {
    Integer n = get(CoreAnnotations.IndexAnnotation.class);
    if(n == null)
      return -1;
    return n;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setIndex(int index) {
    set(CoreAnnotations.IndexAnnotation.class, index);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int sentIndex() {
    Integer n = get(CoreAnnotations.SentenceIndexAnnotation.class);
    if(n == null)
      return -1;
    return n;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSentIndex(int sentIndex) {
    set(CoreAnnotations.SentenceIndexAnnotation.class, sentIndex);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int beginPosition() {
    Integer i = get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    if(i != null) return i;
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int endPosition() {
    Integer i = get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    if(i != null) return i;
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setBeginPosition(int beginPos) {
    set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, beginPos);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setEndPosition(int endPos) {
    set(CoreAnnotations.CharacterOffsetEndAnnotation.class, endPos);
  }

  /**
   * Tag separator to use by default
   */
  public static final String TAG_SEPARATOR = "/";

  public static final String DEFAULT_FORMAT = "value-index";

  @Override
  public String toString() {
    return toString(DEFAULT_FORMAT);
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
      Map map2 = new TreeMap();
      for(Class key : this.keySet()) {
        map2.put(key.getName(), get(key));
      }
      buf.append(map2);
    } else if (format.equals("value{map}")) {
      buf.append(value());
      Map map2 = new TreeMap(asClassComparator);
      for(Class key : this.keySet()) {
        map2.put(key, get(key));
      }
      map2.remove(CoreAnnotations.ValueAnnotation.class);
      buf.append(map2);
    } else if (format.equals("value-index")) {
      buf.append(value());
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      buf.append(toPrimes());
    } else if (format.equals("value-tag-index")) {
      buf.append(value());
      String tag = tag();
      if (tag != null) {
        buf.append(TAG_SEPARATOR).append(tag);
      }
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      buf.append(toPrimes());
    } else if (format.equals("value-index{map}")) {
      buf.append(value());
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      Map<String,Object> map2 = new TreeMap<String,Object>();
      for(Class key : this.keySet()) {
        String cls = key.getName();
        // special shortening of all the Annotation classes
        int idx = cls.indexOf('$');
        if (idx >= 0) {
          cls = cls.substring(idx + 1);
        }
        map2.put(cls, this.get(key));
      }
      map2.remove("IndexAnnotation");
      map2.remove("ValueAnnotation");
      if (!map2.isEmpty()) {
        buf.append(map2);
      }
    } else if (format.equals("word")) {
      buf.append(word());
    } else if (format.equals("text-index")) {
      buf.append(this.get(CoreAnnotations.TextAnnotation.class));
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      buf.append(toPrimes());
    }
    return buf.toString();
  }

  public String toPrimes() {
    Integer copy = get(CoreAnnotations.CopyAnnotation.class);
    if (copy == null || copy == 0)
      return "";
    return StringUtils.repeat('\'', copy);
  }

  private static final Comparator<Class<?>> asClassComparator = new Comparator<Class<?>>() {
    @Override
    public int compare(Class<?> o1, Class<?> o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };

}
