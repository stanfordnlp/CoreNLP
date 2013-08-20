package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.fsm.AutomatonMinimizer;
import edu.stanford.nlp.fsm.ExactAutomatonMinimizer;
import edu.stanford.nlp.fsm.FastExactAutomatonMinimizer;
import edu.stanford.nlp.fsm.TransducerGraph;
import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Timing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//import fsm.*;

/**
 * A class for testing graph minimization algorithms.
 *
 * @author Teg Grenager
 * @version 11/02/03
 */
public class GraphMinTester {

  /** Not instantiable from outside. */
  private GraphMinTester() {}

  TreebankLangParserParams tlpParams = new EnglishTreebankParserParams();
  Options op = new Options(tlpParams);

  /**
   */
  public Map<String, List<List<String>>> extractPaths(String path, int low, int high) {

    Treebank trainTreebank = tlpParams.memoryTreebank(); // this is a new one
    System.out.print("Reading in treebank...");
    Timing.startTime();
    trainTreebank.loadPath(path, new NumberRangeFileFilter(low, high, true));
    Timing.tick("done");

    System.out.print("Extracting trees...");
    Timing.startTime();
    List<Tree> trainTrees = new ArrayList<Tree>();
    for (Tree tree : trainTreebank) {
      trainTrees.add(tree);
    }
    Timing.tick("done");

    System.out.print("Extracting paths...");
    Timing.startTime();
    Extractor<Map<String, List<List<String>>>> pExtractor = new PathExtractor(tlpParams.headFinder(), op);
    Map<String, List<List<String>>> allPaths = pExtractor.extract(trainTrees);
    Timing.tick("done");
    return allPaths;
  }


  public void makeGrammarGraphsAndPrintStats() {
    // get all the paths from the treebank
    Map<String, List<List<String>>> allPaths = extractPaths("/u/nlp/stuff/corpora/Treebank3/parsed/mrg/wsj", 200, 2199);

    TransducerGraph.ArcProcessor ocp = new TransducerGraph.OutputCombiningProcessor();
    System.out.println("symb\tN\tminN\tM\tK\tKN/M\tEM\tSEM\tFEM");
    int pathLength = 5;
    AutomatonMinimizer exactMinimizer = new ExactAutomatonMinimizer(false);
    AutomatonMinimizer sparseExactMinimizer = new ExactAutomatonMinimizer(true);
    AutomatonMinimizer fastExactMinimizer = new FastExactAutomatonMinimizer();
    // iterate over symbols
    for (String symbol : allPaths.keySet()) {
      List<List<String>> paths = allPaths.get(symbol);
      TransducerGraph graph = TransducerGraph.createGraphFromPaths(paths, 3);
      graph = new TransducerGraph(graph, ocp); // push outputs in!

      //      System.out.println("\nPrinting tree for " + symbol);
      //      graph.depthFirstSearch(true);

      int numNodes = graph.getNodes().size();
      int numArcs = graph.getArcs().size();
      int numActualInputs = graph.getInputs().size();
      System.out.print(symbol + "\t" + numNodes + "\t");
      long startTime;
      long elapsedTime;
      //      long numNodes1=0, numNodes2=0, time1=0, time2=0;

      // now minimize it using each of the minimizers
      TransducerGraph graph1 = new TransducerGraph(graph);
      System.gc();
      startTime = System.currentTimeMillis();
      TransducerGraph result1 = exactMinimizer.minimizeFA(graph1);
      elapsedTime = System.currentTimeMillis() - startTime;
      long time1 = elapsedTime;
      long sqtime1 = elapsedTime * elapsedTime;
      int numNodes1 = result1.getNodes().size();

      TransducerGraph graph2 = new TransducerGraph(graph);
      System.gc();
      startTime = System.currentTimeMillis();
      TransducerGraph result2 = sparseExactMinimizer.minimizeFA(graph2);
      elapsedTime = System.currentTimeMillis() - startTime;
      long time2 = elapsedTime;
      long sqtime2 = elapsedTime * elapsedTime;
      long numNodes2 = result2.getNodes().size();

      TransducerGraph graph3 = new TransducerGraph(graph);
      System.gc();
      startTime = System.currentTimeMillis();
      TransducerGraph result3 = fastExactMinimizer.minimizeFA(graph3);
      elapsedTime = System.currentTimeMillis() - startTime;
      long time3 = elapsedTime;
      long sqtime3 = elapsedTime * elapsedTime;
      long numNodes3 = result3.getNodes().size();

      if (numNodes1 != numNodes2 || numNodes1 != numNodes3) {
        System.out.println("Minimizers don't agree on the number of nodes.");
      }

      System.out.println(numNodes1 + "\t" + numArcs + "\t" + numActualInputs + "\t" + ((double) numActualInputs * (double) numNodes / numArcs) + "\t" + (time1 / 1000.0) + "\t" + (time2 / 1000.0) + "\t" + (time3 / 1000.0));
    }
  }

