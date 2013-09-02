package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.Feature;
import edu.stanford.nlp.process.NumAndCapFeature;
import edu.stanford.nlp.process.DocumentProcessor;
import edu.stanford.nlp.stats.Counters;

import java.util.Iterator;
import java.util.Set;

/**
 * Collapses all words that appear less than N times in the given document to a
 * UNK word. A corpus can be provided at construction to seed the vocabulary,
 * otherwise the document to be processed is used. In either case, every word that
 * occurs at least a minimum number of times is preserved, and all the other words
 * are mapped down to a single UNKNOWN token. This is a simple but effective way
 * of dealing with large and variable vocabularies, because all rare words are pooled
 * together for more reliable statistics. If <tt>featuralDecomp</tt> is used, instead
 * of just using a single UNKOWN token, a basic featural decomposition is performed
 * (using {@link edu.stanford.nlp.process.Feature}) so the word is annotated for
 * capitalization and number use. This creates a small set of equivalence classes
 * but allows a more fine-grained set of probabilities to be learned for unknown words.
 * <p/>
 * NOTE: The current implementation is very tied to HMM stuff  (Corpus, TypedTaggedWord, etc).
 * because the more general infrastructure for maintaining a collection of documents,
 * getting the vocab, etc. doesn't really exist. Ideally Corpus would be replaced
 * by some sort of "DocumentCollection" that would support getVocab() etc.
 *
 * @author Joseph Smarr
 */
public class UnknownWordCollapser implements DocumentProcessor {
  /**
   * Default max frequency for a word to be considered unknown: {@value}.
   */
  public static final int defaultMaxUnknownFrequency = 1;

  /**
   * Text for default unknown word that replaces all rare words in source document: {@value}.
   */
  public static final String defaultUnknownWord = "$UNK$";

  /**
   * Default Feature for featural decomp (NumAndCapFeature).
   */
  public static final Feature defaultFeature = new NumAndCapFeature();

  private int maxUnknownFrequency;
  private String unknownWord;
  private Set corpusVocab; // set of unique words in the source corpus above the cutoff
  private boolean featuralDecomp; // whether to use extra feature info for UNK words
  private Feature f;

  /**
   * Constructs an UnknownWordCollapser using a predefined set of common words.
   *
   * @param corpusVocab         set of Strings for words that should be left intact
   * @param maxUnknownFrequency max number of times a word can occur and still be considered unknown
   * @param unknownWord         the text of the word that replaces all uncommon words
   * @param featuralDecomp      whether to annotate unkwown words with a featural decomposition
   */
  public UnknownWordCollapser(Set corpusVocab, int maxUnknownFrequency, String unknownWord, boolean featuralDecomp, Feature f) {
    this.corpusVocab = corpusVocab;
    this.maxUnknownFrequency = maxUnknownFrequency;
    this.unknownWord = unknownWord;
    this.featuralDecomp = featuralDecomp;
    this.f = f;
  }

  /**
   * Constructs a new UnknownWordCollapser with the vocabulary taken from the given corpus's commonly occurring words.
   *
   * @param sourceCorpus        text collection to build vocabulary of common words from
   * @param maxUnknownFrequency max number of times a word can occur and still be considered unknown
   * @param unknownWord         the text of the word that replaces all uncommon words
   * @param featuralDecomp      whether to annotate unkwown words with a featural decomposition
   */
  public UnknownWordCollapser(Corpus sourceCorpus, int maxUnknownFrequency, String unknownWord, boolean featuralDecomp, Feature f) {
    this(sourceCorpus == null ? null : Counters.keysAbove(sourceCorpus.getVocab(), (maxUnknownFrequency + 1)), maxUnknownFrequency, unknownWord, featuralDecomp, f);
  }

  // constructors with no featural decomp

  public UnknownWordCollapser(int maxUnknownFrequency, String unknownWord, Feature f) {
    this((Set) null, maxUnknownFrequency, unknownWord, false, f);
  }

