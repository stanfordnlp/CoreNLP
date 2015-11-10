package edu.stanford.nlp.process;


import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.Document;

/**
 * Class AbstractListProcessor
 *
 * @author Teg Grenager
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <IN> The type of the input document tokens
 * @param <OUT> The type of the output document tokens
 * @param <L> The type of the labels (for the document for classification)
 * @param <F> The type of the features (for the document for classification)
 */
public abstract class AbstractListProcessor<IN,OUT,L,F> implements ListProcessor<IN,OUT>, DocumentProcessor<IN,OUT, L, F> {

  public AbstractListProcessor() {
  }

  public Document<L, F, OUT> processDocument(Document<L, F, IN> in) {
    Document<L, F, OUT> doc = in.blankDocument();
    doc.addAll(process(in));
    return doc;
  }

  /** Process a list of lists of tokens.  For example this might be a
   *  list of lists of words.
   *
   * @param lists a List of objects of type List
   * @return a List of objects of type List, each of which has been processed.
   */
  public List<List<OUT>> processLists(List<List<IN>> lists) {
    List<List<OUT>> result = new ArrayList<List<OUT>>(lists.size());
    for (List<IN> list : lists) {
      List<OUT> outList = process(list);
      result.add(outList);
    }
    return result;
  }

}
