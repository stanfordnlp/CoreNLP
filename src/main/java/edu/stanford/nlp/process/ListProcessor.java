package edu.stanford.nlp.process;


import java.util.List;

/**
 * An interface for things that operate on a List.  This is seen as
 * a lighter weight and more general interface than the Processor interface
 * for documents.  IN and OUT are the type of the objects in the List.
 * The <code>process</code> method acts on a List of IN and produces a List
 * of OUT.
 *
 * @author Teg Grenager
 */
public interface ListProcessor<IN,OUT> {

  /**
   * Take a List (including a Sentence) of input, and return a
   * List that has been processed in some way.
   */
  public List<OUT> process(List<? extends IN> list);

}
