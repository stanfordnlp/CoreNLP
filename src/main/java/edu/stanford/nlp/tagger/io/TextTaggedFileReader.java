package edu.stanford.nlp.tagger.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import edu.stanford.nlp.ling.TaggedWord;

public class TextTaggedFileReader implements TaggedFileReader {
  private final BufferedReader reader;
  private final String tagSeparator;
  private final String filename;

  private int numSentences; // = 0;

  private List<TaggedWord> next;

  public TextTaggedFileReader(TaggedFileRecord record) {
    filename = record.filename();
    try {
      reader = new BufferedReader(new InputStreamReader
                                  (new FileInputStream(filename), 
                                   record.encoding));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    tagSeparator = record.tagSeparator;

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
    String line;
    try {
      line = reader.readLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (line == null) {
      next = null;
      return;
    }

    ++numSentences;
    next = new ArrayList<>();
    StringTokenizer st = new StringTokenizer(line);
    //loop over words in a single sentence

    while (st.hasMoreTokens()) {
      String token = st.nextToken();

      int indexUnd = token.lastIndexOf(tagSeparator);
      if (indexUnd < 0) {
        throw new IllegalArgumentException("Data format error: can't find delimiter \"" + tagSeparator + "\" in word \"" + token + "\" (line " + (numSentences+1) + " of " + filename + ')');
      }
      String word = token.substring(0, indexUnd).intern();
      String tag = token.substring(indexUnd + 1).intern();
      next.add(new TaggedWord(word, tag));
    }
  }

  @Override
  public void remove() { throw new UnsupportedOperationException(); }

}