  public UnknownWordCollapser(Corpus sourceCorpus, String unknownWord, Feature f) {
    this(sourceCorpus, defaultMaxUnknownFrequency, unknownWord, false, f);
  }

  public UnknownWordCollapser(Corpus sourceCorpus, int maxUnknownFrequency, Feature f) {
    this(sourceCorpus, maxUnknownFrequency, defaultUnknownWord, false, f);
  }

  public UnknownWordCollapser(Set corpusVocab, String unknownWord, Feature f) {
    this(corpusVocab, defaultMaxUnknownFrequency, unknownWord, false, f);
  }

  public UnknownWordCollapser(Set corpusVocab, int maxUnknownFrequency, Feature f) {
    this(corpusVocab, maxUnknownFrequency, defaultUnknownWord, false, f);
  }

  public UnknownWordCollapser(Corpus sourceCorpus, Feature f) {
    this(sourceCorpus, defaultMaxUnknownFrequency, defaultUnknownWord, false, f);
  }

  public UnknownWordCollapser(Set corpusVocab, Feature f) {
    this(corpusVocab, defaultMaxUnknownFrequency, defaultUnknownWord, false, f);
  }

  public UnknownWordCollapser(int maxUnknownFrequency, Feature f) {
    this((Set) null, maxUnknownFrequency, defaultUnknownWord, false, f);
  }

  public UnknownWordCollapser(String unknownWord, Feature f) {
    this((Set) null, defaultMaxUnknownFrequency, unknownWord, false, f);
  }

  public UnknownWordCollapser(Feature f) {
    this((Set) null, defaultMaxUnknownFrequency, defaultUnknownWord, false, f);
  }

  public UnknownWordCollapser() {
    this((Set) null, defaultMaxUnknownFrequency, defaultUnknownWord, false, defaultFeature);
  }

  // constructors with featural decomp

