package edu.stanford.nlp.sequences;

import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.io.PrintWriter;
import java.io.Serializable;

/**
 * This interface is used for reading data and writing
 * output into and out of sequence classifiers.
 * If you subclass this interface, all of the other
 * mechanisms necessary for getting your data into a
 * sequence classifier will be taken care of
 * for you.  Subclasses <b>MUST</b> have an empty constructor as
 * they can be instantiated by reflection, and
 * there is a promise that the init method will
 * be called immediately after construction.
 *
 * @author Jenny Finkel
 */

public interface DocumentReaderAndWriter<IN extends CoreMap>
        extends IteratorFromReaderFactory<List<IN>>, Serializable {

  /**
   * This will be called immediately after construction.  It's easier having
   * an init() method because DocumentReaderAndWriter objects are usually
   * created using reflection.
   *
   * @param flags Flags specifying behavior
   */
  public void init(SeqClassifierFlags flags);

  /**
   * This method prints the output of the classifier to a
   * {@link PrintWriter}.
   *
   * @param doc The document which has answers (it has been classified)
   * @param out Where to send the output
   */
  public void printAnswers(List<IN> doc, PrintWriter out);

}
