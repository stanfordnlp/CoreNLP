package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.fsm.TransducerGraph;
import edu.stanford.nlp.fsm.QuasiDeterminizer;
import edu.stanford.nlp.fsm.AutomatonMinimizer;
import edu.stanford.nlp.fsm.FastExactAutomatonMinimizer;

import java.util.List;

/**
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class ExactGrammarCompactor extends GrammarCompactor {

  TransducerGraph.GraphProcessor quasiDeterminizer = new QuasiDeterminizer();
  AutomatonMinimizer minimizer = new FastExactAutomatonMinimizer();
  TransducerGraph.NodeProcessor ntsp = new TransducerGraph.SetToStringNodeProcessor(new PennTreebankLanguagePack());
  TransducerGraph.NodeProcessor otsp = new TransducerGraph.ObjectToSetNodeProcessor();
  TransducerGraph.ArcProcessor isp = new TransducerGraph.InputSplittingProcessor();
  TransducerGraph.ArcProcessor ocp = new TransducerGraph.OutputCombiningProcessor();
  private boolean saveGraphs; // = false;

  public ExactGrammarCompactor(Options op, 
                               boolean saveGraphs, boolean verbose) {
    super(op);
    this.saveGraphs = saveGraphs;
    this.verbose = verbose;
    outputType = NORMALIZED_LOG_PROBABILITIES;
  }

  @Override
  protected TransducerGraph doCompaction(TransducerGraph graph, List l1, List l3) {
    TransducerGraph result = graph;
    if (saveGraphs) {
      writeFile(result, "unminimized", (String) result.getEndNodes().iterator().next());
    }
    result = quasiDeterminizer.processGraph(result);
    result = new TransducerGraph(result, ocp); // combine outputs into inputs
    result = minimizer.minimizeFA(result); // minimize the thing
    //result = new  TransducerGraph(graph, otsp); // for debugging
    result = new TransducerGraph(result, ntsp);  // pull out strings from sets returned by minimizer
    result = new TransducerGraph(result, isp); // split outputs from inputs
    if (saveGraphs) {
      writeFile(result, "exactminimized", (String) result.getEndNodes().iterator().next());
    }
    // for debugging do comparison of the paths accepted by graph and result
    //System.err.println(TransducerGraph.testGraphPaths(graph, result, 100));
    return result;
  }

}
