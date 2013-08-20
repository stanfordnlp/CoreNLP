package edu.stanford.nlp.ling;

import edu.stanford.nlp.util.FilePathProcessor;
import edu.stanford.nlp.util.FileProcessor;
import edu.stanford.nlp.util.Timing;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * A <code>MemorySentencebank</code> object stores a corpus of examples with
 * given sentence structures in memory (as a Collection)
 *
 * @author Christopher Manning
 * @version Jan 2000
 */
public final class MemorySentencebank<T extends HasWord> extends Sentencebank<ArrayList<T>, T> implements FileProcessor {

  private static final boolean PRINT_FILENAMES = false;

  /**
   * The collection of parse sentences
   */
  private ArrayList<ArrayList<T>> parseSentences;


  /**
   * Create a new sentence bank.
   */
  public MemorySentencebank() {
    this(new SimpleSentenceReaderFactory<T>());
  }


  /**
   * Create a new sentence bank.
   *
   * @param srf the factory class to be called to create a new
   *            <code>SentenceReader</code>
   */
  public MemorySentencebank(SentenceReaderFactory<T> srf) {
    super(srf);
    parseSentences = new ArrayList<ArrayList<T>>();
  }


  /**
   * Create a new Sentencebank.
   *
   * @param initialCapacity The initial size of the underlying Collection,
   *                        (if a Collection-based storage mechanism is being provided)
   */
  public MemorySentencebank(int initialCapacity) {
    this(initialCapacity, new SimpleSentenceReaderFactory<T>());
  }


  /**
   * Create a new sentence bank.
   *
   * @param initialCapacity The initial size of the underlying Collection
   * @param srf             the factory class to be called to create a new
   *                        <code>SentenceReader</code>
   */
  public MemorySentencebank(int initialCapacity, SentenceReaderFactory<T> srf) {
    super(initialCapacity, srf);
    parseSentences = new ArrayList<ArrayList<T>>(initialCapacity);
  }


  /**
   * Empty a <code>Sentencebank</code>.
   */
  @Override
  public void clear() {
    parseSentences.clear();
  }


  /**
   * Load sentences from given directory.
   *
   * @param path file or directory to load from
   * @param filt a FilenameFilter of files to load
   */
  @Override
  public void loadPath(File path, FileFilter filt) {
    FilePathProcessor.processPath(path, filt, this);
  }


  /**
   * Load a collection of parse sentences from the file of given name.
   * Each sentence may optionally be encased in parens to allow for Penn
   * Sentencebank style sentences.
   * This methods implements the <code>FileProcessor</code> interface.
   *
   * @param file file to load a sentence from
   */
  public void processFile(File file) {
    SentenceReader<T> sr = null;

    try {
      // maybe print file name to stdout to get some feedback
      if (PRINT_FILENAMES) {
        System.err.println(file);
      }
      // could throw an IO exception if can't open for reading
      sr = sentenceReaderFactory().newSentenceReader(new BufferedReader(new FileReader(file)));

      for (ArrayList<T> pt; (pt = sr.readSentence()) != null; ) {
        parseSentences.add(pt);
      }
    } catch (IOException e) {
      System.err.println("loadSentence IO Exception" + e);
    } finally {
      try {
        if (sr != null) {
          sr.close();  // important: closes file even if error!
        }
      } catch (IOException e) {
        // do nothing
      }
    }
  }


  /**
   * Get a sentence by index from the Sentencebank.
   * @param i The index to retrieve
   * @return The sentence
   */
  public ArrayList<T> get(int i) {
    return parseSentences.get(i);
  }


  /**
   * Apply the SentenceVisitor sp to all sentences in the Sentencebank
   */
  @Override
  public void apply(SentenceVisitor<T> sp) {
    for (int i = 0, sz = parseSentences.size(); i < sz; i++) {
      sp.visitSentence(parseSentences.get(i));
    }
    // or could do as Iterator but slower
    // Iterator iter = parseSentences.iterator();
    // while (iter.hasNext()) {
    //    sp.processSentence((Sentence) iter.next());
    // }
  }


  /**
   * Return an Iterator over Sentences in the Sentencebank
   */
  @Override
  public Iterator<ArrayList<T>> iterator() {
    return parseSentences.iterator();
  }


  /**
   * Returns the size of the Sentencebank.
   * Provides a more efficient implementation than the one for a
   * generic <code>Sentencebank</code>
   *
   * @return the number of trees in the Sentencebank
   */
  @Override
  public int size() {
    return parseSentences.size();
  }


  /**
   * Loads sentencebank grammar from first argument and print it.
   *
   * @param args array of command-line arguments
   */
  public static void main(String[] args) {
    Timing.startTime();
    Sentencebank<ArrayList<HasWord>, HasWord> sentencebank = new MemorySentencebank<HasWord>(new SentenceReaderFactory<HasWord>() {
      public SentenceReader<HasWord> newSentenceReader(Reader in) {
        return new SentenceReader<HasWord>(in, new TaggedWordFactory(), new PennSentenceNormalizer<HasWord>(), new PennTagbankStreamTokenizer(in));
      }
    });
    sentencebank.loadPath(args[0]);
    Timing.endTime();
    System.out.println(sentencebank);

    System.out.println();
    System.out.println("This Sentencebank contains " + sentencebank.size() + " sentences.");

  }

}
