package edu.stanford.nlp.ling;

import java.util.Objects;
import java.util.Set;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TypesafeMap;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * This class provides a {@link CoreLabel} that uses its
 * DocIDAnnotation, SentenceIndexAnnotation, and IndexAnnotation to implement
 * Comparable/compareTo, hashCode, and equals.  This means no other annotations,
 * including the identity of the word, are taken into account when using these
 * methods. Historically, this class was introduced for and is mainly used in
 * the RTE package, and it provides a number of methods that are really specific
 * to that use case. A second use case is now the Stanford Dependencies code,
 * where this class directly implements the "copy nodes" of section 4.6 of the
 * Stanford Dependencies Manual, rather than these being placed directly in the
 * backing CoreLabel. This was so there can stay one CoreLabel per token, despite
 * there being multiple IndexedWord nodes, additional ones representing copy
 * nodes.
 * <p>
 * The actual implementation is to wrap a {@code CoreLabel}.
 * This avoids breaking the {@code equals()} and
 * {@code hashCode()} contract and also avoids expensive copying
 * when used to represent the same data as the original
 * {@code CoreLabel}.
 *
 * @author rafferty
 * @author John Bauer
 * @author Sonal Gupta
 */
public class IndexedWord implements AbstractCoreLabel, Comparable<IndexedWord>  {

  /** A logger for this class */
  private static final  Redwood.RedwoodChannels log = Redwood.channels(IndexedWord.class);

  private static final long serialVersionUID = 3739633991145239829L;

  /**
   * The identifier that points to no word.
   */
  public static final IndexedWord NO_WORD = new IndexedWord(null, -1, -1);

  private final CoreLabel label;

  private int copyCount; // = 0;

  private int numCopies; // = 0;

  /** Stores the original IndexedWord when you make copy nodes. */
  private IndexedWord original; // = null;

  /**
   * Useful for specifying a fine-grained position when butchering parse trees.
   * The canonical use case for this is resolving coreference in the OpenIE system, where
   * we want to move nodes between sentences, but do not want to change their index annotation
   * (plus, we need to have multiple nodes fit into the space of one pronoun).
   */
  private double pseudoPosition = Double.NaN;

  /**
   * Default constructor; uses {@link CoreLabel} default constructor
   */
  public IndexedWord() {
    label = new CoreLabel();
  }


  /**
   * Copy Constructor - relies on {@link CoreLabel} copy constructor
   * It will set the value, and if the word is not set otherwise, set
   * the word to the value.
   *
   * @param w A Label to initialize this IndexedWord from
   */
  public IndexedWord(Label w) {
    if (w instanceof CoreLabel) {
      this.label = (CoreLabel) w;
    } else {
      label = new CoreLabel(w);
      if (label.word() == null) {
        label.setWord(label.value());
      }
    }
  }

  /**
   * Construct an IndexedWord from a CoreLabel just as for a CoreMap.
   * <i>Implementation note:</i> this is a the same as the constructor
   * that takes a CoreMap, but is needed to ensure unique most specific
   * type inference for selecting a constructor at compile-time.
   *
   * @param w A Label to initialize this IndexedWord from
   */
  public IndexedWord(CoreLabel w) {
    label = w;
  }

