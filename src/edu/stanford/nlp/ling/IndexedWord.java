package edu.stanford.nlp.ling;

import edu.stanford.nlp.ling.CoreAnnotations.DocIDAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * This class is mainly for use with RTE in terms of the methods it provides,
 * but on a more general level, it provides a {@link CoreLabel} that uses its
 * DocIDAnnotation, SentenceIndexAnnotation, and IndexAnnotation to implement
 * Comparable/compareTo, hashCode, and equals.  This means no other annotations,
 * including the identity of the word, are taken into account when using these
 * methods.
 *
 * @author rafferty
 *
 */
public class IndexedWord extends CoreLabel implements Comparable<IndexedWord> {

  private static final long serialVersionUID = 3739633991145239829L;

  /**
   * The identifier that points to no word.
   */
  public static final IndexedWord NO_WORD = new IndexedWord(null, -1, -1);

  /**
   * Various printing options for toString
   */
  public static final String WORD_FORMAT = "WORD_FORMAT";
  public static final String WORD_TAG_FORMAT = "WORD_TAG_FORMAT";
  public static final String WORD_TAG_INDEX_FORMAT = "WORD_TAG_INDEX_FORMAT";
  public static final String VALUE_FORMAT = "VALUE_FORMAT";
  public static final String COMPLETE_FORMAT = "COMPLETE_FORMAT";

  private static String printFormat = WORD_TAG_FORMAT;

  /**
   * Default constructor; uses {@link CoreLabel} default constructor
   */
  public IndexedWord() {
    super();
  }


  /**
   * Copy Constructor - relies on {@link CoreLabel} copy constructor
   * It will set the value, and if the word is not set otherwise, set
   * the word to the value.
   *
   * @param w A Label to initialize this IndexedWord from
   */
  public IndexedWord(Label w) {
    super(w);
    if (this.word() == null)
      this.setWord(this.value());
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
    this((CoreMap) w);
  }

  /**
   * Copy Constructor - relies on {@link CoreLabel} copy constructor
   * @param w A Label to initialize this IndexedWord from
   */
  public IndexedWord(CoreMap w) {
    super(w);
    if (this.word() == null)
      this.setWord(this.value());
  }

  /**
   * Copy Constructor - relies on {@link CoreLabel} copy constructor
   * It will set the value, and if the word is not set otherwise, set
   * the word to the value.
   *
   * @param w A Label to initialize this IndexedWord from
   */
  public IndexedWord(CyclicCoreLabel w) {
    super(w);
    if (this.word() == null)
      this.setWord(this.value());
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
    super();
    this.set(DocIDAnnotation.class, docID);
    this.set(SentenceIndexAnnotation.class, sentenceIndex);
    this.set(IndexAnnotation.class, index);
  }


  /**
   * Copies the given label and then sets the docID, sentenceIndex,
   * and Index; if these differ from those in label, the parameters
   * will be used (not the label values).
   *
   * @param docID The document ID (arbitrary string)
   * @param sentenceIndex The sentence number in the document (normally 0-based)
   * @param index The index of the word in the sentence (normally 0-based)
   * @param label The CoreLabel to initialize all other fields from.
   */
  public IndexedWord(String docID, int sentenceIndex, int index, CoreLabel label) {
    this(label);
    this.set(DocIDAnnotation.class, docID);
    this.set(SentenceIndexAnnotation.class, sentenceIndex);
    this.set(IndexAnnotation.class, index);
  }


