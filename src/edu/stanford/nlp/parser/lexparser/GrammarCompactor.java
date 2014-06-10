package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.fsm.TransducerGraph;
import edu.stanford.nlp.fsm.TransducerGraph.Arc;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public abstract class GrammarCompactor {

  // so that the grammar remembers its graphs after compacting them
  Set<TransducerGraph> compactedGraphs;

  public static final Object RAW_COUNTS = new Object();
  public static final Object NORMALIZED_LOG_PROBABILITIES = new Object();

  public Object outputType = RAW_COUNTS; // default value

  protected Index<String> stateIndex;
  protected Index<String> newStateIndex;

  // String rawBaseDir = "raw";
  // String compactedBaseDir = "compacted";
  // boolean writeToFile = false;
  protected Distribution<String> inputPrior;
  private static final String END = "END";
  private static final String EPSILON = "EPSILON";
  protected boolean verbose = false;

  protected final Options op;

  public GrammarCompactor(Options op) {
    this.op = op;
  }


  protected abstract TransducerGraph doCompaction(TransducerGraph graph, List<List<String>> trainPaths, List<List<String>> testPaths);

  public Triple<Index<String>, UnaryGrammar, BinaryGrammar> compactGrammar(Pair<UnaryGrammar,BinaryGrammar> grammar, Index<String> originalStateIndex) {
    return compactGrammar(grammar, new HashMap<String, List<List<String>>>(), new HashMap<String, List<List<String>>>(), originalStateIndex);
  }

  /**
   * Compacts the grammar specified by the Pair.
   *
   * @param grammar       a Pair of grammars, ordered UnaryGrammar BinaryGrammar.
   * @param allTrainPaths a Map from String passive constituents to Lists of paths
   * @param allTestPaths  a Map from String passive constituents to Lists of paths
   * @return a Pair of grammars, ordered UnaryGrammar BinaryGrammar.
   */
  public Triple<Index<String>, UnaryGrammar, BinaryGrammar> compactGrammar(Pair<UnaryGrammar,BinaryGrammar> grammar, Map<String, List<List<String>>> allTrainPaths, Map<String, List<List<String>>> allTestPaths, Index<String> originalStateIndex) {
    inputPrior = computeInputPrior(allTrainPaths); // computed once for the whole grammar
    // BinaryGrammar bg = grammar.second;
    this.stateIndex = originalStateIndex;
    List<List<String>> trainPaths, testPaths;
    Set<UnaryRule> unaryRules = new HashSet<UnaryRule>();
    Set<BinaryRule> binaryRules = new HashSet<BinaryRule>();
    Map<String, TransducerGraph> graphs = convertGrammarToGraphs(grammar, unaryRules, binaryRules);
    compactedGraphs = new HashSet<TransducerGraph>();
    if (verbose) {
      System.out.println("There are " + graphs.size() + " categories to compact.");
    }
    int i = 0;
    for (Iterator<Entry<String, TransducerGraph>> graphIter = graphs.entrySet().iterator(); graphIter.hasNext();) {
      Map.Entry<String, TransducerGraph> entry = graphIter.next();
      String cat = entry.getKey();
      TransducerGraph graph = entry.getValue();
      if (verbose) {
        System.out.println("About to compact grammar for " + cat + " with numNodes=" + graph.getNodes().size());
      }
      trainPaths = allTrainPaths.remove(cat);// to save memory
      if (trainPaths == null) {
        trainPaths = new ArrayList<List<String>>();
      }
      testPaths = allTestPaths.remove(cat);// to save memory
      if (testPaths == null) {
        testPaths = new ArrayList<List<String>>();
      }
      TransducerGraph compactedGraph = doCompaction(graph, trainPaths, testPaths);
      i++;
      if (verbose) {
        System.out.println(i + ". Compacted grammar for " + cat + " from " + graph.getArcs().size() + " arcs to " + compactedGraph.getArcs().size() + " arcs.");
      }
      graphIter.remove(); // to save memory, remove the last thing
      compactedGraphs.add(compactedGraph);
    }
    Pair<UnaryGrammar, BinaryGrammar> ugbg = convertGraphsToGrammar(compactedGraphs, unaryRules, binaryRules);
    return new Triple<Index<String>, UnaryGrammar, BinaryGrammar>(newStateIndex, ugbg.first(), ugbg.second());
  }

  protected static Distribution<String> computeInputPrior(Map<String, List<List<String>>> allTrainPaths) {
    ClassicCounter<String> result = new ClassicCounter<String>();
    for (List<List<String>> pathList : allTrainPaths.values()) {
      for (List<String> path : pathList) {
        for (String input : path) {
          result.incrementCount(input);
        }
      }
    }
    return Distribution.laplaceSmoothedDistribution(result, result.size() * 2, 0.5);
  }

  private double smartNegate(double output) {
    if (outputType == NORMALIZED_LOG_PROBABILITIES) {
      return -output;
    }
    return output;
  }

  public static boolean writeFile(TransducerGraph graph, String dir, String name) {
    try {
      File baseDir = new File(dir);
      if (baseDir.exists()) {
        if (!baseDir.isDirectory()) {
          return false;
        }
      } else {
        if (!baseDir.mkdirs()) {
          return false;
        }
      }
      File file = new File(baseDir, name + ".dot");
      PrintWriter w;
      try {
        w = new PrintWriter(new FileWriter(file));
        String dotString = graph.asDOTString();
        w.print(dotString);
        w.flush();
        w.close();
      } catch (FileNotFoundException e) {
        System.err.println("Failed to open file in writeToDOTfile: " + file);
        return false;
      } catch (IOException e) {
        System.err.println("Failed to open file in writeToDOTfile: " + file);
        return false;
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   *
   */
  protected Map<String, TransducerGraph> convertGrammarToGraphs(Pair<UnaryGrammar,BinaryGrammar> grammar, Set<UnaryRule> unaryRules, Set<BinaryRule> binaryRules) {
    int numRules = 0;
    UnaryGrammar ug = grammar.first;
    BinaryGrammar bg = grammar.second;
    Map<String, TransducerGraph> graphs = new HashMap<String, TransducerGraph>();
    // go through the BinaryGrammar and add everything
    for (BinaryRule rule : bg) {
      numRules++;
      boolean wasAdded = addOneBinaryRule(rule, graphs);
      if (!wasAdded)
      // add it for later, since we don't make graphs for these
      {
        binaryRules.add(rule);
      }
    }
    // now we need to use the UnaryGrammar to
    // add start and end Arcs to the graphs
    for (UnaryRule rule : ug) {
      numRules++;
      boolean wasAdded = addOneUnaryRule(rule, graphs);
      if (!wasAdded)
      // add it for later, since we don't make graphs for these
      {
        unaryRules.add(rule);
      }
    }
    if (verbose) {
      System.out.println("Number of raw rules: " + numRules);
      System.out.println("Number of raw states: " + stateIndex.size());
    }
    return graphs;
  }

  protected static TransducerGraph getGraphFromMap(Map<String, TransducerGraph> m, String o) {
    TransducerGraph graph = m.get(o);
    if (graph == null) {
      graph = new TransducerGraph();
      graph.setEndNode(o);
      m.put(o, graph);
    }
    return graph;
  }

  protected static String getTopCategoryOfSyntheticState(String s) {
    if (s.charAt(0) != '@') {
      return null;
    }
    int bar = s.indexOf('|');
    if (bar < 0) {
      throw new RuntimeException("Grammar format error. Expected bar in state name: " + s);
    }
    String topcat = s.substring(1, bar);
    return topcat;
  }

  protected boolean addOneUnaryRule(UnaryRule rule, Map<String, TransducerGraph> graphs) {
    String parentString = stateIndex.get(rule.parent);
    String childString = stateIndex.get(rule.child);
    if (isSyntheticState(parentString)) {
      String topcat = getTopCategoryOfSyntheticState(parentString);
      TransducerGraph graph = getGraphFromMap(graphs, topcat);
      Double output = new Double(smartNegate(rule.score()));
      graph.addArc(graph.getStartNode(), parentString, childString, output);
      return true;
    } else if (isSyntheticState(childString)) {
      // need to add Arc from synthetic state to endState
      TransducerGraph graph = getGraphFromMap(graphs, parentString);
      Double output = new Double(smartNegate(rule.score()));
      graph.addArc(childString, parentString, END, output); // parentString should the the same as endState
      graph.setEndNode(parentString);
      return true;
    } else {
      return false;
    }
  }

  protected boolean addOneBinaryRule(BinaryRule rule, Map<String, TransducerGraph> graphs) {
    // parent has to be synthetic in BinaryRule
    String parentString = stateIndex.get(rule.parent);
    String leftString = stateIndex.get(rule.leftChild);
    String rightString = stateIndex.get(rule.rightChild);
    String source, target, input;
    String bracket = null;
    if (op.trainOptions.markFinalStates) {
      bracket = parentString.substring(parentString.length() - 1, parentString.length());
    }
    // the below test is not necessary with left to right grammars
    if (isSyntheticState(leftString)) {
      source = leftString;
      input = rightString + (bracket == null ? ">" : bracket);
    } else if (isSyntheticState(rightString)) {
      source = rightString;
      input = leftString + (bracket == null ? "<" : bracket);
    } else {
      // we don't know what to do with this rule
      return false;
    }
    target = parentString;
    Double output = new Double(smartNegate(rule.score())); // makes it a real  0 <= k <= infty
    String topcat = getTopCategoryOfSyntheticState(source);
    if (topcat == null) {
      throw new RuntimeException("can't have null topcat");
    }
    TransducerGraph graph = getGraphFromMap(graphs, topcat);
    graph.addArc(source, target, input, output);
    return true;
  }

  protected static boolean isSyntheticState(String state) {
    return state.charAt(0) == '@';
  }


  /**
   * @param graphs      a Map from String categories to TransducerGraph objects
   * @param unaryRules  is a Set of UnaryRule objects that we need to add
   * @param binaryRules is a Set of BinaryRule objects that we need to add
   * @return a new Pair of UnaryGrammar, BinaryGrammar
   */
  protected Pair<UnaryGrammar,BinaryGrammar> convertGraphsToGrammar(Set<TransducerGraph> graphs, Set<UnaryRule> unaryRules, Set<BinaryRule> binaryRules) {
    // first go through all the existing rules and number them with new numberer
    newStateIndex = new HashIndex<String>();
    for (UnaryRule rule : unaryRules) {
      String parent = stateIndex.get(rule.parent);
      rule.parent = newStateIndex.indexOf(parent, true);
      String child = stateIndex.get(rule.child);
      rule.child = newStateIndex.indexOf(child, true);
    }
    for (BinaryRule rule : binaryRules) {
      String parent = stateIndex.get(rule.parent);
      rule.parent = newStateIndex.indexOf(parent, true);
      String leftChild = stateIndex.get(rule.leftChild);
      rule.leftChild = newStateIndex.indexOf(leftChild, true);
      String rightChild = stateIndex.get(rule.rightChild);
      rule.rightChild = newStateIndex.indexOf(rightChild, true);
    }

    // now go through the graphs and add the rules
    for (TransducerGraph graph : graphs) {
      Object startNode = graph.getStartNode();
      for (Arc arc : graph.getArcs()) {
        // TODO: make sure these are the strings we're looking for
        String source = arc.getSourceNode().toString();
        String target = arc.getTargetNode().toString();
        Object input = arc.getInput();
        String inputString = input.toString();
        double output = ((Double) arc.getOutput()).doubleValue();
        if (source.equals(startNode)) {
          // make a UnaryRule
          UnaryRule ur = new UnaryRule(newStateIndex.indexOf(target, true), newStateIndex.indexOf(inputString, true), smartNegate(output));
          unaryRules.add(ur);
        } else if (inputString.equals(END) || inputString.equals(EPSILON)) {
          // make a UnaryRule
          UnaryRule ur = new UnaryRule(newStateIndex.indexOf(target, true), newStateIndex.indexOf(source, true), smartNegate(output));
          unaryRules.add(ur);
        } else {
          // make a BinaryRule
          // figure out whether the input was generated on the left or right
          int length = inputString.length();
          char leftOrRight = inputString.charAt(length - 1);
          inputString = inputString.substring(0, length - 1);
          BinaryRule br;
          if (leftOrRight == '<' || leftOrRight == '[') {
            br = new BinaryRule(newStateIndex.indexOf(target, true), newStateIndex.indexOf(inputString, true), newStateIndex.indexOf(source, true), smartNegate(output));
          } else if (leftOrRight == '>' || leftOrRight == ']') {
            br = new BinaryRule(newStateIndex.indexOf(target, true), newStateIndex.indexOf(source, true), newStateIndex.indexOf(inputString, true), smartNegate(output));
          } else {
            throw new RuntimeException("Arc input is in unexpected format: " + arc);
          }
          binaryRules.add(br);
        }
      }
    }
    // by now, the unaryRules and binaryRules Sets have old untouched and new rules with scores
    ClassicCounter<String> symbolCounter = new ClassicCounter<String>();
    if (outputType == RAW_COUNTS) {
      // now we take the sets of rules and turn them into grammars
      // the scores of the rules we are given are actually counts
      // so we count parent symbol occurrences
      for (UnaryRule rule : unaryRules) {
        symbolCounter.incrementCount(newStateIndex.get(rule.parent), rule.score);
      }
      for (BinaryRule rule : binaryRules) {
        symbolCounter.incrementCount(newStateIndex.get(rule.parent), rule.score);
      }
    }
    // now we put the rules in the grammars
    int numStates = newStateIndex.size();     // this should be smaller than last one
    int numRules = 0;
    UnaryGrammar ug = new UnaryGrammar(newStateIndex);
    BinaryGrammar bg = new BinaryGrammar(newStateIndex);
    for (UnaryRule rule : unaryRules) {
      if (outputType == RAW_COUNTS) {
        double count = symbolCounter.getCount(newStateIndex.get(rule.parent));
        rule.score = (float) Math.log(rule.score / count);
      }
      ug.addRule(rule);
      numRules++;
    }
    for (BinaryRule rule : binaryRules) {
      if (outputType == RAW_COUNTS) {
        double count = symbolCounter.getCount(newStateIndex.get(rule.parent));
        rule.score = (float) Math.log((rule.score - op.trainOptions.ruleDiscount) / count);
      }
      bg.addRule(rule);
      numRules++;
    }
    if (verbose) {
      System.out.println("Number of minimized rules: " + numRules);
      System.out.println("Number of minimized states: " + newStateIndex.size());
    }

    ug.purgeRules();
    bg.splitRules();
    return new Pair<UnaryGrammar,BinaryGrammar>(ug, bg);
  }

}
