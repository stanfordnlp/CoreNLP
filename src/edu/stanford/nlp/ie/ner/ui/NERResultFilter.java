package edu.stanford.nlp.ie.ner.ui;

/**
 * An interface for filtering a set of NERResults.  Reject results for which filter return true.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public interface NERResultFilter {
  public boolean filter(NERResult result);
}