  /**
   * Constructor for setting docID, sentenceIndex, and
   * index without any other annotations.
   *
   * @param docID The document ID (arbitrary string)
   * @param sentenceIndex The sentence number in the document (normally 0-based)
   * @param index The index of the word in the sentence (normally 0-based)
   */
  public IndexedWord(String docID, int sentenceIndex, int index) {
    label = new CoreLabel();
    label.set(CoreAnnotations.DocIDAnnotation.class, docID);
    label.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);
    label.set(CoreAnnotations.IndexAnnotation.class, index);
  }

  private IndexedWord makeCopy(int count) {
    CoreLabel labelCopy = new CoreLabel(label);
    IndexedWord copy = new IndexedWord(labelCopy);
    copy.setCopyCount(count);
    return copy;
  }

  /** This copies the whole IndexedWord, incrementing a copy count.
   *  We've subsequently decided that it is better to use soft copies, where the IndexedWord is copied,
   *  but the backing CoreLabel is kept the same. See {@link #makeSoftCopy()}.
   */
  @SuppressWarnings("unused")
  public IndexedWord makeCopy() {
    return makeCopy(++numCopies);
  }

  public IndexedWord makeSoftCopy(int count) {
    IndexedWord copy = new IndexedWord(label);
    copy.setCopyCount(count);
    copy.original = this;
    return copy;
  }

  public IndexedWord makeSoftCopy() {
    if (original != null) {
      return original.makeSoftCopy();
    } else {
      return makeSoftCopy(++numCopies);
    }
  }

  public IndexedWord getOriginal() {
    return original;
  }

  /**
   * Return the CoreLabel behind this IndexedWord
   */
  public CoreLabel backingLabel() { return label; }

  @Override
  public <VALUE> VALUE get(Class<? extends TypesafeMap.Key<VALUE>> key) {
    return label.get(key);
  }

  @Override
  public <VALUE> boolean containsKey(Class<? extends TypesafeMap.Key<VALUE>> key) {
    return label.containsKey(key);
  }

  @Override
  public <VALUE> VALUE set(Class<? extends TypesafeMap.Key<VALUE>> key, VALUE value) {
    return label.set(key, value);
  }

  @Override
  public <KEY extends TypesafeMap.Key<String>> String getString(Class<KEY> key) {
    return label.getString(key);
  }

  @Override
  public <KEY extends TypesafeMap.Key<String>> String getString(Class<KEY> key, String def) {
    return label.getString(key, def);
  }

  @Override
  public <VALUE> VALUE remove(Class<? extends Key<VALUE>> key) {
    return label.remove(key);
  }

  @Override
  public Set<Class<?>> keySet() {
    return label.keySet();
  }

  @Override
  public int size() {
    return label.size();
  }

  @Override
  public String value() {
    return label.value();
  }

  @Override
  public void setValue(String value) {
    label.setValue(value);
  }

  @Override
  public String tag() {
    return label.tag();
  }

  @Override
  public void setTag(String tag) {
    label.setTag(tag);
  }

  @Override
  public String word() {
    return label.word();
  }

  @Override
  public void setWord(String word) {
    label.setWord(word);
  }

  @Override
  public String lemma() {
    return label.lemma();
  }

  @Override
  public void setLemma(String lemma) {
    label.setLemma(lemma);
  }

  @Override
  public String ner() {
    return label.ner();
  }

  @Override
  public void setNER(String ner) {
    label.setNER(ner);
  }

  @Override
  public String docID() {
    return label.docID();
  }

  @Override
  public void setDocID(String docID) {
    label.setDocID(docID);
  }

  @Override
  public int index() {
    return label.index();
  }

  @Override
  public void setIndex(int index) {
    label.setIndex(index);
  }

  /**
   * In most cases, this is just the index of the word.
   * However, this should be the value used to sort nodes in
   * a tree.
   *
   * @see IndexedWord#pseudoPosition
   */
  public double pseudoPosition() {
    if (!Double.isNaN(pseudoPosition)) {
      return pseudoPosition;
    } else {
      return (double) index();
    }
  }

  /**
   * @see IndexedWord#pseudoPosition
   */
  public void setPseudoPosition(double position) {
    this.pseudoPosition = position;
  }

  @Override
  public int sentIndex() {
    return label.sentIndex();
  }

  @Override
  public void setSentIndex(int sentIndex) {
    label.setSentIndex(sentIndex);
  }

  @Override
  public String before() {
    return label.before();
  }

  @Override
  public void setBefore(String before) {
    label.setBefore(before);

  }

  @Override
  public String originalText() {
    return label.originalText();
  }

  @Override
  public void setOriginalText(String originalText) {
    label.setOriginalText(originalText);
  }

  @Override
  public String after() {
    return label.after();
  }

  @Override
  public void setAfter(String after) {
    label.setAfter(after);
  }

  @Override
  public int beginPosition() {
    return label.beginPosition();
  }

  @Override
  public int endPosition() {
    return label.endPosition();
  }

  @Override
  public void setBeginPosition(int beginPos) {
    label.setBeginPosition(beginPos);
  }

  @Override
  public void setEndPosition(int endPos) {
    label.setEndPosition(endPos);
  }

  public void setEmptyIndex(int empty) {
    label.setEmptyIndex(empty);
  }

  public int getEmptyIndex() {
    return label.getEmptyIndex();
  }

  public boolean hasEmptyIndex() {
    return label.hasEmptyIndex();
  }

  public int copyCount() {
    return copyCount;
  }

  public void setCopyCount(int count) {
    this.copyCount = count;
  }

  public String toPrimes() {
    return StringUtils.repeat('\'', copyCount);
  }

  public String toCopyOrEmptyIndex() {
    // TODO: if there's ever a CoNLLU or other situation that has both
    // copy & empty indices, no idea what to do with such an abomination
    if (copyCount == 0) {
      Integer empty = this.get(CoreAnnotations.EmptyIndexAnnotation.class);
      if (empty != null) {
        return this.index() + "." + empty;
      } else if (Double.isNaN(this.pseudoPosition)) {
        return String.valueOf(this.index());
      } else {
        return String.valueOf(this.pseudoPosition);
      }
    } else {
      return this.index() + "." + copyCount;
    }
  }

  @SuppressWarnings("RedundantIfStatement")
  public boolean isCopy(IndexedWord otherWord) {
    Integer myInd = get(CoreAnnotations.IndexAnnotation.class);
    Integer otherInd = otherWord.get(CoreAnnotations.IndexAnnotation.class);
    if ( ! Objects.equals(myInd, otherInd)) {
      return false;
    }

    Integer mySentInd = get(CoreAnnotations.SentenceIndexAnnotation.class);
    Integer otherSentInd = otherWord.get(CoreAnnotations.SentenceIndexAnnotation.class);
    if ( ! Objects.equals(mySentInd, otherSentInd)) {
      return false;
    }

    String myDocID = getString(CoreAnnotations.DocIDAnnotation.class);
    String otherDocID = otherWord.getString(CoreAnnotations.DocIDAnnotation.class);
    if ( ! Objects.equals(myDocID, otherDocID)) {
      return false;
    }

    // compare empty word index
    Integer myEmptyIndex = get(CoreAnnotations.EmptyIndexAnnotation.class);
    Integer otherEmptyIndex = otherWord.get(CoreAnnotations.EmptyIndexAnnotation.class);
    if ( ! Objects.equals(myEmptyIndex, otherEmptyIndex)) {
      return false;
    }

    if (copyCount() == 0 || otherWord.copyCount() != 0) {
      return false;
    }

    return true;
  }

  /**
   * This .equals is dependent only on docID, sentenceIndex, emptyIndex, and index.
   * It doesn't consider the actual word value, but assumes that it is
   * validly represented by token position.
   * All IndexedWords that lack these fields will be regarded as equal.
   */
  @SuppressWarnings("RedundantIfStatement")
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IndexedWord)) return false;

    //now compare on appropriate keys
    final IndexedWord otherWord = (IndexedWord) o;
    Integer myInd = get(CoreAnnotations.IndexAnnotation.class);
    Integer otherInd = otherWord.get(CoreAnnotations.IndexAnnotation.class);
    if ( ! Objects.equals(myInd, otherInd)) {
      return false;
    }

    Integer mySentInd = get(CoreAnnotations.SentenceIndexAnnotation.class);
    Integer otherSentInd = otherWord.get(CoreAnnotations.SentenceIndexAnnotation.class);
    if ( ! Objects.equals(mySentInd, otherSentInd)) {
      return false;
    }

    String myDocID = getString(CoreAnnotations.DocIDAnnotation.class);
    String otherDocID = otherWord.getString(CoreAnnotations.DocIDAnnotation.class);
    if ( ! Objects.equals(myDocID, otherDocID)) {
      return false;
    }

    if (copyCount() != otherWord.copyCount()) {
      return false;
    }

    // compare empty word index
    Integer myEmptyIndex = get(CoreAnnotations.EmptyIndexAnnotation.class);
    Integer otherEmptyIndex = otherWord.get(CoreAnnotations.EmptyIndexAnnotation.class);
    if ( ! Objects.equals(myEmptyIndex, otherEmptyIndex)) {
      return false;
    }

    // Compare pseudo-positions
    if ( (!Double.isNaN(this.pseudoPosition) || !Double.isNaN(otherWord.pseudoPosition)) &&
         this.pseudoPosition != otherWord.pseudoPosition) {
      return false;
    }
    return true;
  }


  private int cachedHashCode; // = 0;
  /**
   * This hashCode uses only the docID, sentenceIndex, emptyIndex, and index.
   * See compareTo for more info.
   */
  @Override
  public int hashCode() {
    if (cachedHashCode != 0) {
      return cachedHashCode;
    }
    boolean sensible = false;
    int result = 0;
    if (get(CoreAnnotations.DocIDAnnotation.class) != null) {
      result = get(CoreAnnotations.DocIDAnnotation.class).hashCode();
      sensible = true;
    }
    if (containsKey(CoreAnnotations.SentenceIndexAnnotation.class)) {
      result = 29 * result + get(CoreAnnotations.SentenceIndexAnnotation.class).hashCode();
      sensible = true;
    }
    if (containsKey(CoreAnnotations.IndexAnnotation.class)) {
      result = 29 * result + get(CoreAnnotations.IndexAnnotation.class).hashCode();
      sensible = true;
    }
    Integer emptyIndex = get(CoreAnnotations.EmptyIndexAnnotation.class);
    if (emptyIndex != null) {
      result = 29 * result + emptyIndex.hashCode();
    }
    if ( ! sensible) {
      log.info("WARNING!!!  You have hashed an IndexedWord with no docID, sentIndex or wordIndex. You will almost certainly lose");
    }
    cachedHashCode = result;
    return result;
  }

  /**
   * NOTE: For this compareTo, you <em>must</em> have a DocIDAnnotation,
   * SentenceIndexAnnotation, and IndexAnnotation for it to make sense and
   * be guaranteed to work properly. Currently, it won't error out and will
   * try to return something sensible if these are not defined, but that really
   * isn't proper usage!
   *
   * This compareTo method is based not by value elements like the word(),
   *  but on passage position. It puts NO_WORD elements first, and then orders
   *  by document, sentence, word index, and empty word index.
   *  If these do not differ, it returns equal.
   *
   *  @param w The IndexedWord to compare with
   *  @return Whether this is less than w or not in the ordering
   */
  @Override
  public int compareTo(IndexedWord w) {
    if (this.equals(IndexedWord.NO_WORD)) {
      if (w.equals(IndexedWord.NO_WORD)) {
        return 0;
      } else {
        return -1;
      }
    }
    if (w.equals(IndexedWord.NO_WORD)) {
      return 1;
    }

    // Override the default comparator if pseudo-positions are set.
    // This is needed for splicing trees together awkwardly in OpenIE.
    if (!Double.isNaN(w.pseudoPosition) || !Double.isNaN(this.pseudoPosition)) {
      double val = this.pseudoPosition() - w.pseudoPosition();
      if (val < 0) { return -1; }
      if (val > 0) { return 1; }
      else { return 0; }
    }

    // Otherwise, compare using the normal doc/sentence/token index hierarchy
    String docID = this.getString(CoreAnnotations.DocIDAnnotation.class);
    int docComp = docID.compareTo(w.getString(CoreAnnotations.DocIDAnnotation.class));
    if (docComp != 0) return docComp;

    int sentComp = Integer.compare(sentIndex(), w.sentIndex());
    if (sentComp != 0) return sentComp;

    int indexComp = Integer.compare(index(), w.index());
    if (indexComp != 0) return indexComp;

    int emptyIndexComp = Integer.compare(getEmptyIndex(), w.getEmptyIndex());
    if (emptyIndexComp != 0) return emptyIndexComp;

    return Integer.compare(copyCount(), w.copyCount());
  }

  /**
   * Returns the value-tag of this label. Doesn't represent copy nodes specially.
   */
  @Override
  public String toString() {
    return toString(CoreLabel.OutputFormat.VALUE_TAG);
  }

  /** Allows choices of how to format Label.
   *
   * @param format An instance of the OutputFormat enum (the same as for a CoreLabel)
   * @return A String representation of the label, including marking copy nodes with primes (')
   */
  public String toString(CoreLabel.OutputFormat format) {
    return label.toString(format) + toPrimes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setFromString(String labelStr) {
    throw new UnsupportedOperationException("Cannot set from string");
  }


  public static LabelFactory factory() {
    return new LabelFactory() {

      @Override
      public Label newLabel(String labelStr) {
        CoreLabel coreLabel = new CoreLabel();
        coreLabel.setValue(labelStr);
        return new IndexedWord(coreLabel);
      }

      @Override
      public Label newLabel(String labelStr, int options) {
        return newLabel(labelStr);
      }

      @Override
      public Label newLabel(Label oldLabel) {
        return new IndexedWord(oldLabel);
      }

      @Override
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
    return IndexedWord.factory();
  }

}
