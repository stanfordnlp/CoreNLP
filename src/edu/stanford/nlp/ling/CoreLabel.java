package edu.stanford.nlp.ling;

import java.util.HashMap;
import java.util.Set;

import edu.stanford.nlp.ling.AnnotationLookup.KeyLookup;
import edu.stanford.nlp.ling.CoreAnnotations.AfterAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BeforeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CategoryAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.DocIDAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;


/**
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
    Set<Class<?>> otherKeys = label.keySet();
    for (Class key : otherKeys) {
      set(key, label.get(key));
    }
  }

  /**
   * Returns a new CoreLabel instance based on the contents of the given
   * label.   Warning: The behavior of this method is a bit disjunctive!
   * If label is a CoreMap (including CoreLabel), then it's entire
   * contents is copied into this label.  But, otherwise, just the
   * value() is copied.
   *
   * @param label Basis for this label
   */
  public CoreLabel(Label label) {
    super(1);
    if (label instanceof CoreMap) {
      CoreMap cl = (CoreMap) label;
      Set<Class<?>> otherKeys = cl.keySet();
      setCapacity(otherKeys.size());
      for (Class key : otherKeys) {
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
  public static HashMap<String, Class<? extends GenericAnnotation>> genericKeys = new HashMap<String, Class<? extends GenericAnnotation>>();
  @SuppressWarnings("unchecked")
  public static HashMap<Class<? extends GenericAnnotation>, String> genericValues = new HashMap<Class<? extends GenericAnnotation>, String>();


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
            this.set((Class<? extends CoreAnnotation>)lookup.coreKey, values[i]);
          } else if(valueClass == Integer.class) {
            this.set((Class<? extends CoreAnnotation>)lookup.coreKey, Integer.parseInt(values[i]));
          } else if(valueClass == Double.class) {
            this.set((Class<? extends CoreAnnotation>)lookup.coreKey, Double.parseDouble(values[i]));
          } else if(valueClass == Long.class) {
            this.set((Class<? extends CoreAnnotation>)lookup.coreKey, Long.parseLong(values[i]));
          }
        } catch(Exception e) {
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

    public Label newLabel(String labelStr) {
      CoreLabel label = new CoreLabel();
      label.setValue(labelStr);
      return label;
    }

    public Label newLabel(String labelStr, int options) {
      return newLabel(labelStr);
    }

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
  public <KEY extends Key<CoreMap, String>> String getString(Class<KEY> key) {
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
  public void setFromString(String labelStr) {
    throw new UnsupportedOperationException("Cannot set from string");
  }

  /**
   * {@inheritDoc}
   */
  public final void setValue(String value) {
    set(ValueAnnotation.class, value);
  }

  /**
   * {@inheritDoc}
   */
  public final String value() {
    return get(ValueAnnotation.class);
  }

  /**
   * Set the word value for the label.  Also, clears the lemma, since
   * that may have changed if the word changed.
   */
  public void setWord(String word) {
    set(TextAnnotation.class, word);
    // pado feb 09: if you change the word, delete the lemma.
    remove(LemmaAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public String word() {
    return get(TextAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public void setTag(String tag) {
    set(PartOfSpeechAnnotation.class, tag);
  }

  /**
   * {@inheritDoc}
   */
  public String tag() {
    return get(PartOfSpeechAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public void setCategory(String category) {
    set(CategoryAnnotation.class, category);
  }

  /**
   * {@inheritDoc}
   */
  public String category() {
    return get(CategoryAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public void setAfter(String after) {
    set(AfterAnnotation.class, after);
  }

  /**
   * {@inheritDoc}
   */
  public String after() {
    return getString(AfterAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public void setBefore(String before) {
    set(BeforeAnnotation.class, before);
  }


  /**
   * {@inheritDoc}
   */
  public String before() {
    return getString(BeforeAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public void setOriginalText(String originalText) {
    set(CoreAnnotations.OriginalTextAnnotation.class, originalText);
  }

  /**
   * {@inheritDoc}
   */
  public String originalText() {
    return getString(OriginalTextAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public String docID() {
    return get(DocIDAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  public void setDocID(String docID) {
    set(DocIDAnnotation.class, docID);
  }

  /**
   * Return the named entity class of the label (or null if none).
   *
   * @return String the word value for the label
   */
  public String ner() {
    return get(NamedEntityTagAnnotation.class);
  }

  public void setNER(String ner) {
    set(NamedEntityTagAnnotation.class, ner);
  }

  /**
   * Return the lemma of the label (or null if none).
   *
   * @return String the word value for the label
   */
  public String lemma() {
    return get(LemmaAnnotation.class);
  }

  public void setLemma(String lemma) {
    set(LemmaAnnotation.class, lemma);
  }


  /**
   * {@inheritDoc}
   */
  public int index() {
    Integer n = get(IndexAnnotation.class);
    if(n == null)
      return -1;
    return n;
  }

  /**
   * {@inheritDoc}
   */
  public void setIndex(int index) {
    set(IndexAnnotation.class, index);
  }

  /**
   * {@inheritDoc}
   */
  public int sentIndex() {
    Integer n = get(SentenceIndexAnnotation.class);
    if(n == null)
      return -1;
    return n;
  }

  /**
   * {@inheritDoc}
   */
  public void setSentIndex(int sentIndex) {
    set(SentenceIndexAnnotation.class, sentIndex);
  }

  public int beginPosition() {
    Integer i = get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    if(i != null) return i;
    return -1;
  }

  public int endPosition() {
    Integer i = get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    if(i != null) return i;
    return -1;
  }

  public void setBeginPosition(int beginPos) {
    set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, beginPos);
  }

  public void setEndPosition(int endPos) {
    set(CoreAnnotations.CharacterOffsetEndAnnotation.class, endPos);
  }

  /**
   * Tag separator to use by default
   */
  public static final String TAG_SEPARATOR = "/";

  /** {@inheritDoc} */
//  public <VALUE, KEY extends edu.stanford.nlp.util.TypesafeMap.Key<CoreMap, VALUE>>
//  VALUE get(Class<KEY> key) {
//    return map.get(key);
//  }

  /** {@inheritDoc} */
//  public <VALUE, KEY extends edu.stanford.nlp.util.TypesafeMap.Key<CoreMap, VALUE>>
//  boolean has(Class<KEY> key) {
//    return map.has(key);
//  }

  /** {@inheritDoc} */
//  public Set<Class<?>> keySet() {
//    return map.keySet();
//  }

  /** {@inheritDoc} */
//  public <VALUE, KEY extends edu.stanford.nlp.util.TypesafeMap.Key<CoreMap, VALUE>>
//  VALUE remove(Class<KEY> key) {
//    return map.remove(key);
//  }

  /** {@inheritDoc} */
//  public <VALUEBASE, VALUE extends VALUEBASE, KEY extends edu.stanford.nlp.util.TypesafeMap.Key<CoreMap, VALUEBASE>>
//  VALUE set(Class<KEY> key, VALUE value) {
//    return map.set(key, value);
//  }

  /** {@inheritDoc} */
//  public <VALUE, KEY extends Key<CoreMap, VALUE>>
//  boolean containsKey(Class<KEY> key) {
//    return map.containsKey(key);
//  }

 // @Override
//  public String toString() {
//    return value();
//    return map.toString();
//  }

 // @Override
//  public boolean equals(Object other) {
//    if (other instanceof CyclicCoreLabel) {
//      // CyclicCoreLabel overrides our equality, use its
//      return other.equals(this);
//    } else if (other instanceof CoreLabel) {
//      // If its a CoreLabel, compare our map with its
//      return map.equals(((CoreLabel)other).map);
//    } else if (other instanceof CoreMap) {
//      // If its any other type of CoreMap, compare our map with it directly
//      return map.equals(other);
//    } else {
//      return false;
//    }
//  }

 // @Override
//  public int hashCode() {
//    return map.hashCode();
//  }

}
