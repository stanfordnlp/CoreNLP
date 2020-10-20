package edu.stanford.nlp.tagger.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import edu.stanford.nlp.ling.TaggedWord;

public class TSVTaggedFileReader implements TaggedFileReader {

  private final BufferedReader reader;
  private final String filename;
  private final int wordColumn, tagColumn;
  private List<TaggedWord> next; // = null;
  private int linesRead; // = 0;

  private static final int DEFAULT_WORD_COLUMN = 0;
  private static final int DEFAULT_TAG_COLUMN = 1;

  public TSVTaggedFileReader(TaggedFileRecord record) {
    filename = record.filename();
    try {
      reader = new BufferedReader(new InputStreamReader
                                  (new FileInputStream(filename),
                                   record.encoding));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    wordColumn = ((record.wordColumn == null) ?
                  DEFAULT_WORD_COLUMN : record.wordColumn);
    tagColumn = ((record.tagColumn == null) ?
                 DEFAULT_TAG_COLUMN : record.tagColumn);
    primeNext();
  }

  @Override
  public Iterator<List<TaggedWord>> iterator() { return this; }

  @Override
  public String filename() { return filename; }

  @Override
  public boolean hasNext() { return next != null; }

  @Override
  public List<TaggedWord> next() {
    if (next == null) {
      throw new NoSuchElementException();
    }
    List<TaggedWord> thisIteration = next;
    primeNext();
    return thisIteration;
  }


  private void primeNext() {
    // eat all blank lines until we hit the next block of text
    String line = "";
    while (line.trim().isEmpty()) {
      try {
        line = reader.readLine();
        ++linesRead;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (line == null) {
        next = null;
        return;
      }
    }
    // we hit something with text, so now we read one line at a time
    // until we hit the next blank line.  the next blank line (or EOF)
    // ends the sentence.
    next = new ArrayList<>();
    while (line != null && ! line.trim().isEmpty()) {
      String[] pieces = line.split("\t");
      if (pieces.length <= wordColumn || pieces.length <= tagColumn) {
        throw new IllegalArgumentException("File " + filename + " line #" +
                                           linesRead + " too short");
      }
      String word = pieces[wordColumn];
      String tag = pieces[tagColumn];
      next.add(new TaggedWord(word, tag));
      try {
        line = reader.readLine();
        ++linesRead;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void remove() { throw new UnsupportedOperationException(); }

}