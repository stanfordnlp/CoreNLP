package edu.stanford.nlp.fsm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.fsm.TransducerGraph.Arc;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.util.Maps;

/**
 * Minimization in n log n a la Hopcroft.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public class FastExactAutomatonMinimizer implements AutomatonMinimizer {
  TransducerGraph unminimizedFA = null;
  Map memberToBlock = null;
  LinkedList splits = null;

  boolean sparseMode = true;

  static final Object SINK_NODE = "SINK_NODE";

  static class Split {
    Collection members;
    Object symbol;
    Block block;

    public Collection getMembers() {
      return members;
    }

    public Object getSymbol() {
      return symbol;
    }

    public Block getBlock() {
      return block;
    }

    public Split(Collection members, Object symbol, Block block) {
      this.members = members;
      this.symbol = symbol;
      this.block = block;
    }
  }

  static class Block {
    Set members;

    public Set getMembers() {
      return members;
    }

    public Block(Set members) {
      this.members = members;
    }
  }

  protected TransducerGraph getUnminimizedFA() {
    return unminimizedFA;
  }

  protected Collection getSymbols() {
    return getUnminimizedFA().getInputs();
  }

  public TransducerGraph minimizeFA(TransducerGraph unminimizedFA) {
    //    System.out.println(unminimizedFA);
    this.unminimizedFA = unminimizedFA;
    this.splits = new LinkedList();
    this.memberToBlock = new HashMap(); //new IdentityHashMap(); // TEG: I had to change this b/c some weren't matching
    minimize();
    return buildMinimizedFA();
  }

  protected TransducerGraph buildMinimizedFA() {
    TransducerGraph minimizedFA = new TransducerGraph();
    TransducerGraph unminimizedFA = getUnminimizedFA();
    for (TransducerGraph.Arc arc : unminimizedFA.getArcs()) {
      Object source = projectNode(arc.getSourceNode());
      Object target = projectNode(arc.getTargetNode());
      try {
        if (minimizedFA.canAddArc(source, target, arc.getInput(), arc.getOutput())) {
          minimizedFA.addArc(source, target, arc.getInput(), arc.getOutput());
        }
      } catch (Exception e) {
        //throw new IllegalArgumentException();
      }
    }
    minimizedFA.setStartNode(projectNode(unminimizedFA.getStartNode()));
    for (Object o : unminimizedFA.getEndNodes()) {
      minimizedFA.setEndNode(projectNode(o));
    }

    return minimizedFA;
  }

  protected Object projectNode(Object node) {
    Set members = getBlock(node).getMembers();
    return members;
  }


  protected boolean hasSplit() {
    return splits.size() > 0;
  }

  protected Split getSplit() {
    return (Split) splits.removeFirst();
  }

  protected void addSplit(Split split) {
    splits.addLast(split);
  }

  //  protected Collection inverseImages(Collection block, Object symbol) {
  //    List inverseImages = new ArrayList();
  //    for (Iterator nodeI = block.iterator(); nodeI.hasNext();) {
  //      Object node = nodeI.next();
  //      inverseImages.addAll(getUnminimizedFA().getInboundArcs(node, symbol));
  //    }
  //    return inverseImages;
  //  }

  protected Map sortIntoBlocks(Collection nodes) {
    Map blockToMembers = new IdentityHashMap();
    for (Object o : nodes) {
      Block block = getBlock(o);
      Maps.putIntoValueHashSet(blockToMembers, block, o);
    }
    return blockToMembers;
  }

  protected void makeBlock(Collection members) {
    Block block = new Block(new HashSet(members));
    for (Object member : block.getMembers()) {
      if (member != SINK_NODE) {
        //        System.out.println("putting in memberToBlock: " + member + " " + block);
        memberToBlock.put(member, block);
      }
    }
    addSplits(block);
  }

  protected void addSplits(Block block) {
    Map symbolToTarget = new HashMap();
    for (Object member : block.getMembers()) {
      for (Object o : getInverseArcs(member)) {
        TransducerGraph.Arc arc = (TransducerGraph.Arc) o;
        Object symbol = arc.getInput();
        Object target = arc.getTargetNode();
        Maps.putIntoValueArrayList(symbolToTarget, symbol, target);
      }
    }
    for (Object symbol : symbolToTarget.keySet()) {
      addSplit(new Split((List) symbolToTarget.get(symbol), symbol, block));
    }
  }

  protected void removeAll(Collection block, Collection members) {
    // this is because AbstractCollection/Set.removeAll() isn't always linear in members.size()
    for (Object member : members) {
      block.remove(member);
    }
  }

  protected Collection difference(Collection block, Collection members) {
    Set difference = new HashSet();
    for (Object member : block) {
      if (!members.contains(member)) {
        difference.add(member);
      }
    }
    return difference;
  }

  protected Block getBlock(Object o) {
    Block result = (Block) memberToBlock.get(o);
    if (result == null) {
      System.out.println("No block found for: " + o); // debug
      System.out.println("But I do have blocks for: ");
      for (Object o1 : memberToBlock.keySet()) {
        System.out.println(o1);
      }
      throw new RuntimeException("FastExactAutomatonMinimizer: no block found");
    }
    return result;
  }

  protected Collection getInverseImages(Split split) {
    List<Object> inverseImages = new ArrayList<>();
    Object symbol = split.getSymbol();
    Block block = split.getBlock();
    for (Object member : split.getMembers()) {
      if (!block.getMembers().contains(member)) {
        continue;
      }
      Collection arcs = getInverseArcs(member, symbol);
      for (Object arc1 : arcs) {
        TransducerGraph.Arc arc = (TransducerGraph.Arc) arc1;
        Object source = arc.getSourceNode();
        inverseImages.add(source);
      }
    }
    return inverseImages;
  }

  protected Collection<Arc> getInverseArcs(Object member, Object symbol) {
    if (member != SINK_NODE) {
      return getUnminimizedFA().getArcsByTargetAndInput(member, symbol);
    }
    return getUnminimizedFA().getArcsByInput(symbol);
  }

  protected Collection<Arc> getInverseArcs(Object member) {
    if (member != SINK_NODE) {
      return getUnminimizedFA().getArcsByTarget(member);
    }
    return getUnminimizedFA().getArcs();
  }

  protected void makeInitialBlocks() {
    // sink block (for if the automaton isn't complete
    makeBlock(Collections.singleton(SINK_NODE));
    // accepting block
    Set endNodes = getUnminimizedFA().getEndNodes();
    makeBlock(endNodes);
    // main block
    Collection nonFinalNodes = new HashSet(getUnminimizedFA().getNodes());
    nonFinalNodes.removeAll(endNodes);
    makeBlock(nonFinalNodes);
  }

  protected void minimize() {
    makeInitialBlocks();
    while (hasSplit()) {
      Split split = getSplit();
      Collection inverseImages = getInverseImages(split);
      Map inverseImagesByBlock = sortIntoBlocks(inverseImages);
      for (Object o : inverseImagesByBlock.keySet()) {
        Block block = (Block) o;
        Collection members = (Collection) inverseImagesByBlock.get(block);
        if (members.size() == 0 || members.size() == block.getMembers().size()) {
          continue;
        }
        if (members.size() > block.getMembers().size() - members.size()) {
          members = difference(block.getMembers(), members);
        }
        removeAll(block.getMembers(), members);
        makeBlock(members);
      }
    }
  }

  public static void main(String[] args) {
    /*
    TransducerGraph fa = new TransducerGraph();
    fa.addArc(fa.getStartNode(),"1","a","");
    fa.addArc(fa.getStartNode(),"2","b","");
    fa.addArc(fa.getStartNode(),"3","c","");
    fa.addArc("1","4","a","");
    fa.addArc("2","4","a","");
    fa.addArc("3","5","c","");
    fa.addArc("4",fa.getEndNode(),"c","");
    fa.addArc("5",fa.getEndNode(),"c","");
    System.out.println(fa);
    ExactAutomatonMinimizer minimizer = new ExactAutomatonMinimizer();
    System.out.println(minimizer.minimizeFA(fa));
    */
    System.out.println("Starting minimizer test...");
    List<List<String>> pathList = new ArrayList<>();
    TransducerGraph randomFA = TransducerGraph.createRandomGraph(5000, 5, 1.0, 5, pathList);
    List<Double> outputs = randomFA.getPathOutputs(pathList);

    TransducerGraph.GraphProcessor quasiDeterminizer = new QuasiDeterminizer();
    AutomatonMinimizer minimizer = new FastExactAutomatonMinimizer();
    TransducerGraph.NodeProcessor ntsp = new TransducerGraph.SetToStringNodeProcessor(new PennTreebankLanguagePack());
    TransducerGraph.ArcProcessor isp = new TransducerGraph.InputSplittingProcessor();
    TransducerGraph.ArcProcessor ocp = new TransducerGraph.OutputCombiningProcessor();

    TransducerGraph detGraph = quasiDeterminizer.processGraph(randomFA);
    TransducerGraph combGraph = new TransducerGraph(detGraph, ocp); // combine outputs into inputs
    TransducerGraph result = minimizer.minimizeFA(combGraph); // minimize the thing
    System.out.println("Minimized from " + randomFA.getNodes().size() + " to " + result.getNodes().size());
    result = new TransducerGraph(result, ntsp);  // pull out strings from sets returned by minimizer
    result = new TransducerGraph(result, isp); // split outputs from inputs
    List<Double> minOutputs = result.getPathOutputs(pathList);
    System.out.println("Equal? " + outputs.equals(minOutputs));

    /*
     randomFA = new TransducerGraph(randomFA, new TransducerGraph.OutputCombiningProcessor());
     System.out.print("Starting fast minimization...");
     FastExactAutomatonMinimizer minimizer2 = new FastExactAutomatonMinimizer();
     Timing.startTime();
     TransducerGraph minimizedRandomFA = minimizer2.minimizeFA(randomFA);
     Timing.tick("done. ( "+randomFA.getArcs().size()+" arcs to "+minimizedRandomFA.getArcs().size()+" arcs)");
     minimizedRandomFA = new TransducerGraph(minimizedRandomFA, new TransducerGraph.InputSplittingProcessor());
     List minOutputs = minimizedRandomFA.getPathOutputs(pathList);
     System.out.println("Equal? "+outputs.equals(minOutputs));

     System.out.print("Starting slow minimization...");
     ExactAutomatonMinimizer minimizer = new ExactAutomatonMinimizer();
     Timing.startTime();
     minimizedRandomFA = minimizer.minimizeFA(randomFA);
     Timing.tick("done. ( "+randomFA.getArcs().size()+" arcs to "+minimizedRandomFA.getArcs().size()+" arcs)");
     minimizedRandomFA = new TransducerGraph(minimizedRandomFA, new TransducerGraph.InputSplittingProcessor());
     minOutputs = minimizedRandomFA.getPathOutputs(pathList);
     System.out.println("Equal? "+outputs.equals(minOutputs));
     */
  }
}
