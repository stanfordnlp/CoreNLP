package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.Americanize;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;
import java.util.regex.Pattern;


/**
 * This class is used to wrap the ObjectBank used by the sequence
 * models and is where any sort of general processing, like the IOB mapping
 * stuff and wordshape stuff, should go.
 * It checks the SeqClassifierFlags to decide what to do.
 *
 * TODO: We should rearchitect this so that the FeatureFactory-specific
 * stuff is done by a callback to the relevant FeatureFactory.
 *
 * @author Jenny Finkel
 */
public class ObjectBankWrapper<IN extends CoreMap> extends ObjectBank<List<IN>> {

  private static final long serialVersionUID = -3838331732026362075L;

  private final SeqClassifierFlags flags;
  private final ObjectBank<List<IN>> wrapped;
  private final Set<String> knownLCWords;


  public ObjectBankWrapper(SeqClassifierFlags flags, ObjectBank<List<IN>> wrapped, Set<String> knownLCWords) {
    super(null, null);
    this.flags = flags;
    this.wrapped = wrapped;
    this.knownLCWords = knownLCWords;
  }


  @Override
  public Iterator<List<IN>> iterator() {
    return new WrappedIterator(wrapped.iterator());
  }


  private class WrappedIterator extends AbstractIterator<List<IN>> {

    private final Iterator<List<IN>> wrappedIter;
    private Iterator<List<IN>> spilloverIter;

    public WrappedIterator(Iterator<List<IN>> wrappedIter) {
      this.wrappedIter = wrappedIter;
    }

    private void primeNextHelper() {
      while ((spilloverIter == null || ! spilloverIter.hasNext()) &&
             wrappedIter.hasNext()) {
        List<IN> doc = wrappedIter.next();
        List<List<IN>> docs = new ArrayList<>();
        docs.add(doc);
        fixDocLengths(docs);
        spilloverIter = docs.iterator();
      }
    }

    @Override
    public boolean hasNext() {
      primeNextHelper();
      return wrappedIter.hasNext() || (spilloverIter != null && spilloverIter.hasNext());
    }

    @Override
    public List<IN> next() {
      primeNextHelper();
      return processDocument(spilloverIter.next());
    }

  } // end class WrappedIterator


  public List<IN> processDocument(List<IN> doc) {
    if (flags.mergeTags) { mergeTags(doc); }
    if (flags.iobTags) { iobTags(doc); }
    doBasicStuff(doc);

    return doc;
  }

  private String intern(String s) {
    if (flags.intern) {
      return s.intern();
    } else {
      return s;
    }
  }


  private static final Pattern monthDayPattern = Pattern.compile("Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|January|February|March|April|May|June|July|August|September|October|November|December", Pattern.CASE_INSENSITIVE);

  private String fix(String word) {
    if (flags.normalizeTerms || flags.normalizeTimex) {
      // Same case for days/months: map to lowercase
      if (monthDayPattern.matcher(word).matches()) {
        return word.toLowerCase();
      }
    }
    if (flags.normalizeTerms) {
      return Americanize.americanize(word, false);
    }
    return word;
  }


  private void doBasicStuff(List<IN> doc) {
    int position = 0;
    for (IN fl : doc) {
      // position in document
      fl.set(CoreAnnotations.PositionAnnotation.class, Integer.toString((position++)).intern());

      // word shape
      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) && ! flags.useShapeStrings) {
        // TODO: if we pass in a FeatureFactory, as suggested by an earlier comment,
        // we should use that FeatureFactory's getWord function
        String word = fl.get(CoreAnnotations.TextAnnotation.class);
        if (flags.wordFunction != null) {
          word = flags.wordFunction.apply(word);
        }
        if ( ! word.isEmpty() && Character.isLowerCase(word.codePointAt(0))) {
          knownLCWords.add(intern(word));
        }

        String s = intern(WordShapeClassifier.wordShape(word, flags.wordShape, knownLCWords));
        fl.set(CoreAnnotations.ShapeAnnotation.class, s);
      }