  public static void makeRandomGraphsAndPrintStats() {

    int numSamples = 5;
    TransducerGraph.ArcProcessor ocp = new TransducerGraph.OutputCombiningProcessor();
    System.out.println("inp\tlen\tpaths\tN\tminN\tM\tK\tKN/M\tEM\tSEM\tFEM\tEMvar\tSEMvar\tFEMvar");
    int pathLength = 5;
    AutomatonMinimizer exactMinimizer = new ExactAutomatonMinimizer(false);
    AutomatonMinimizer sparseExactMinimizer = new ExactAutomatonMinimizer(true);
    AutomatonMinimizer fastExactMinimizer = new FastExactAutomatonMinimizer();
    for (int numInputs = 128; numInputs <= 512; numInputs *= 2) {
      for (int numPaths = 128; numPaths <= 2048; numPaths *= 2) {
        int numNodes = 0;
        int numArcs = 0;
        int numActualInputs = 0;
        int numNodes1 = 0;
        int numNodes2 = 0;
        int numNodes3 = 0;
        long time1 = 0L;
        long time2 = 0L;
        long time3 = 0L;
        long sqtime1 = 0L;
        long sqtime2 = 0L;
        long sqtime3 = 0L;
        for (int i = 0; i < numSamples; i++) {
          List paths = TransducerGraph.createRandomPaths(numPaths, pathLength, 1.0, numInputs);
          TransducerGraph graph = TransducerGraph.createGraphFromPaths(paths, -1);
          //graph = new TransducerGraph(graph, ocp); // push outputs in!

          numNodes += graph.getNodes().size();
          numArcs += graph.getArcs().size();
          numActualInputs += graph.getInputs().size();
          long startTime;
          long elapsedTime;

          // now minimize it using each of the minimizers
          TransducerGraph graph1 = new TransducerGraph(graph);
          System.gc();
          startTime = System.currentTimeMillis();
          TransducerGraph result1 = exactMinimizer.minimizeFA(graph1);
          elapsedTime = System.currentTimeMillis() - startTime;
          time1 += elapsedTime;
          sqtime1 += elapsedTime * elapsedTime;
          numNodes1 += result1.getNodes().size();

          TransducerGraph graph2 = new TransducerGraph(graph);
          System.gc();
          startTime = System.currentTimeMillis();
          TransducerGraph result2 = sparseExactMinimizer.minimizeFA(graph2);
          elapsedTime = System.currentTimeMillis() - startTime;
          time2 += elapsedTime;
          sqtime2 += elapsedTime * elapsedTime;
          numNodes2 += result2.getNodes().size();

          TransducerGraph graph3 = new TransducerGraph(graph);
          System.gc();
          startTime = System.currentTimeMillis();
          TransducerGraph result3 = fastExactMinimizer.minimizeFA(graph3);
          elapsedTime = System.currentTimeMillis() - startTime;
          time3 += elapsedTime;
          sqtime3 += elapsedTime * elapsedTime;
          numNodes3 += result3.getNodes().size();

          if (numNodes1 != numNodes2 || numNodes1 != numNodes3) {
            System.out.println("Minimizers don't agree on the number of nodes.");
          }
        }
        double enumNodes = (double) numNodes / (double) numSamples;
        double eminNodes = (double) numNodes1 / (double) numSamples;
        double enumArcs = (double) numArcs / (double) numSamples;
        double enumActualInputs = (double) numActualInputs / (double) numSamples;
        double etime1 = (double) time1 / (double) numSamples;
        double etime2 = (double) time2 / (double) numSamples;
        double etime3 = (double) time3 / (double) numSamples;
        double esqtime1 = (double) sqtime1 / (double) numSamples;
        double esqtime2 = (double) sqtime2 / (double) numSamples;
        double esqtime3 = (double) sqtime3 / (double) numSamples;
        double var1 = esqtime1 - (etime1 * etime1);
        double var2 = esqtime2 - (etime2 * etime2);
        double var3 = esqtime3 - (etime3 * etime3);
        System.out.println(numInputs + "\t" + pathLength + "\t" + numPaths + "\t" + enumNodes + "\t" + eminNodes + "\t" + enumArcs + "\t" + enumActualInputs + "\t" + (enumActualInputs * enumNodes / enumArcs) + "\t" + (etime1 / 1000.0) + "\t" + (etime2 / 1000.0) + "\t" + (etime3 / 1000.0) + "\t" + (var1 / 1000.0) + "\t" + (var2 / 1000.0) + "\t" + (var3 / 1000.0));
      }
    }
  }


  public static void main(String[] args) throws Exception {
    //makeGrammarGraphsAndPrintStats();
    new GraphMinTester().makeRandomGraphsAndPrintStats();
  }
}