  /**
   * This .equals is dependent only on docID, sentenceIndex, and index.
   * It doesn't consider the actual word value, but assumes that it is
   * validly represented by token position.
   * All IndexedWords that lack these fields will be regarded as equal.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IndexedWord)) return false;

    //now compare on appropriate keys
    final IndexedWord otherWord = (IndexedWord) o;
    String myDocID = getString(DocIDAnnotation.class);
    String otherDocID = otherWord.getString(DocIDAnnotation.class);
    if (myDocID == null) {
      if (otherDocID != null)
      return false;
    } else if ( ! myDocID.equals(otherDocID)) {
      return false;
    }
    Integer mySentInd = get(SentenceIndexAnnotation.class);
    Integer otherSentInd = otherWord.get(SentenceIndexAnnotation.class);
    if (mySentInd == null) {
      if (otherSentInd != null)
      return false;
    } else if ( ! mySentInd.equals(otherSentInd)) {
      return false;
    }
    Integer myInd = get(IndexAnnotation.class);
    Integer otherInd = otherWord.get(IndexAnnotation.class);
    if (myInd == null) {
      if (otherInd != null)
      return false;
    } else if ( ! myInd.equals(otherInd)) {
      return false;
    }
    return true;
  }


  /**
   * This hashcode uses only the docID, sentenceIndex, and index
   * See compareTo for more info
   */
  @Override
  public int hashCode() {
    boolean sensible = false;
    int result = 0;
    if (get(DocIDAnnotation.class) != null) {
      result = get(DocIDAnnotation.class).hashCode();
      sensible = true;
    }
    if (has(SentenceIndexAnnotation.class)) {
      result = 29 * result + get(SentenceIndexAnnotation.class);
      sensible = true;
    }
    if (has(IndexAnnotation.class)) {
      result = 29 * result + get(IndexAnnotation.class);
      sensible = true;
    }
    if ( ! sensible) {
      System.err.println("WARNING!!!  You have hashed an IndexedWord with no docID, sentIndex or wordIndex. You will almost certainly lose");
    }
    return result;
  }

  /**
   * NOTE: This compareTo is based on and made to be compatible with the one
   * from IndexedFeatureLabel.  You <em>must</em> have a DocIDAnnotation,
   * SentenceIndexAnnotation, and IndexAnnotation for this to make sense and
   * be guaranteed to work properly. Currently, it won't error out and will
   * try to return something sensible if these are not defined, but that really
   * isn't proper usage!
   *
   * This compareTo method is based not by value elements like the word(),
   *  but on passage position. It puts NO_WORD elements first, and then orders
   *  by document, sentence, and word index.  If these do not differ, it
   *  returns equal.
   *
   *  @param w The IndexedWord to compare with
   *  @return Whether this is less than w or not in the ordering
   */
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

    String docID = this.getString(DocIDAnnotation.class);
    int docComp = docID.compareTo(w.getString(DocIDAnnotation.class));
    if (docComp != 0) return docComp;

    int sentComp = sentIndex() - w.sentIndex();
    if (sentComp != 0) return sentComp;

    return index() - w.index();
  }

  /**
   * Computes the toString based on whatever the printFormat is
   * currently set as.
   */
  @Override
  public String toString() {
    return toString(printFormat);
  }

  public static void setPrintFormat(String printFormat) {
    IndexedWord.printFormat = printFormat;
  }

  /**
   * Prints the toString in the form of format.
   *
   * @param format One of the constants defined for this class. (You must use
   *     one of these constants, because the Strings are compared by ==.)
   * @return A printed representation
   */
  public String toString(String format) {

    if (this.equals(NO_WORD)) return "NO_WORD";
    StringBuilder result = new StringBuilder();

    // word
    if (format == WORD_FORMAT ||
        format == WORD_TAG_FORMAT ||
        format == WORD_TAG_INDEX_FORMAT) {
      result.append(word());

      // tag
      if (format == WORD_TAG_FORMAT ||
          format == WORD_TAG_INDEX_FORMAT) {
        String tag = tag();
        if (tag != null && tag.length() != 0) {
          result.append('-').append(tag);
        }

        // index
        if (format == WORD_TAG_INDEX_FORMAT) {
          result.append('-').append(sentIndex()).append(':').append(index());
        }
      }

      // value format
    } else if (format == VALUE_FORMAT) {
      result.append(value());
      if (index() >= 0) {
        result.append(':').append(index());
      }

    } else {
      return super.toString();
    }

    return result.toString();
  }

  public static LabelFactory factory() {
    return new LabelFactory() {

      public Label newLabel(String labelStr) {
        IndexedWord label = new IndexedWord();
        label.setValue(labelStr);
        return label;
      }

      public Label newLabel(String labelStr, int options) {
        return newLabel(labelStr);
      }

      public Label newLabel(Label oldLabel) {
        return new IndexedWord(oldLabel);
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
    return IndexedWord.factory();
  }
}
