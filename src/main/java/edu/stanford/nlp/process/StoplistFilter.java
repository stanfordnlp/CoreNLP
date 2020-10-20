package edu.stanford.nlp.process;


import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.Word;

import java.io.File;


/**
 * Filter which removes stop-listed words.
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 */

public class StoplistFilter<L, F> implements DocumentProcessor<Word, Word, L, F> {

  private StopList stoplist;

  /**
   * Create a new StopListFilter with a small default stoplist
   */
  public StoplistFilter() {
    this(new StopList());
  }

  /**
   * Create a new StopListFilter with the stoplist given in <code>stoplistfile</code>
   */
  public StoplistFilter(String stoplistfile) {
    this(new StopList(new File(stoplistfile)));
  }

  /**
   * Create a new StoplistFilter with the given StopList.
   */
  public StoplistFilter(StopList stoplist) {
    this.stoplist = stoplist;
  }

  /**
   * Returns a new Document with the same meta-data as <tt>in</tt> and the same words
   * except those on the stop list this filter was constructed with.
   */
  public Document<L, F, Word> processDocument(Document<L, F, Word> in) {
    Document<L, F, Word> out = in.blankDocument();
    for (Word w: in) {
      if (!stoplist.contains(w)) {
        out.add(w);
      }
    }
    return (out);
  }
}

