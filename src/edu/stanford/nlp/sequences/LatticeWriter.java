package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.fsm.DFSA;

import java.util.List;
import java.io.PrintWriter;

/**
 * This interface is used for writing
 * lattices out of {@link SequenceClassifier}s.
 * 
 * @author Michel Galley
 */

public interface LatticeWriter<IN extends CoreMap, T, S> {
  
  /**
   * This method prints the output lattice (typically, Viterbi search graph) of 
   * the classifier to a {@link PrintWriter}.
   */
  public void printLattice(DFSA<T, S> tagLattice, List<IN> doc, PrintWriter out) ;
  
}
