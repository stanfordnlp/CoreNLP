package old.edu.stanford.nlp.sequences;

import old.edu.stanford.nlp.ling.CoreLabel;
import old.edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

import java.util.List;
import java.io.PrintWriter;
import java.io.Serializable;

/**
 * This interface is used for reading data and writing
 * output into and out of {@link SequenceClassifier}s.
 * If you subclass this interface, all of the other
 * mechanisms necessary for getting your data into a
 * {@link SequenceClassifier} will be taken care of
 * for you.  Subclasses <B>MUST</B> have an empty constructor so
 * that they can be instantiated by reflection, and
 * there is a promise that the init method will
 * be called immediately after construction.
 *
 * @author Jenny Finkel
 */

public interface  DocumentReaderAndWriter extends IteratorFromReaderFactory<List<CoreLabel>>, Serializable {

  /**
   * Will be called immediately after construction.  It's easier having
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
  public void printAnswers(List<CoreLabel> doc, PrintWriter out);

}
