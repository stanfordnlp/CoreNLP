package edu.stanford.nlp.fsm;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Maps;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * TransducerGraph represents a deterministic finite state automaton
 * without epsilon transitions.
 *
 * @author Teg Grenager
 * @version 11/02/03
 */
// TODO: needs some work to make type-safe.
// (In several places, it takes an Object and does instanceof to see what
// it is, or assumes one of the alphabets is a Double, etc....)
public class TransducerGraph implements Cloneable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TransducerGraph.class);

  public static final String EPSILON_INPUT = "EPSILON";

  private static final String DEFAULT_START_NODE = "START";

  private static final Random r = new Random();

  // internal data structures
  private final Set<Arc> arcs;
  private final Map<Object, Set<Arc>> arcsBySource;
  private final Map<Object, Set<Arc>> arcsByTarget;
  private final Map<Object, Set<Arc>> arcsByInput;
  private Map<Pair<Object, Object>, Arc> arcsBySourceAndInput;
  private Map<Object, Set<Arc>> arcsByTargetAndInput;
  private Object startNode;
  private Set endNodes;
  private boolean checkDeterminism = false;

  public void setDeterminism(boolean checkDeterminism) {
    this.checkDeterminism = checkDeterminism;
  }

  public TransducerGraph() {
    arcs = Generics.newHashSet();
    arcsBySource = Generics.newHashMap();
    arcsByTarget = Generics.newHashMap();
    arcsByInput = Generics.newHashMap();
    arcsBySourceAndInput = Generics.newHashMap();
    arcsByTargetAndInput = Generics.newHashMap();
    endNodes = Generics.newHashSet();
    setStartNode(DEFAULT_START_NODE);
  }

  public TransducerGraph(TransducerGraph other) {
    this(other, (ArcProcessor) null);
  }

  public TransducerGraph(TransducerGraph other, ArcProcessor arcProcessor) {
    this(other.getArcs(), other.getStartNode(), other.getEndNodes(), arcProcessor, null);
  }

  public TransducerGraph(TransducerGraph other, NodeProcessor nodeProcessor) {
    this(other.getArcs(), other.getStartNode(), other.getEndNodes(), null, nodeProcessor);
  }

  public TransducerGraph(Set<Arc> newArcs, Object startNode, Set endNodes, ArcProcessor arcProcessor, NodeProcessor nodeProcessor) {
    this();
    ArcProcessor arcProcessor2 = null;
    if (nodeProcessor != null) {
      arcProcessor2 = new NodeProcessorWrappingArcProcessor(nodeProcessor);
    }
    for (Arc a : newArcs) {
      a = new Arc(a); // make a copy
      if (arcProcessor != null) {
        a = arcProcessor.processArc(a);
      }
      if (arcProcessor2 != null) {
        a = arcProcessor2.processArc(a);
      }
      addArc(a);
    }
    if (nodeProcessor != null) {
      this.startNode = nodeProcessor.processNode(startNode);
    } else {
      this.startNode = startNode;
    }
    if (nodeProcessor != null) {
      if (endNodes != null) {
        for (Object o : endNodes) {
          this.endNodes.add(nodeProcessor.processNode(o));
        }
      }
    } else {
      if (endNodes != null) {
        this.endNodes.addAll(endNodes);
      }
    }
  }

  /**
   * Uses the Arcs newArcs.
   */
  public TransducerGraph(Set<Arc> newArcs) {
    this(newArcs, null, null, null, null);
  }

  @Override
  public TransducerGraph clone() throws CloneNotSupportedException {
    super.clone();
    TransducerGraph result = new TransducerGraph(this, (ArcProcessor) null);
    return result;
  }

  public Set<Arc> getArcs() {
    return arcs;
  }

  /**
   * Just does union of keysets of maps.
   */
  public Set getNodes() {
    Set result = Generics.newHashSet();
    result.addAll(arcsBySource.keySet());
    result.addAll(arcsByTarget.keySet());
    return result;
  }

  public Set getInputs() {
    return arcsByInput.keySet();
  }

  public void setStartNode(Object o) {
    startNode = o;
  }

  public void setEndNode(Object o) {
    //System.out.println(this + " setting endNode to " + o);
    endNodes.add(o);
  }

  public Object getStartNode() {
    return startNode;
  }

  public Set getEndNodes() {
    //System.out.println(this + " getting endNode " + endNode);
    return endNodes;
  }

  /**
   * Returns a Set of type TransducerGraph.Arc.
   */
  public Set<Arc> getArcsByInput(Object node) {
    return ensure(arcsByInput.get(node));
  }

  /**
   * Returns a Set of type TransducerGraph.Arc.
   */
  public Set<Arc> getArcsBySource(Object node) {
    return ensure(arcsBySource.get(node));
  }

  private static Set<Arc> ensure(Set<Arc> s) {
    if (s == null) {
      return Collections.emptySet();
    }
    return s;
  }

  /**
   * Returns a Set of type TransducerGraph.Arc.
   */
  public Set<Arc> getArcsByTarget(Object node) {
    return ensure(arcsByTarget.get(node));
  }

  /**
   * Can only be one because automaton is deterministic.
   */
  public Arc getArcBySourceAndInput(Object node, Object input) {
    return arcsBySourceAndInput.get(Generics.newPair(node, input));
  }

  /**
   * Returns a Set of type TransducerGraph.Arc.
   */
  public Set<Arc> getArcsByTargetAndInput(Object node, Object input) {
    return ensure(arcsByTargetAndInput.get(Generics.newPair(node, input)));
  }

  /**
   * Slow implementation.
   */
  public Arc getArc(Object source, Object target) {
    Set arcsFromSource = arcsBySource.get(source);
    Set arcsToTarget = arcsByTarget.get(target);
    Set result = Generics.newHashSet();
    result.addAll(arcsFromSource);
    result.retainAll(arcsToTarget); // intersection
    if (result.size() < 1) {
      return null;
    }
    if (result.size() > 1) {
      throw new RuntimeException("Problem in TransducerGraph data structures.");
    }
    // get the only member
    Iterator iterator = result.iterator();
    return (Arc) iterator.next();
  }

  /**
   * @return true if and only if it created a new Arc and added it to the graph.
   */
  public boolean addArc(Object source, Object target, Object input, Object output) {
    Arc a = new Arc(source, target, input, output);
    return addArc(a);
  }

  /**
   * @return true if and only if it added Arc a to the graph.
   *         determinism.
   */
  protected boolean addArc(Arc a) {
    Object source = a.getSourceNode();
    Object target = a.getTargetNode();
    Object input = a.getInput();
    if (source == null || target == null || input == null) {
      return false;
    }
    // add to data structures
    if (arcs.contains(a)) {
      return false;
    }
    // it's new, so add to the rest of the data structures
    // add to source and input map
    Pair p = Generics.newPair(source, input);
    if (arcsBySourceAndInput.containsKey(p) && checkDeterminism) {
      throw new RuntimeException("Creating nondeterminism while inserting arc " + a + " because it already has arc " + arcsBySourceAndInput.get(p) + checkDeterminism);
    }
    arcsBySourceAndInput.put(p, a);
    Maps.putIntoValueHashSet(arcsBySource, source, a);
    p = Generics.newPair(target, input);
    Maps.putIntoValueHashSet(arcsByTargetAndInput, p, a);
    Maps.putIntoValueHashSet(arcsByTarget, target, a);
    Maps.putIntoValueHashSet(arcsByInput, input, a);
    // add to arcs
    arcs.add(a);
    return true;
  }

  public boolean removeArc(Arc a) {
    Object source = a.getSourceNode();
    Object target = a.getTargetNode();
    Object input = a.getInput();
    // remove from arcs
    if (!arcs.remove(a)) {
      return false;
    }
    // remove from arcsBySourceAndInput
    Pair p = Generics.newPair(source, input);
    if (!arcsBySourceAndInput.containsKey(p)) {
      return false;
    }
    arcsBySourceAndInput.remove(p);
    // remove from arcsBySource
    Set<Arc> s = arcsBySource.get(source);
    if (s == null) {
      return false;
    }
    if (!s.remove(a)) {
      return false;
    }
    // remove from arcsByTargetAndInput
    p = Generics.newPair(target, input);
    s = arcsByTargetAndInput.get(p);
    if (s == null) {
      return false;
    }
    if (!s.remove(a)) {
      return false;
    }
    // remove from arcsByTarget
    s = arcsByTarget.get(target);
    if (s == null) {
      return false;
    }
    s = arcsByInput.get(input);
    if (s == null) {
      return false;
    }
    if (!s.remove(a)) {
      return false;
    }
    return true;
  }

  public boolean canAddArc(Object source, Object target, Object input, Object output) {
    Arc a = new Arc(source, target, input, output);
    if (arcs.contains(a)) // inexpensive check
    {
      return false;
    }
    Pair p = Generics.newPair(source, input);
    return !arcsBySourceAndInput.containsKey(p); // expensive check
  }

  /** An arc in a finite state transducer.
   *
   *  @param <NODE> The type of the nodes that an Arc links
   *  @param <IN> The type of the input language of the transducer
   *  @param <OUT> The type of the output language of the transducer
   */
  public static class Arc<NODE, IN, OUT> {

    private NODE sourceNode;
    private NODE targetNode;
    private IN input;
    private OUT output;

    public NODE getSourceNode() {
      return sourceNode;
    }

    public NODE getTargetNode() {
      return targetNode;
    }

    public IN getInput() {
      return input;
    }

    public OUT getOutput() {
      return output;
    }

    public void setSourceNode(NODE o) {
      sourceNode = o;
    }

    public void setTargetNode(NODE o) {
      targetNode = o;
    }

    public void setInput(IN o) {
      input = o;
    }

    public void setOutput(OUT o) {
      output = o;
    }

    @Override
    public int hashCode() {
      return sourceNode.hashCode() ^ (targetNode.hashCode() << 16) ^ (input.hashCode() << 16);
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof Arc)) {
        return false;
      }
      Arc a = (Arc) o;
      return ((sourceNode == null ? a.sourceNode == null : sourceNode.equals(a.sourceNode)) && (targetNode == null ? a.targetNode == null : targetNode.equals(a.targetNode)) && (input == null ? a.input == null : input.equals(a.input)));
    }

    // makes a copy of Arc a
    protected Arc(Arc<NODE,IN,OUT> a) {
      this(a.getSourceNode(), a.getTargetNode(), a.getInput(), a.getOutput());
    }

    protected Arc(NODE sourceNode, NODE targetNode) {
      this(sourceNode, targetNode, null, null);
    }

    protected Arc(NODE sourceNode, NODE targetNode, IN input) {
      this(sourceNode, targetNode, input, null);
    }

    protected Arc(NODE sourceNode, NODE targetNode, IN input, OUT output) {
      this.sourceNode = sourceNode;
      this.targetNode = targetNode;
      this.input = input;
      this.output = output;
    }

    @Override
    public String toString() {
      return sourceNode + " --> " + targetNode + " (" + input + " : " + output + ")";
    }

  } // end static class Arc


  public static interface ArcProcessor {
    /**
     * Modifies Arc a.
     */
    public Arc processArc(Arc a);
  }

  public static class OutputCombiningProcessor implements ArcProcessor {
    @Override
    public Arc processArc(Arc a) {
      a = new Arc(a);
      a.setInput(Generics.newPair(a.getInput(), a.getOutput()));
      a.setOutput(null);
      return a;
    }
  }

  public static class InputSplittingProcessor implements ArcProcessor {
    @Override
    public Arc processArc(Arc a) {
      a = new Arc(a);
      Pair p = (Pair) a.getInput();
      a.setInput(p.first);
      a.setOutput(p.second);
      return a;
    }
  }

  public static class NodeProcessorWrappingArcProcessor implements ArcProcessor {
    private final NodeProcessor nodeProcessor;

    public NodeProcessorWrappingArcProcessor(NodeProcessor nodeProcessor) {
      this.nodeProcessor = nodeProcessor;
    }

    @Override
    public Arc processArc(Arc a) {
      a = new Arc(a);
      a.setSourceNode(nodeProcessor.processNode(a.getSourceNode()));
      a.setTargetNode(nodeProcessor.processNode(a.getTargetNode()));
      return a;
    }
  }

  public static interface NodeProcessor {
    public Object processNode(Object node);
  }

  public static class SetToStringNodeProcessor implements NodeProcessor {
    private TreebankLanguagePack tlp;

    public SetToStringNodeProcessor(TreebankLanguagePack tlp) {
      this.tlp = tlp;
    }

    @Override
    public Object processNode(Object node) {
      Set s = null;
      if (node instanceof Set) {
        s = (Set) node;
      } else {
        if (node instanceof Block) {
          Block b = (Block) node;
          s = b.getMembers();
        } else {
          throw new RuntimeException("Unexpected node class");
        }
      }
      Object sampleNode = s.iterator().next();
      if (s.size() == 1) {
        if (sampleNode instanceof Block) {
          return processNode(sampleNode);
        } else {
          return sampleNode;
        }
      }
      // nope there's a set of things
      if (sampleNode instanceof String) {
        String str = (String) sampleNode;
        if (str.charAt(0) != '@') {
          // passive category...
          return tlp.basicCategory(str) + "-" + s.hashCode(); // TODO remove b/c there could be collisions
          //          return tlp.basicCategory(str) + "-" + System.identityHashCode(s);
        }
      }
      return "@NodeSet-" + s.hashCode(); // TODO remove b/c there could be collisions
      //      return sampleNode.toString();
    }
  }

  public static class ObjectToSetNodeProcessor implements NodeProcessor {
    @Override
    public Object processNode(Object node) {
      return Collections.singleton(node);
    }
  }

  public static interface GraphProcessor {
    public TransducerGraph processGraph(TransducerGraph g);
  }


  public static class NormalizingGraphProcessor implements GraphProcessor {
    boolean forward = true;

    public NormalizingGraphProcessor(boolean forwardNormalization) {
      this.forward = forwardNormalization;
    }

    public TransducerGraph processGraph(TransducerGraph g) {
      g = new TransducerGraph(g);
      Set nodes = g.getNodes();
      for (Object node : nodes) {
        Set<Arc> myArcs = null;
        if (forward) {
          myArcs = g.getArcsBySource(node);
        } else {
          myArcs = g.getArcsByTarget(node);
        }
        // compute a total
        double total = 0.0;
        for (Arc a : myArcs) {
          total += ((Double) a.getOutput()).doubleValue();
        }
        // divide each by total
        for (Arc a : myArcs) {
          a.setOutput(Double.valueOf(Math.log(((Double) a.getOutput()).doubleValue() / total)));
        }
      }
      return g;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    depthFirstSearch(true, sb);
    return sb.toString();
  }


  private boolean dotWeightInverted = false;

  private void setDotWeightingInverted(boolean inverted) {
    dotWeightInverted = true;
  }

  public String asDOTString() {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(3);
    nf.setMinimumFractionDigits(1);
    StringBuilder result = new StringBuilder();
    Set nodes = getNodes();
    result.append("digraph G {\n");
    //    result.append("page = \"8.5,11\";\n");
    //    result.append("margin = \"0.25\";\n");
    // Heuristic number of pages
    int sz = arcs.size();
    int ht = 105;
    int mag = 250;
    while (sz > mag) {
      ht += 105;
      mag *= 2;
    }
    int wd = 8;
    mag = 500;
    while (sz > mag) {
      wd += 8;
      mag *= 4;
    }
    double htd = ht / 10.0;
    result.append("size = \"" + wd + "," + htd + "\";\n");
    result.append("graph [rankdir = \"LR\"];\n");
    result.append("graph [ranksep = \"0.2\"];\n");
    for (Object node : nodes) {
      String cleanString = StringUtils.fileNameClean(node.toString());
      result.append(cleanString);
      result.append(" [ ");
      //      if (getEndNodes().contains(node)) {
      //        result.append("label=\"" + node.toString() + "\", style=filled, ");
      //      } else
      result.append("label=\"" + node.toString() + "\"");
      result.append("height=\"0.3\", width=\"0.3\"");
      result.append(" ];\n");
      for (Arc arc : getArcsBySource(node)) {
        result.append(StringUtils.fileNameClean(arc.getSourceNode().toString()));
        result.append(" -> ");
        result.append(StringUtils.fileNameClean(arc.getTargetNode().toString()));
        result.append(" [ ");
        result.append("label=\"");
        result.append(arc.getInput());
        result.append(" : ");
        // result.append(arc.getOutput());
        Object output = arc.getOutput();
        String wt = "";
        if (output instanceof Number) {
          double dd = ((Number) output).doubleValue();
          if (dd == -0.0d) {
            result.append(nf.format(0.0d));
          } else {
            result.append(nf.format(output));
          }
          int weight;
          if (dotWeightInverted) {
            weight = (int) (20.0 - dd);
          } else {
            weight = (int) dd;
          }
          if (weight > 0) {
            wt = ", weight = \"" + weight + "\"";
          }
          if (dotWeightInverted && dd <= 2.0 || (!dotWeightInverted) && dd >= 20.0) {
            wt += ", style=bold";
          }
        } else {
          result.append(output);
        }
        result.append("\"");
        result.append(wt);
        // result.append("fontsize = 14 ");
        if (arc.getInput().toString().equals("EPSILON")) {
          result.append(", style = \"dashed\" ");
        } else {
          result.append(", style = \"solid\" ");
        }
        // result.append(", weight = \"" + arc.getOutput() + "\" ");
        result.append("];\n");
      }
    }
    result.append("}\n");
    return result.toString();
  }

  public double inFlow(Object node) {
    Set<Arc> arcs = getArcsByTarget(node);
    return sumOutputs(arcs);
  }

  public double outFlow(Object node) {
    Set<Arc> arcs = getArcsBySource(node);
    return sumOutputs(arcs);
  }

  private static double sumOutputs(Set<Arc> arcs) {
    double sum = 0.0;
    for (Arc arc : arcs) {
      sum += ((Double) arc.getOutput()).doubleValue();
    }
    return sum;
  }

  private double getSourceTotal(Object node) {
    double result = 0.0;
    Set<Arc> arcs = getArcsBySource(node);
    if (arcs.isEmpty()) {
      log.info("No outbound arcs from node.");
      return result;
    }
    for (Arc arc : arcs) {
      result += ((Double) arc.getOutput()).doubleValue();
    }
    return result;
  }

  /**
   * For testing only.  Doubles combined by addition.
   */
  public double getOutputOfPathInGraph(List path) {
    double score = 0.0;
    Object node = getStartNode();
    for (Object input : path) {
      Arc arc = getArcBySourceAndInput(node, input); // next input in path
      if (arc == null) {
        System.out.println(" NOT ACCEPTED :" + path);
        return Double.NEGATIVE_INFINITY;
      }
      score += ((Double) arc.getOutput()).doubleValue();
      node = arc.getTargetNode();
    }
    return score;
  }

  /**
   * for testing only. doubles combined by addition.
   */
  public List sampleUniformPathFromGraph() {
    List<Object> list = new ArrayList<>();
    Object node = this.getStartNode();
    Set endNodes = this.getEndNodes();
    while (!endNodes.contains(node)) {
      List<Arc> arcs = new ArrayList<>(this.getArcsBySource(node));
      TransducerGraph.Arc arc = arcs.get(r.nextInt(arcs.size()));
      list.add(arc.getInput());
      node = arc.getTargetNode();
    }
    return list;
  }

  private Map<List, Double> samplePathsFromGraph(int numPaths) {
    Map<List, Double> result = Generics.newHashMap();
    for (int i = 0; i < numPaths; i++) {
      List l = sampleUniformPathFromGraph();
      result.put(l, Double.valueOf(getOutputOfPathInGraph(l)));
    }
    return result;
  }

  /**
   * For testing only.
   */
  private static void printPathOutputs(List<List> pathList, TransducerGraph graph, boolean printPaths) {
    int i = 0;
    for (List path : pathList) {
      if (printPaths) {
        for (Object aPath : path) {
          System.out.print(aPath + " ");
        }
      } else {
        System.out.print(i++ + " ");
      }
      System.out.print("output: " + graph.getOutputOfPathInGraph(path));
      System.out.println();
    }
  }

  /**
   * For testing only.
   */
  public List<Double> getPathOutputs(List<List<String>> pathList) {
    List<Double> outputList = new ArrayList<>();
    for (List<String> path : pathList) {
      outputList.add(Double.valueOf(getOutputOfPathInGraph(path)));
    }
    return outputList;
  }

  public static boolean testGraphPaths(TransducerGraph sourceGraph, TransducerGraph testGraph, int numPaths) {
    for (int i = 0; i < numPaths; i++) {
      List path = sourceGraph.sampleUniformPathFromGraph();
      double score = sourceGraph.getOutputOfPathInGraph(path);
      double newScore = testGraph.getOutputOfPathInGraph(path);
      if ((score - newScore) / (score + newScore) > 1e-10) {
        System.out.println("Problem: " + score + " vs. " + newScore + " on " + path);
        return false;
      }
    }
    return true;
  }


  /**
   * For testing only.  Doubles combined by multiplication.
   */
  private boolean canAddPath(List path) {
    Object node = this.getStartNode();
    for (int j = 0; j < path.size() - 1; j++) {
      Object input = path.get(j);
      Arc arc = this.getArcBySourceAndInput(node, input); // next input in path
      if (arc == null) {
        return true;
      }
      node = arc.getTargetNode();
    }
    Object input = path.get(path.size() - 1); // last element
    Arc arc = this.getArcBySourceAndInput(node, input); // next input in path
    if (arc == null) {
      return true;
    } else {
      return getEndNodes().contains(arc.getTargetNode());
    }
  }

  /**
   * If markovOrder is zero, we always transition back to the start state
   * If markovOrder is negative, we assume that it is infinite
   */
  public static TransducerGraph createGraphFromPaths(List paths, int markovOrder) {
    ClassicCounter pathCounter = new ClassicCounter();
    for (Object o : paths) {
      pathCounter.incrementCount(o);
    }
    return createGraphFromPaths(pathCounter, markovOrder);
  }

  public static <T> TransducerGraph createGraphFromPaths(ClassicCounter<List<T>> pathCounter, int markovOrder) {
    TransducerGraph graph = new TransducerGraph(); // empty
    for (List<T> path : pathCounter.keySet()) {
      double count = pathCounter.getCount(path);
      addOnePathToGraph(path, count, markovOrder, graph);
    }
    return graph;
  }

  // assumes that the path already has EPSILON as the last element.
  public static void addOnePathToGraph(List path, double count, int markovOrder, TransducerGraph graph) {
    Object source = graph.getStartNode();
    for (int j = 0; j < path.size(); j++) {
      Object input = path.get(j);
      Arc a = graph.getArcBySourceAndInput(source, input);
      if (a != null) {
        // increment the arc weight
        a.output = Double.valueOf(((Double) a.output).doubleValue() + count);
      } else {
        Object target;
        if (input.equals(TransducerGraph.EPSILON_INPUT)) {
          target = "END"; // to ensure they all share the same end node
        } else if (markovOrder == 0) {
          // we all transition back to the same state
          target = source;
        } else if (markovOrder > 0) {
          // the state is described by the partial history
          target = path.subList((j < markovOrder ? 0 : j - markovOrder + 1), j + 1);
        } else {
          // the state is described by the full history
          target = path.subList(0, j + 1);
        }
        Double output = Double.valueOf(count);
        a = new Arc(source, target, input, output);
        graph.addArc(a);
      }
      source = a.getTargetNode();
    }
    graph.setEndNode(source);
  }

  /**
   * For testing only. All paths will be added to pathList as Lists.
   * // generate a bunch of paths through the graph with the input alphabet
   * // and create new nodes for each one.
   */
  public static TransducerGraph createRandomGraph(int numPaths, int pathLengthMean, double pathLengthVariance, int numInputs, List<List<String>> pathList) {
    // compute the path length. Draw from a normal distribution
    int pathLength = (int) (r.nextGaussian() * pathLengthVariance + pathLengthMean);
    for (int i = 0; i < numPaths; i++) {
      // make a path
      List<String> path = new ArrayList();
      for (int j = 0; j < pathLength; j++) {
        String input = Integer.toString(r.nextInt(numInputs));
        path.add(input);
      }
      // TODO: createRandomPaths had the following difference:
      // we're done, add one more arc to get to the endNode.
      //input = TransducerGraph.EPSILON_INPUT;
      //path.add(input);
      pathList.add(path);
    }
    return createGraphFromPaths(pathList, -1);
  }

  public static List createRandomPaths(int numPaths, int pathLengthMean, double pathLengthVariance, int numInputs) {
    List pathList = new ArrayList();
    // make a bunch of paths, randomly
    // compute the path length. Draw from a normal distribution
    int pathLength = (int) (r.nextGaussian() * pathLengthVariance + pathLengthMean);
    for (int i = 0; i < numPaths; i++) {
      // make a path
      List<String> path = new ArrayList<>();
      String input;
      for (int j = 0; j < pathLength; j++) {
        input = Integer.toString(r.nextInt(numInputs));
        path.add(input);
      }
      // we're done, add one more arc to get to the endNode.
      input = TransducerGraph.EPSILON_INPUT;
      path.add(input);
      pathList.add(path);
    }
    return pathList;
  }

  public void depthFirstSearch(boolean forward, StringBuilder b) {
    if (forward) {
      depthFirstSearchHelper(getStartNode(), new HashSet(), 0, true, b);
    } else {
      for (Object o : getEndNodes()) {
        depthFirstSearchHelper(o, new HashSet(), 0, false, b);
      }
    }
  }

  /**
   * For testing only.
   */
  private void depthFirstSearchHelper(Object node, Set marked, int level, boolean forward, StringBuilder b) {
    if (marked.contains(node)) {
      return;
    }
    marked.add(node);
    Set<Arc> arcs;
    if (forward) {
      arcs = this.getArcsBySource(node);
    } else {
      arcs = this.getArcsByTarget(node);
    }
    if (arcs == null) {
      return;
    }
    for (Arc newArc : arcs) {
      // print it out
      for (int i = 0; i < level; i++) {
        b.append("  ");
      }
      if (getEndNodes().contains(newArc.getTargetNode())) {
        b.append(newArc + " END\n");
      } else {
        b.append(newArc + "\n");
      }
      if (forward) {
        depthFirstSearchHelper(newArc.getTargetNode(), marked, level + 1, forward, b);
      } else {
        depthFirstSearchHelper(newArc.getSourceNode(), marked, level + 1, forward, b);
      }
    }
  }

  /**
   * For testing only.
   */
  public static void main(String[] args) {
    List pathList = new ArrayList();
    TransducerGraph graph = createRandomGraph(1000, 10, 0.0, 10, pathList);
    System.out.println("Done creating random graph");
    printPathOutputs(pathList, graph, true);
    System.out.println("Depth first search from start node");
    StringBuilder b = new StringBuilder();
    graph.depthFirstSearch(true, b);
    System.out.println(b.toString());
    b = new StringBuilder();
    System.out.println("Depth first search back from end node");
    graph.depthFirstSearch(false, b);
    System.out.println(b.toString());
  }

}