  public UnknownWordCollapser(int maxUnknownFrequency, String unknownWord, boolean featuralDecomp, Feature f) {
    this((Set) null, maxUnknownFrequency, unknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(Corpus sourceCorpus, String unkownWord, boolean featuralDecomp, Feature f) {
    this(sourceCorpus, defaultMaxUnknownFrequency, unkownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(Corpus sourceCorpus, int maxUnknownFrequency, boolean featuralDecomp, Feature f) {
    this(sourceCorpus, maxUnknownFrequency, defaultUnknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(Set corpusVocab, String unkownWord, boolean featuralDecomp, Feature f) {
    this(corpusVocab, defaultMaxUnknownFrequency, unkownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(Set corpusVocab, int maxUnknownFrequency, boolean featuralDecomp, Feature f) {
    this(corpusVocab, maxUnknownFrequency, defaultUnknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(Corpus sourceCorpus, boolean featuralDecomp, Feature f) {
    this(sourceCorpus, defaultMaxUnknownFrequency, defaultUnknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(Set corpusVocab, boolean featuralDecomp, Feature f) {
    this(corpusVocab, defaultMaxUnknownFrequency, defaultUnknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(int maxUnknownFrequency, boolean featuralDecomp, Feature f) {
    this((Set) null, maxUnknownFrequency, defaultUnknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(String unknownWord, boolean featuralDecomp, Feature f) {
    this((Set) null, defaultMaxUnknownFrequency, unknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(boolean featuralDecomp, Feature f) {
    this((Set) null, defaultMaxUnknownFrequency, defaultUnknownWord, featuralDecomp, f);
  }

  public UnknownWordCollapser(boolean featuralDecomp) {
    this((Set) null, defaultMaxUnknownFrequency, defaultUnknownWord, featuralDecomp, defaultFeature);
  }

  /**
   * Returns a copy of <tt>in</tt> where all rare words have been replaced by a single
   * UNKNOWN word. If a (non-null) source corpus was passed in during construction, the vocab used
   * is the set of words in that corpus that occurred more than the given number of times.
   * Otherwise the vocab used is the set of words in <tt>in</tt> that have occurred more than
   * the given number of times.
   * <p/>
   * NOTE: The current implementation assumes the Words in the input document are
   * TypedTaggedWords, because there's no good way to preserve the extra word
   * info. This needs to be changed if we want this class to be more general.
   */
  public Document processDocument(Document in) {

    Set vocab = corpusVocab;
    if (vocab == null) {
      // makes a corpus out of the in document and gets its vocab
      Corpus c = new Corpus(new String[]{"(Background)"});
      TypedTaggedDocument ttd = new TypedTaggedDocument();
      Iterator iter = in.iterator();
      while (iter.hasNext()) {
        ttd.add(new TypedTaggedWord(((Word) iter.next()).word(), 0));
      }
      c.add(ttd);
      vocab = Counters.keysAbove(c.getVocab(), (getMaxUnknownFrequency() + 1));
    }

    // goes thru the doc and replaces out-of-vocab words with unk
    Document out = in.blankDocument();
    Iterator iter = in.iterator();
    while (iter.hasNext()) {
      TypedTaggedWord ttw = (TypedTaggedWord) iter.next();
      if (vocab.contains(ttw.word()) || ttw.word().startsWith(unknownWord)) {
        out.add(ttw); // in vocab or already UNKed
      } else {
        String unkText = unknownWord;
        if (featuralDecomp) {
          unkText += f.getValue(ttw.word());
        }
        out.add(new TypedTaggedWord(unkText, ttw.type()));
      }
    }
    return (out);
  }

  /**
   * Processes an entire Corpus at once. Uses the corpus given at construction for
   * the vocab, or the input corpus if the former is null.
   */
  public Corpus processCorpus(Corpus in) {
    boolean localVocab = false;
    if (corpusVocab == null) {
      // temporarily uses the vocab from this corpus for processing
      corpusVocab = Counters.keysAbove(in.getVocab(), (getMaxUnknownFrequency() + 1));
      localVocab = true;
    }
    Corpus out = new Corpus(in.getTargetFields());
    for (int i = 0; i < in.size(); i++) {
      out.add(processDocument((Document) in.get(i)));
    }
    if (localVocab) {
      corpusVocab = null; // clears vocab if it's from this corpus only
    }
    return (out);
  }

  /**
   * Returns the maximum number of times a word can occur and still be considered unknown.
   */
  public int getMaxUnknownFrequency() {
    return (maxUnknownFrequency);
  }

  /**
   * Returns the text of the unknown word that replaces all rare words during processing.
   */
  public String getUnknownWord() {
    return (unknownWord);
  }

  /**
   * For internal debugging purposes only.
   * Processes a corpus and prints the before/after for each document (without featural decomp)
   * <pre>Usage: java edu.stanford.nlp.ie.hmm.UnknownWordCollapser corpusfilename [maxUnknownFrequency] [unknownWord]</pre>
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: java edu.stanford.nlp.ie.hmm.UnknownWordCollapser corpusfilename [maxUnknownFrequency] [unknownWord]");
      System.exit(1);
    }

    Corpus c = new Corpus(args[0], new String[]{"(Background)", "purchaser"});
    UnknownWordCollapser uwc;
    if (args.length == 1) {
      uwc = new UnknownWordCollapser(c, new NumAndCapFeature());
    } else if (args.length == 2) {
      uwc = new UnknownWordCollapser(c, Integer.parseInt(args[1]), new NumAndCapFeature());
    } else {
      uwc = new UnknownWordCollapser(c, Integer.parseInt(args[1]), args[2], false, new NumAndCapFeature());
    }
    for (int i = 0; i < c.size(); i++) {
      // processes each document and prints before/after
      Document in = (Document) c.get(i);
      System.out.println("BEFORE: " + in);
      System.out.println("AFTER: " + uwc.processDocument(in));
      System.out.println();
    }
  }
}
