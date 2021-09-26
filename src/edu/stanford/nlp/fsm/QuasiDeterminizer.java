package edu.stanford.nlp.fsm;

import edu.stanford.nlp.stats.ClassicCounter;
import java.util.*;

/**
 * QuasiDeterminizer performing quasi-determinization on TransducerGraphs.
 *
 * @author Teg Grenager
 * @version 11/02/03
 */
public class QuasiDeterminizer implements TransducerGraph.GraphProcessor {


  public TransducerGraph processGraph(TransducerGraph graph) {
    // compute lambda function
    ClassicCounter lambda = computeLambda(graph); // not destructive
    // do the pushing
    return pushLambdas(graph, lambda);
  }

  /**
   * Takes time linear in number of arcs.
   */
  public static ClassicCounter computeLambda(TransducerGraph graph) {
    LinkedList queue = new LinkedList();
    ClassicCounter lambda = new ClassicCounter();
    ClassicCounter length = new ClassicCounter();
    Map first = new HashMap();
    Set nodes = graph.getNodes();
    for (Object node : nodes) {
      lambda.setCount(node, 0);
      length.setCount(node, Double.POSITIVE_INFINITY);
    }
    Set endNodes = graph.getEndNodes();
    for (Object o : endNodes) {
      lambda.setCount(o, 0);
      length.setCount(o, 0);
      queue.addLast(o);
    }
    // Breadth first search
    // get the first node from the queue
    Object node = null;
    try {
      node = queue.removeFirst();
    } catch (NoSuchElementException e) {
    }
    while (node != null) {
      double oldLen = length.getCount(node);
      Set arcs = graph.getArcsByTarget(node);
      if (arcs != null) {
        for (Object arc1 : arcs) {
          TransducerGraph.Arc arc = (TransducerGraph.Arc) arc1;
          Object newNode = arc.getSourceNode();
          Comparable a = (Comparable) arc.getInput();
          double k = ((Double) arc.getOutput()).doubleValue();
          double newLen = length.getCount(newNode);
          if (newLen == Double.POSITIVE_INFINITY) {
            // we are discovering this
            queue.addLast(newNode);
          }
          Comparable f = (Comparable) first.get(newNode);
          if (newLen == Double.POSITIVE_INFINITY || (newLen == oldLen + 1 && a.compareTo(f) < 0)) { // f can't be null, since we have a newLen
            // we do this to this to newNode when we have new info, possibly many times
            first.put(newNode, a); // ejecting old one if necessary
            length.setCount(newNode, oldLen + 1); // this may already be the case
            lambda.setCount(newNode, k + lambda.getCount(node));
          }
        }
      }
      // get a new node from the queue
      node = null;
      try {
        node = queue.removeFirst();
      } catch (NoSuchElementException e) {
      }
    }
    return lambda;
  }

  /**
   * Takes time linear in number of arcs.
   */
  public TransducerGraph pushLambdas(TransducerGraph graph, ClassicCounter lambda) {

    TransducerGraph result = null;
    try {
      result = graph.clone(); // arcs have been copied too so we don't mess up graph
    } catch (CloneNotSupportedException cnse) {
      throw new RuntimeException(cnse);
    }
    Set<TransducerGraph.Arc> arcs = result.getArcs();
    for (TransducerGraph.Arc arc : arcs) {
      double sourceLambda = lambda.getCount(arc.getSourceNode());
      double targetLambda = lambda.getCount(arc.getTargetNode());
      double oldOutput = ((Double) arc.getOutput()).doubleValue();
      double newOutput = oldOutput + targetLambda - sourceLambda;
      arc.setOutput(Double.valueOf(newOutput));
    }
    // do initialOutput
    double startLambda = lambda.getCount(result.getStartNode());
    if (startLambda != 0.0) {
      // add it back to the outbound arcs from start (instead of adding it to the initialOutput)
      Set<TransducerGraph.Arc> startArcs = result.getArcsBySource(result.getStartNode());
      for (TransducerGraph.Arc arc : startArcs) {
        double oldOutput = ((Double) arc.getOutput()).doubleValue();
        double newOutput = oldOutput + startLambda;
        arc.setOutput(Double.valueOf(newOutput));
      }
    }
    // do finalOutput
    for (Object o : result.getEndNodes()) {
      double endLambda = lambda.getCount(o);
      if (endLambda != 0.0) {
        // subtract it from the inbound arcs to end (instead of subtracting it from the finalOutput)
        Set<TransducerGraph.Arc> endArcs = result.getArcsByTarget(o);
        for (TransducerGraph.Arc arc : endArcs) {
          double oldOutput = ((Double) arc.getOutput()).doubleValue();
          double newOutput = oldOutput - endLambda;
          arc.setOutput(Double.valueOf(newOutput));
        }
      }

    }
    return result;
  }

  public static void main(String[] args) {
    TransducerGraph.GraphProcessor qd = new QuasiDeterminizer();
    List<List<String>> pathList = new ArrayList<>();
    TransducerGraph graph = TransducerGraph.createRandomGraph(1000, 10, 1.0, 10, pathList);
    StringBuilder b = new StringBuilder();
    graph.depthFirstSearch(true, b);
    System.out.println(b.toString());
    System.out.println("Done creating random graph");
    //    TransducerGraph.printPathOutputs(pathList, graph, false);
    //System.out.println("Depth first search from start node");
    //TransducerGraph.depthFirstSearch(graph, TransducerGraph.END_NODE, new HashSet(), 0, false);
    TransducerGraph newGraph = qd.processGraph(graph);
    System.out.println("Done quasi-determinizing");
    //TransducerGraph.printPathOutputs(pathList, newGraph, false);
    //System.out.println("Depth first search from start node");
    //TransducerGraph.depthFirstSearch(newGraph, TransducerGraph.END_NODE, new HashSet(), 0, false);
    TransducerGraph.testGraphPaths(graph, newGraph, 1000);
  }

}
