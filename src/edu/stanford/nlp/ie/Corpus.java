package edu.stanford.nlp.ie;

import edu.stanford.nlp.ie.hmm.FreitagIECollectionIterator;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.process.WordExtractor;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Iterables;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Class to handle a corpus of information extraction data.
 * A Corpus is a set of documents, each document being a list of words.
 * Corpus objects also know which words are contained in which states according
 * to the partial labeling of the source data.
 * A representation of the entire corpus is kept in memory.
 * <p/>
 * Within a document, one basically has natural language text, but any number
 * of states can be partially labeled by enclosing sequences of words in
 * an XML-style tag, such as <code>&lt;purchaser&gt;First Wisconsin
 * Corp&lt;/purchaser&gt; said it plans to acquire &lt;acquired&gt;Shelard
 * Bancshares Inc&lt;/acquired&gt;.</code>.  In a particular use, some
 * tags will be treated as target tags and the tagging of those words will
 * be returned by the tokenizer, while other tags will be ignored.
 * <p/>
 * Corpus also contains methods for pulling out just the target sequences or
 * replacing them with generic target markers in order to focus on context.
 *
 * @author Jim McFadden
 * @author Huy Nguyen (htnguyen@stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see edu.stanford.nlp.annotation.TaggedStreamTokenizer
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels in the Corpus
 * @param <F> The type of the features in the Corpus
 */
public class Corpus<L, F extends HasWord> extends BasicDataCollection<L, F> {

  /**
   * 
   */
  private static final long serialVersionUID = 7837079430197487945L;
  /**
   * Target fields by word type (0 is (Background)).
   */
  private String[] targetFields;

  /**
   * Constructs an empty Corpus with no target fields.
   */
  public Corpus() {
    this(new String[0]);
  }

  /**
   * Make a Corpus from a file.
   * Convenience constructor for when there is only one target field.
   *
   * @param fileName    file to read from
   * @param targetField field that is being extracted
   */
  public Corpus(String fileName, String targetField) {
    this(fileName, new String[]{targetField});
  }


  /**
   * @param fileName file to read from
   * @param targets  fields that is being extracted
   */
  public Corpus(String fileName, String[] targets) {
    this(fileName, targets, true);
  }

  /**
   * @param fileName    file to read from
   * @param targets     fields that is being extracted
   * @param discardHtml whether to discard HTML from the input source
   */
  public Corpus(String fileName, String[] targets, boolean discardHtml) {
    this(targets);
    Iterables.addAll(new ObjectBank(new ReaderIteratorFactory(new File(fileName)), FreitagIECollectionIterator.factory(targets, discardHtml)).iterator(), this);
  }

  /**
   * Constructs a new empty Corpus with the given target fields.
   */
  public Corpus(String[] targets) {
    setTargetFields(targets);
  }

  /**
   * Constructs a new empty Corpus with the given target field.
   */
  public Corpus(String targetField) {
    this(new String[]{targetField});
  }

  /**
   * Constructs a new Corpus that is an exact copy of the given old Corpus.
   */
  public Corpus(Corpus<L, F> oldCorpus) {
    this(oldCorpus.getTargetFields());
    for (Datum<L, F> datum : oldCorpus) {
      add(datum);
    }
  }

  /**
   * Requires that only TypedTaggedDocuments are added.
   */
  @Override
  public boolean add(Datum<L, F> d) {
    if (!(d instanceof TypedTaggedDocument)) {
      System.err.println("Corpus datum must be of type ie.hmm.TypedTaggedDocument");
      return false;
    }
    return (super.add(d));
  }

  /*
  public boolean add(Object o) {
    if (!(o instanceof Datum)) {
      System.err.println("Corpus data must be derived from old.Datum");
      return false;
    } else {
      add((Datum) o);
      return true;
    }
  }
  */

  // public interface

  /**
   * Computes and returns a Map from all words (String) in all documents in
   * this corpus to their observed frequencies (double).
   *
   * @return A Counter from all words (String) observed to their frequencies (double).
   */
  public ClassicCounter<String> getVocab() {
    return computeFeatureCounts(new WordExtractor<F>());
  }

  /**
   * @return Number of words in corpus
   */
  public int wordCount() {
    return computeFeatureTokenCount();
  }

  /**
   * Computes the vocab and returns its size.
   *
   * @see #getVocab
   */
  public int vocabSize() {
    return getVocab().size();
  }

  /**
   * Returns the list of target fields to extract. The 0th element is always
   * "(Background)" even if this Corpus was constructed without explicitly
   * mentioning the background state.
   */
  public String[] getTargetFields() {
    return targetFields;
  }

  /**
   * Get field we are extracting.
   */
  public String getTargetField() {
    if (targetFields.length > 1) {
      System.err.println("Warning: there are multiple target fields!");
    }
    return targetFields[0];
  }

  /**
   * Sets the given target fields for this Corpus. If targetFields[0] is not
   * "(Background)" it is added as such internally.
   */
  public void setTargetFields(String[] targetFields) {
    this.targetFields = TypedTaggedDocument.getTargetFieldsPlusBG(targetFields);
  }

  /**
   * Replaces this corpus with a new corpus where each document contains
   * a single "answer" or a series of words representing a single target
   * field.
   */
  public void retainOnlyTarget(String tagName) {
    int type = -1;
    for (int i = 0; i < targetFields.length; i++) {
      if (tagName.equals(targetFields[i])) {
        type = i;
        break;
      }
    }
    if (type < 0) {
      System.err.println("Error: can't isolate " + tagName + ", tag not found in target list");
      return;
    }

    //TODO: "this" isn't a list of Documents.......  or at least, I don't think it should be
    List<Document> oldDocs = new ArrayList(this); // cache current document set
    clear(); // kill all docs

    for (Document doc: oldDocs) {
      List<TypedTaggedWord> newWords = null; // have not started an answer
      for (int j = 0, docSize = doc.size(); j < docSize; j++) {
        TypedTaggedWord ttw = (TypedTaggedWord) doc.get(j);
        if (ttw.type() == type) {
          if (newWords == null) {
            newWords = new ArrayList<TypedTaggedWord>();
          }
          newWords.add(new TypedTaggedWord(ttw.word(), 1));
        } else if (newWords != null) {
          // we are done with a current answer
          add(new TypedTaggedDocument().init(newWords));
          newWords = null;
        }
      }
      // pick up targets at end of document
      if (newWords != null) {
        add(new TypedTaggedDocument().init(newWords));
      }
    }
    // reset targetFields
    targetFields = new String[2];
    targetFields[0] = "(Background)";
    targetFields[1] = tagName;
  }


  /**
   * Isolates context of all targetFields.  That is, this goes through the
   * documents in the corpus, and for each document, when a sequence of one
   * or more words of a target state are found, they are replaced with a
   * single special target state observation token.
   */
  public void isolateContext() {
    int currType = -1;

    List oldDocs = new ArrayList(this); // cache current document set
    clear(); // kill all docs

    for (int i = 0, numDocs = oldDocs.size(); i < numDocs; i++) {
      Document doc = (Document) oldDocs.get(i);
      List<TypedTaggedWord> newWords = new ArrayList<TypedTaggedWord>();

      for (int j = 0, docSize = doc.size(); j < docSize; j++) {
        TypedTaggedWord ttw = (TypedTaggedWord) doc.get(j);
        if (ttw.type() > 0) {
          if (currType != ttw.type()) {
            // replace with special token to denote entry into target area
            newWords.add(new TypedTaggedWord(targetFields[ttw.type()] + "STATE", ttw.type()));
          }
        } else {
          newWords.add(ttw); // otherwise just copy over
        }
        currType = ttw.type(); // remember last type
      }
      add(new TypedTaggedDocument().init(newWords));
    }
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer("[");
    for (int i = 0; i < targetFields.length; i++) {
      sb.append(targetFields[i]);
      if (i != targetFields.length - 1) {
        sb.append(", ");
      }
    }
    sb.append("]");
    return "Corpus{" + wordCount() + " tokens; " + vocabSize() + " types; " + size() + " documents; targets = " + sb + "}";
  }

  /**
   * Returns a new Corpus with the same target fields as this Corpus.
   */
  @Override
  public DataCollection<L, F> blankDataCollection() {
    Corpus<L, F> c = (Corpus<L, F>) super.blankDataCollection();
    c.clear(); // kill all docs
    c.setTargetFields(getTargetFields());
    return (c);
  }


  /**
   * Provides a test of what gets put into a corpus.
   * This prints out the contents of the corpus, showing the tokenization.
   * The type assigned to each token is printed after each word as a
   * number enclosed in brackets.
   * <br>
   * Usage: <code> java edu.stanford.nlp.ie.Corpus
   * corpusFile targetFields*</code>
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("usage: java edu.stanford.nlp.ie.Corpus " + "corpusFile targetFields*");
      return;
    }
    Corpus corp;
    // just to test two different constructors ....
    if (args.length == 2) {
      corp = new Corpus(args[0], args[1]);
    } else {
      String[] targs = new String[args.length - 1];
      for (int j = 0; j < args.length - 1; j++) {
        targs[j] = args[j + 1];
      }
      corp = new Corpus(args[0], targs);
    }
    System.out.println("The corpus contains " + corp.size() + " documents, " + corp.wordCount() + " word tokens, and " + corp.vocabSize() + " word types.");

    int i = 0;
    for (Iterator it = corp.iterator(); it.hasNext();) {
      Document doc = (Document) it.next();
      System.out.println("---- Document " + i + "----");
      i++;
      for (Iterator iter = doc.iterator(); iter.hasNext();) {
        TypedTaggedWord w = (TypedTaggedWord) iter.next();
        System.out.print(w);
        if (iter.hasNext()) {
          System.out.print(" ");
        } else {
          System.out.println();
        }
      }
    }
  }
}