      // normalizing and interning was the following; should presumably now be:
      // if ("CTBSegDocumentReader".equalsIgnoreCase(flags.documentReader)) {
      if ("edu.stanford.nlp.wordseg.Sighan2005DocumentReaderAndWriter".equalsIgnoreCase(flags.readerAndWriter)) {
        // for Chinese segmentation, "word" is no use and ignore goldAnswer for memory efficiency.
        fl.set(CoreAnnotations.CharAnnotation.class,intern(fix(fl.get(CoreAnnotations.CharAnnotation.class))));
      } else {
        fl.set(CoreAnnotations.TextAnnotation.class, intern(fix(fl.get(CoreAnnotations.TextAnnotation.class))));
        // only override GoldAnswer if not set - so that a DocumentReaderAndWriter can set it right in the first place.
        if (fl.get(CoreAnnotations.GoldAnswerAnnotation.class) == null) {
          fl.set(CoreAnnotations.GoldAnswerAnnotation.class, fl.get(CoreAnnotations.AnswerAnnotation.class));
        }
      }
    }
  }

  /**
   * Take a {@link List} of documents (which are themselves {@link List}s
   * of something that extends {@link CoreMap}, CoreLabel by default),
   * and if any are longer than the length
   * specified by flags.maxDocSize split them up.  If maxDocSize is negative,
   * nothing is changed.  In practice, documents need to be not too long or
   * else the CRF inference will fail due to numerical problems.
   * This method tries to be smart
   * and split on sentence boundaries, but this is hard-coded to English.
   *
   * @param docs The list of documents whose length might be adjusted.
   */
  private void fixDocLengths(List<List<IN>> docs) {
    final int maxDocSize = flags.maxDocSize;

    WordToSentenceProcessor<IN> wts = null; // allocated lazily
    List<List<IN>> newDocuments = new ArrayList<>();
    for (List<IN> document : docs) {
      if (maxDocSize <= 0 || document.size() <= maxDocSize) {
        if (flags.keepEmptySentences || ! document.isEmpty()) {
          newDocuments.add(document);
        }
        continue;
      }
      if (wts == null) {
        wts = new WordToSentenceProcessor<>();
      }
      List<List<IN>> sentences = wts.process(document);
      List<IN> newDocument = new ArrayList<>();
      for (List<IN> sentence : sentences) {
        if (newDocument.size() + sentence.size() > maxDocSize) {
          if ( ! newDocument.isEmpty()) {
            newDocuments.add(newDocument);
          }
          newDocument = new ArrayList<>();
        }
        newDocument.addAll(sentence);
      }
      if (flags.keepEmptySentences || ! newDocument.isEmpty()) {
        newDocuments.add(newDocument);
      }
    }

    docs.clear();
    docs.addAll(newDocuments);
  }

  private void iobTags(List<IN> doc) {
    String lastTag = "";
    for (IN wi : doc) {
      String answer = wi.get(CoreAnnotations.AnswerAnnotation.class);
      if ( ! flags.backgroundSymbol.equals(answer) && answer != null) {
        int index = answer.indexOf('-');
        String prefix;
        String label;
        if (index < 0) {
          prefix = "";
          label = answer;
        } else {
          prefix = answer.substring(0,index);
          label = answer.substring(index+1);
        }

        if ( ! prefix.equals("B")) {
          if ( ! label.equals(lastTag)) {
            wi.set(CoreAnnotations.AnswerAnnotation.class, "B-" + label);
          } else {
            wi.set(CoreAnnotations.AnswerAnnotation.class, "I-" + label);
          }
        }
        lastTag = label;
      } else {
        lastTag = answer;
      }
    }
  }

  /** Change some form of IOB/IOE encoding via forms like "I-PERS" to
   *  IO encoding as just "PERS".
   *
   *  @param doc The document for which the AnswerAnnotation will be changed (in place)
   */
  private void mergeTags(List<IN> doc) {
    for (IN wi : doc) {
      String answer = wi.get(CoreAnnotations.AnswerAnnotation.class);
      if (answer == null) {
        continue;
      }
      if ( ! answer.equals(flags.backgroundSymbol)) {
        int index = answer.indexOf('-');
        if (index >= 0) {
          answer = answer.substring(index + 1);
        }
      }
      wi.set(CoreAnnotations.AnswerAnnotation.class, answer);
    }
  }

  // This class inherits ObjectBank's implementation of the two toArray() methods.
  // These are implemented in terms of iterator(), and hence they will correctly use the WrappedIterator.
  // Forwarding these methods to the wrapped ObjectBank would be wrong, as then wrapper processing doesn't happen.


  // all the other crap from ObjectBank
  @Override
  public boolean add(List<IN> o) { return wrapped.add(o); }
  @Override
  public boolean addAll(Collection<? extends List<IN>> c) { return wrapped.addAll(c); }
  @Override
  public void clear() { wrapped.clear(); }
  @Override
  public void clearMemory() { wrapped.clearMemory(); }
  @Override
  public boolean contains(Object o) { return wrapped.contains(o); }
  @Override
  public boolean containsAll(Collection<?> c) { return wrapped.containsAll(c); }
  @Override
  public boolean isEmpty() { return wrapped.isEmpty(); }
  @Override
  public void keepInMemory(boolean keep) { wrapped.keepInMemory(keep); }
  @Override
  public boolean remove(Object o) { return wrapped.remove(o); }
  @Override
  public boolean removeAll(Collection<?> c) { return wrapped.removeAll(c); }
  @Override
  public boolean retainAll(Collection<?> c) { return wrapped.retainAll(c); }
  @Override
  public int size() { return wrapped.size(); }

} // end class ObjectBankWrapper
