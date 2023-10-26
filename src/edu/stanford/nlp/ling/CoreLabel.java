package edu.stanford.nlp.ling;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import edu.stanford.nlp.trees.ud.CoNLLUFeatures;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;


/**
 * A CoreLabel represents a single word with ancillary information
 * attached using CoreAnnotations.
 * A CoreLabel also provides convenient methods to access tags,
 * lemmas, etc. (if the proper annotations are set).
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
public class CoreLabel extends ArrayCoreMap implements AbstractCoreLabel, HasCategory /* , HasContext */  {

  private static final long serialVersionUID = 2L;


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
    Consumer<Class<? extends Key<?>>> savedListener = ArrayCoreMap.listener;  // don't listen to the clone operation
    ArrayCoreMap.listener = null;
    for (Class key : label.keySet()) {
      set(key, label.get(key));
    }
    ArrayCoreMap.listener = savedListener;
  }

  /**
   * Returns a new CoreLabel instance based on the contents of the given
   * label.   Warning: The behavior of this method is a bit disjunctive!
   * If label is a CoreMap (including CoreLabel), then its entire
   * contents is copied into this label.
   * If label is an IndexedWord, then the backing label is copied over
   * entirely.
   * But, otherwise, just the
   * value() and word iff it implements {@link HasWord} is copied.
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
    } else if (label instanceof IndexedWord) {
      CoreMap cl = ((IndexedWord) label).backingLabel();
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
   * This constructor attempts to use preparsed Class keys.
   * It's mainly useful for reading from a file.
   *
   * @param keys Array of key classes
   * @param values Array of values (as String)
   */
  @SuppressWarnings("rawtypes")
  public CoreLabel(Class[] keys, String[] values) {
    super(keys.length);
    //this.map = new ArrayCoreMap();
    initFromStrings(keys, values);
  }

  /** This is provided as a simple way to make a CoreLabel for a word from a String.
   *  It's often useful in fixup or test code. It sets all three of the Text, OriginalText,
   *  and Value annotations to the given value.
   *
   *  @param word The word string to make a CoreLabel for
   *  @return A CoreLabel for this word string
   */
  public static CoreLabel wordFromString(String word) {
    CoreLabel cl = new CoreLabel();
    cl.setWord(word);
    cl.setOriginalText(word);
    cl.setValue(word);
    return cl;
  }

  /**
   * Class that all "generic" annotations extend.
   * This allows you to read in arbitrary values from a file as features, for example.
   */
  public interface GenericAnnotation<T> extends CoreAnnotation<T> {  }

  public static final Map<String, Class<? extends GenericAnnotation<String>>> genericKeys = Generics.newHashMap();

  public static final Map<Class<? extends GenericAnnotation<String>>, String> genericValues = Generics.newHashMap();


  @SuppressWarnings({"unchecked", "rawtypes"})
  private void initFromStrings(String[] keys, String[] values) {
    if (keys.length != values.length) {
      throw new UnsupportedOperationException("Argument array lengths differ: " +
              Arrays.toString(keys) + " vs. " + Arrays.toString(values));
    }
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      String value = values[i];
      Class coreKeyClass = AnnotationLookup.toCoreKey(key);

      //now work with the key we got above
      if (coreKeyClass == null) {
        if (key != null) {
          throw new UnsupportedOperationException("Unknown key " + key);
        }
      } else {
        try {
          Class<?> valueClass = AnnotationLookup.getValueType(coreKeyClass);
          if(valueClass.equals(String.class)) {
            this.set(coreKeyClass, values[i]);
          } else if(valueClass == Integer.class) {
            this.set(coreKeyClass, Integer.parseInt(values[i]));
          } else if(valueClass == Double.class) {
            this.set(coreKeyClass, Double.parseDouble(values[i]));
          } else if(valueClass == Long.class) {
            this.set(coreKeyClass, Long.parseLong(values[i]));
          } else if (valueClass == Boolean.class) {
            this.set(coreKeyClass, Boolean.parseBoolean(values[i]));
          } else if (coreKeyClass == CoreAnnotations.CoNLLUFeats.class) {
            this.set(coreKeyClass, new CoNLLUFeatures(values[i]));
          } else {
            throw new UnsupportedOperationException("CORE: CoreLabel.initFromStrings: " +
                                                    "Can't handle " + valueClass + " (key " + key + ")");
          }
        } catch (NumberFormatException e) {
          // unexpected value type
          throw new UnsupportedOperationException("CORE: CoreLabel.initFromStrings: "
              + "Bad type for " + key
              + ". Value was: " + value
              + "; expected "+AnnotationLookup.getValueType(coreKeyClass), e);
        }
      }
    }
  }

  @SuppressWarnings("rawtypes")
  public static Class[] parseStringKeys(String[] keys) {
    Class[] classes = new Class[keys.length];
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      classes[i] = AnnotationLookup.toCoreKey(key);

      // now work with the key we got above
      if (classes[i] == null) {
        throw new UnsupportedOperationException("Unknown key " + key);
      }
    }
    return classes;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void initFromStrings(Class[] keys, String[] values) {
    if (keys.length != values.length) {
      throw new UnsupportedOperationException("Argument array lengths differ: " +
              Arrays.toString(keys) + " vs. " + Arrays.toString(values));
    }
    for (int i = 0; i < keys.length; i++) {
      Class coreKeyClass = keys[i];
      String value = values[i];
      try {
        Class<?> valueClass = AnnotationLookup.getValueType(coreKeyClass);
        if (valueClass.equals(String.class)) {
          this.set(coreKeyClass, values[i]);
        } else if (valueClass == Integer.class) {
          this.set(coreKeyClass, Integer.parseInt(values[i]));
        } else if (valueClass == Double.class) {
          this.set(coreKeyClass, Double.parseDouble(values[i]));
        } else if (valueClass == Long.class) {
          this.set(coreKeyClass, Long.parseLong(values[i]));
        } else if (valueClass == Boolean.class) {
          this.set(coreKeyClass, Boolean.parseBoolean(values[i]));
        } else if (coreKeyClass == CoreAnnotations.CoNLLUFeats.class) {
          this.set(coreKeyClass, new CoNLLUFeatures(values[i]));
        } else {
          throw new UnsupportedOperationException("CORE: CoreLabel.initFromStrings: " +
                                                  "Can't handle " + valueClass + " (key " + coreKeyClass + ")");
        }
      } catch (NumberFormatException e) {
        // unexpected value type
        throw new UnsupportedOperationException("CORE: CoreLabel.initFromStrings: "
            + "Bad type for " + coreKeyClass.getSimpleName()
            + ". Value was: " + value
            + "; expected "+AnnotationLookup.getValueType(coreKeyClass), e);
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
   * {@inheritDoc}
   */
  @Override
  public <KEY extends Key<String>> String getString(Class<KEY> key) {
    return this.getString(key, "");
  }

  @Override
  public <KEY extends Key<String>> String getString(Class<KEY> key, String def) {
    String value = get(key);
    if (value == null) {
      return def;
    }
    return value;
  }

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
    String originalWord = get(CoreAnnotations.TextAnnotation.class);
    set(CoreAnnotations.TextAnnotation.class, word);
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
   * {@inheritDoc}
   */
  @Override
  public String ner() {
    return get(CoreAnnotations.NamedEntityTagAnnotation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setNER(String ner) {
    set(CoreAnnotations.NamedEntityTagAnnotation.class, ner);
  }

  /**
   * Return the map of confidences
   */
  public Map<String,Double> nerConfidence() { return get(CoreAnnotations.NamedEntityTagProbsAnnotation.class); }

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
   * Get value of IsNewlineAnnotation
   *
   * @return value of IsNewlineAnnotation
   */
  public Boolean isNewline() {
    return get(CoreAnnotations.IsNewlineAnnotation.class);
  }

  /**
   * Set value of IsNewlineAnnotation
   */
  public void setIsNewline(boolean isNewline) {
    set(CoreAnnotations.IsNewlineAnnotation.class, isNewline);
  }

  /**
   * Get value of IsMultiWordToken
   *
   * @return value of IsMultiWordTokenAnnotation
   */
  public Boolean isMWT() {
    return get(CoreAnnotations.IsMultiWordTokenAnnotation.class);
  }

  /**
   * Get value of IsFirstWordOfMWT
   *
   * @return value of IsFirstMultiWordAnnotation
   */
  public Boolean isMWTFirst() {
    return get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class);
  }

  /**
   * Set value of IsMultiWordToken
   */
  public void setIsMWT(boolean isMWT) {
    set(CoreAnnotations.IsMultiWordTokenAnnotation.class, isMWT);
  }

  /**
   * Set value of IsFirstWordOfMWT
   */
  public void setIsMWTFirst(boolean isFirstMWT) {
    set(CoreAnnotations.IsFirstWordOfMWTAnnotation.class,
        isFirstMWT);
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
   * Similar to a copy node in IndexedWord, but not exactly,
   * as the word can have its own attributes.
   * Fairly common in enhanced graphs in UD
   */
  public int getEmptyIndex() {
    Integer index = get(CoreAnnotations.EmptyIndexAnnotation.class);
    if (index == null) {
      return 0;
    }
    return index;
  }

  /**
   * Similar to a copy node in IndexedWord, but not exactly,
   * as the word can have its own attributes.
   * Fairly common in enhanced graphs in UD
   */
  public void setEmptyIndex(int empty) {
    set(CoreAnnotations.EmptyIndexAnnotation.class, empty);
  }

  /**
   * Keeping track of this can save us a little effort when serializing CoreLabels
   */
  public boolean hasEmptyIndex() {
    Integer index = get(CoreAnnotations.EmptyIndexAnnotation.class);
    return index != null;
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

  public enum OutputFormat {
    VALUE_INDEX, VALUE, VALUE_TAG, VALUE_TAG_INDEX, MAP, VALUE_MAP, VALUE_INDEX_MAP, WORD, WORD_INDEX, VALUE_TAG_NER, LEMMA_INDEX, ALL
  }

  public static final OutputFormat DEFAULT_FORMAT = OutputFormat.VALUE_INDEX;

  @Override
  public String toString() {
    return toString(DEFAULT_FORMAT);
  }

  /**
   * Returns a formatted string representing this label.  The
   * desired format is passed in as a {@code String}.
   * Currently supported formats include:
   * <ul>
   * <li>"value": just prints the value</li>
   * <li>"{map}": prints the complete map</li>
   * <li>"value{map}": prints the value followed by the contained
   * map (less the map entry containing key {@code CATEGORY_KEY})</li>
   * <li>"value-index": extracts a value and an integer index from
   * the contained map using keys  {@code INDEX_KEY},
   * respectively, and prints them with a hyphen in between</li>
   * <li>"value-tag"
   * <li>"value-tag-index"
   * <li>"value-index{map}": a combination of the above; the index is
   * displayed first and then not shown in the map that is displayed</li>
   * <li>"word": Just the value of HEAD_WORD_KEY in the map</li>
   * </ul>
   * <p>
   * Map is printed in alphabetical order of keys.
   */
  @SuppressWarnings("unchecked")
  public String toString(OutputFormat format) {
    StringBuilder buf = new StringBuilder();
    switch(format) {
    case VALUE:
      buf.append(value());
      break;
    case MAP: {
      Map map2 = new TreeMap();
      for (Class key : this.keySet()) {
        map2.put(key.getName(), get(key));
      }
      buf.append(map2);
      break;
    }
    case VALUE_MAP: {
      buf.append(value());
      Map map2 = new TreeMap(asClassComparator);
      for (Class key : this.keySet()) {
        map2.put(key, get(key));
      }
      map2.remove(CoreAnnotations.ValueAnnotation.class);
      buf.append(map2);
      break;
    }
    case VALUE_INDEX: {
      buf.append(value());
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      Integer emptyIndex = this.get(CoreAnnotations.EmptyIndexAnnotation.class);
      if (emptyIndex != null && emptyIndex != 0) {
        buf.append('.').append((emptyIndex).intValue());
      }
      break;
    }
    case VALUE_TAG: {
      buf.append(value());
      String tag = tag();
      if (tag != null) {
        buf.append(TAG_SEPARATOR).append(tag);
      }
      break;
    }
    case VALUE_TAG_INDEX: {
      buf.append(value());
      String tag = tag();
      if (tag != null) {
        buf.append(TAG_SEPARATOR).append(tag);
      }
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      Integer emptyIndex = this.get(CoreAnnotations.EmptyIndexAnnotation.class);
      if (emptyIndex != null && emptyIndex != 0) {
        buf.append('.').append((emptyIndex).intValue());
      }
      break;
    }
    case VALUE_INDEX_MAP: {
      buf.append(value());
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      Integer emptyIndex = this.get(CoreAnnotations.EmptyIndexAnnotation.class);
      if (emptyIndex != null && emptyIndex != 0) {
        buf.append('.').append((emptyIndex).intValue());
      }
      Map<String,Object> map2 = new TreeMap<>();
      for (Class key : this.keySet()) {
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
      break;
    }
    case WORD:
      // TODO: maybe we should unify word() and value(). [cdm 2015] I think not, rather maybe remove value and redefine category.
      buf.append(word());
      break;
    case WORD_INDEX: {
      buf.append(this.get(CoreAnnotations.TextAnnotation.class));
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      Integer emptyIndex = this.get(CoreAnnotations.EmptyIndexAnnotation.class);
      if (emptyIndex != null && emptyIndex != 0) {
        buf.append('.').append((emptyIndex).intValue());
      }
      break;
    }
    case VALUE_TAG_NER:{
      buf.append(value());
      String tag = tag();
      if (tag != null) {
        buf.append(TAG_SEPARATOR).append(tag);
      }
      if(ner() != null){
        buf.append(TAG_SEPARATOR).append(ner());
      }
      break;
    }
    case LEMMA_INDEX:
      buf.append(lemma());
      Integer index = this.get(CoreAnnotations.IndexAnnotation.class);
      if (index != null) {
        buf.append('-').append((index).intValue());
      }
      Integer emptyIndex = this.get(CoreAnnotations.EmptyIndexAnnotation.class);
      if (emptyIndex != null && emptyIndex != 0) {
        buf.append('.').append((emptyIndex).intValue());
      }
      break;
    case ALL:{
      for (Class en: this.keySet()) {
        buf.append(';').append(en).append(':').append(this.get(en));
      }
      break;
    }
    default:
      throw new IllegalArgumentException("Unknown format " + format);
    }
    return buf.toString();
  }

  private static final Comparator<Class<?>> asClassComparator =
          Comparator.comparing(Class::getName);

}
