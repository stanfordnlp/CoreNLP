package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.annotation.TaggedStreamTokenizer;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.objectbank.DelimitIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.util.Function;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reads in IE documents delimited by ENDOFDOC lines and using simple xml tags
 * to mark target fields.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */
public class FreitagIECollectionIterator<L> extends IECollectionIterator<TypedTaggedDocument<L>> {
  private static String[] allFields = {"acqabr", "acqbus", "acqcode", "acqloc", "acquired", "dlramt", "purchabr", "purchaser", "purchcode", "seller", "sellerabr", "sellercode", "status"};

  private static final String ENDOFDOC = "ENDOFDOC";
  public static final int NUM_MARKUP_FORMS = 2;
  private DelimitIterator<TypedTaggedDocument<L>> di;

  private IteratorFromReaderFactory<TypedTaggedDocument<L>> dif;

  /**
   * Calls {@link #factory(String[],boolean) factory(targetFields, true)}.
   */
  public static final <L> IteratorFromReaderFactory<TypedTaggedDocument<L>> factory(String[] targetFields) {
    return factory(targetFields, true);
  }

  /**
   * Returns a factory that vends FreitageIECollectionIterators.
   */
  public static final <L> IteratorFromReaderFactory<TypedTaggedDocument<L>> factory(String[] targetFields, boolean discardHtml) {
    return new FreitagIECollectionIteratorFactory<L>(targetFields, discardHtml);
  }

  @Override
  public boolean hasNext() {
    return di.hasNext();
  }

  /**
   * Returns the next {@link TypedTaggedDocument} in the Collection.
   */
  @Override
  public TypedTaggedDocument<L> next() {
    return di.next();
  }

  /**
   * Constructs a new FreitagIECollectionIterator from the given input source
   * that will tag the given target fields. If targetFields[0] is not
   * "(Background)" it is added as such internally.
   *
   * @param discardHtml whether to discard HTML tags from the input source
   */
  @SuppressWarnings("unchecked")
  protected FreitagIECollectionIterator(Reader in, String[] targetFields, boolean discardHtml) {
    setTargetFields(targetFields);
    dif = DelimitIterator.getFactory(ENDOFDOC, new FreitagIEParser(getTargetFields(), discardHtml), false);
    di = (DelimitIterator<TypedTaggedDocument<L>>) dif.getIterator(in);
  }

  private static class FreitagIECollectionIteratorFactory<L> implements IteratorFromReaderFactory<TypedTaggedDocument<L>> {
    String[] targetFields;
    boolean discardHtml;

    public FreitagIECollectionIteratorFactory(String[] targetFields, boolean discardHtml) {
      this.targetFields = targetFields;
      this.discardHtml = discardHtml;
    }

    public Iterator<TypedTaggedDocument<L>> getIterator(Reader r) {
      return new FreitagIECollectionIterator<L>(r, targetFields, discardHtml);
    }
  }

  private static class FreitagIEParser<L> implements Function<String, TypedTaggedDocument<L>> {
    String[] targetFields;
    boolean discardHtml;

    public FreitagIEParser(String[] targetFields, boolean discardHtml) {
      this.targetFields = targetFields;
      this.discardHtml = discardHtml;
    }

    /**
     * Parses the given text by looking for XML tags for target fields.
     * For text inside an XML tag, if the name of the tag is one of the
     * target fields passed in during construction, those words get a unique
     * type. All other words are given type 0 (i.e. background). For example,
     * if the targetField passed in was <tt>target</tt>, then the text
     * "this is a <target>taget field</target> in a doc" would be parsed into
     * 8 TypedTaggedWords, of which "target" and "field" have type 1 and all
     * the other words have type 0.
     *
     * @return a TypedTaggedDocument where tagged fields are given non-zero types.
     */
    public TypedTaggedDocument<L> apply(String text) {
      if (text == null) {
        return null;
      }
      TypedTaggedDocument<L> doc = new TypedTaggedDocument<L>(targetFields);

      // holds the two markup forms recognized
      String[][] tags = new String[targetFields.length][];
      // start from 1 as targetFields[0] is background state
      for (int i = 1; i < targetFields.length; i++) {
        tags[i] = new String[NUM_MARKUP_FORMS];
        tags[i][0] = "<" + targetFields[i] + ">";
        tags[i][1] = "<tag name=\"" + targetFields[i] + "\" value=\"start\"/>";
      }

      TaggedStreamTokenizer tokenizer = makeTokenizer(new StringReader(text));
      if (tokenizer == null) {
        throw(new IllegalStateException("Unable to create tokenizer"));
      }
      List<Word> words = new ArrayList<Word>();   // list of words for this document
      tokenizer.setDiscardHtml(discardHtml);
      try {
        while (tokenizer.ttype != TaggedStreamTokenizer.TT_EOF) {
          if (tokenizer.sval != null) {
            int newtype = -1;
            boolean set = false;
            if (tokenizer.ttype != TaggedStreamTokenizer.TT_TARGET_WORD) {
              newtype = 0;
            } else {
              for (int t = 1; t < targetFields.length && !set; t++) {
                for (int u = 0; u < tags[t].length && !set; u++) {
                  if (tags[t][u].equals(tokenizer.attr)) {
                    set = true;
                  }
                  newtype = t;
                }
              }
            }
            words.add(new TypedTaggedWord(tokenizer.sval, newtype));
          }
          tokenizer.nextToken();
        }
        doc.addAll(words); // add parsed words to this doc
      } catch (Exception e) {
        e.printStackTrace();
      }

      return (doc);
    }

    private TaggedStreamTokenizer makeTokenizer(Reader input) {
      TaggedStreamTokenizer tokenizer;

      tokenizer = new TaggedStreamTokenizer(input);
      tokenizer.setKeeperCharacters(".,:!?;{}-()/$'@");

      for (int i = 1; i < targetFields.length; i++) {
        tokenizer.addTarget("<" + targetFields[i] + ">", "</" + targetFields[i] + ">", true);
        tokenizer.addTarget("<tag name=\"" + targetFields[i] + "\" value=\"start\"/>", "<tag name=\"" + targetFields[i] + "\" value=\"end\"/>");
      }
      return tokenizer;
    }
  }

  /**
   * Takes the given Freitag file, and writes it out, one token per line, with the label in the second column.
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: FreitagIEColletionIterator <file>");
    }
    IteratorFromReaderFactory<TypedTaggedDocument<String>> x = FreitagIECollectionIterator.factory(allFields);
    ObjectBank<TypedTaggedDocument<String>> ob = new ObjectBank<TypedTaggedDocument<String>>(new ReaderIteratorFactory(new File(args[0])), x);
    int docId = 1;
    for (TypedTaggedDocument<String> doc : ob) {
      System.out.println("Doc" + docId);
      for (int i = 0; i < doc.size(); i++) {
        TypedTaggedWord word = (TypedTaggedWord) doc.get(i);
        System.out.println(word.word() + " " + ((word.type() == 0) ? "0" : doc.getTargetField(word.type())));
      }
      System.out.println();
      docId++;
    }
  }
}
