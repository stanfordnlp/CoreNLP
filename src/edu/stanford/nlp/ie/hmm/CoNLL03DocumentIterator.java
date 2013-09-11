package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.AbstractIterator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

/**
 * DocumentReader for CoNLL 03 format (one word per line).
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Huy Nguyen
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */
public class CoNLL03DocumentIterator<L, F, T> extends AbstractIterator<Document<L, F, T>> {
  private static final String DOCSTART = "-DOCSTART-";
  BufferedReader br;
  private String[] targetFields; // includes (Background) as tF[0]
  Document<L, F, T> next;

  public static <L, F, T> IteratorFromReaderFactory<Document<L, F, T>> factory(String[] targetFields) {
    return new CoNLL03DocumentIteratorFactory<L, F, T>(targetFields);
  }

  private CoNLL03DocumentIterator(Reader r, String[] targetFields) {
    this.targetFields = targetFields;
    br = (BufferedReader) r; // ObjectBank should always vend BufferedReaders
    next(); // fetch first Document
  }

  @Override
  public boolean hasNext() {
    return (next != null);
  }

  @Override
  public Document<L, F, T> next() {
    Document<L, F, T> tmp = next;
    String text = readNextDocumentText();
    if (text == null) {
      next = null;
    } else {
      next = parseDocumentText(text);
    }
    return tmp;
  }

  /**
   * Breaks docs on -DOCSTART-
   */
  protected String readNextDocumentText() {
    StringBuffer docBuffer = new StringBuffer();
    String line;
    boolean started = false;

    try {
      while ((line = br.readLine()) != null) {
        if (line.startsWith(DOCSTART) || line.startsWith("======")) {
          if (started) {
            break; // die when you hit the next docstart
          } else {
            continue; // ignore first docstart of whole corpus
          }
        }
        started = true;
        docBuffer.append(line);
        docBuffer.append('\n');
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (docBuffer.length() == 0) {
      return (null); // nothing more to read
    }
    return (docBuffer.toString());
  }

  /**
   * Each line is a word and its type label is converted to corpus type.
   *
   * @param text The complete text of a CoNLL03 file
   * @return A TypedTaggedDocuemnt.
   */
  //TODO: TypedTaggedDocument can only have "Word"s for features...
  //should this class also only have Word and not be templatized on features (F and T)?
  protected Document<L, F, T> parseDocumentText(String text) {
    TypedTaggedDocument doc = new TypedTaggedDocument(targetFields);
    String[] lines = text.split("\n+");
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].length() == 0) {
        continue; // skip blank lines
      }
      String[] tokens = lines[i].split(" ");
      String word = tokens[0];
      String typeLabel = tokens[tokens.length - 1];
      doc.add(new TypedTaggedWord(word, getType(typeLabel)));
    }
    return (doc);
  }

  /**
   * Converts NER type label to corpus type.
   */
  private int getType(String typeLabel) {
    if (typeLabel.equals("O")) {
      return (0);
    }
    int hyphenIndex = typeLabel.indexOf('-');
    if (hyphenIndex != -1) {
      typeLabel = typeLabel.substring(hyphenIndex + 1);
    }
    for (int i = 1; i < targetFields.length; i++) {
      if (typeLabel.equals(targetFields[i])) {
        return (i);
      }
    }
    return (-1);
  }

  private static class CoNLL03DocumentIteratorFactory<L, F, T> implements IteratorFromReaderFactory<Document<L, F, T>> {
    String[] targetFields;

    private CoNLL03DocumentIteratorFactory(String[] targetFields) {
      this.targetFields = targetFields;
    }

    public Iterator<Document<L, F, T>> getIterator(Reader r) {
      return new CoNLL03DocumentIterator<L, F, T>(r, targetFields);
    }
  }


}
