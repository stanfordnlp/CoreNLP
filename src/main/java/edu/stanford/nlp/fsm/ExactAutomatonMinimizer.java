package edu.stanford.nlp.fsm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.fsm.TransducerGraph.Arc;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Maps;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

/**
 * Minimization of a FA in n log n a la Hopcroft.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public class ExactAutomatonMinimizer implements AutomatonMinimizer {

  private TransducerGraph unminimizedFA = null;
  private Map<Arc, ExactBlock<Arc>> memberToBlock = null;
  private LinkedList<Pair<ExactBlock<Arc>, Arc>> activePairs = null;

  private boolean sparseMode = false;

  private static final Arc SINK_NODE = new Arc(null);


  protected TransducerGraph getUnminimizedFA() {
    return unminimizedFA;
  }

  protected Collection<?> getSymbols() {
    return getUnminimizedFA().getInputs();
  }

  public TransducerGraph minimizeFA(TransducerGraph unminimizedFA) {
    this.unminimizedFA = unminimizedFA;
    this.activePairs = Generics.newLinkedList();
    this.memberToBlock = Generics.newHashMap();
    minimize();
    return buildMinimizedFA();
  }

  protected TransducerGraph buildMinimizedFA() {
    TransducerGraph minimizedFA = new TransducerGraph();
    TransducerGraph unminimizedFA = getUnminimizedFA();
    for (TransducerGraph.Arc arc : unminimizedFA.getArcs()) {
      Set<Arc> source = projectNode(arc.getSourceNode());
      Set<Arc> target = projectNode(arc.getTargetNode());
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

  protected Set<Arc> projectNode(Object node) {
    return getBlock(node).getMembers();
  }


  protected boolean hasActivePair() {
    return activePairs.size() > 0;
  }

  protected Pair<ExactBlock<Arc>, ?> getActivePair() {
    return activePairs.removeFirst();
  }

  protected void addActivePair(Pair<ExactBlock<Arc>, Arc> pair) {
    activePairs.addLast(pair);
  }

  //  protected Collection inverseImages(Collection block, Object symbol) {
  //    List inverseImages = new ArrayList();
  //    for (Iterator nodeI = block.iterator(); nodeI.hasNext();) {
  //      Object node = nodeI.next();
  //      inverseImages.addAll(getUnminimizedFA().getInboundArcs(node, symbol));
  //    }
  //    return inverseImages;
  //  }

  protected <Y> Map<ExactBlock<Arc>, Set<Y>> sortIntoBlocks(Collection<Y> nodes) {
    Map<ExactBlock<Arc>, Set<Y>> blockToMembers = Generics.newHashMap(); // IdentityHashMap();
    for (Y o : nodes) {
      ExactBlock<Arc> block = getBlock(o);
      if (block == null) {
        throw new RuntimeException("got null block");
      }
      Maps.putIntoValueHashSet(blockToMembers, block, o);
    }
    return blockToMembers;
  }

  protected void makeBlock(Collection<Arc> members) {
    ExactBlock<Arc> block = new ExactBlock<>(Generics.newHashSet(members));
    for (Arc member : block.getMembers()) {
      if (member != SINK_NODE) {
        memberToBlock.put(member, block);
      }
    }
    for (Object o : getSymbols()) {
      Arc symbol = (Arc) o;
      addActivePair(new Pair<>(block, symbol));
    }
  }

  protected static void removeAll(Collection<? extends Arc> block, Collection members) {
    // this is because AbstractCollection/Set.removeAll() isn't always linear in members.size()
    for (Object member : members) {
      block.remove(member);
    }
  }

  protected static Collection<Arc> difference(Collection<Arc> block, Collection<Arc> members) {
    Set<Arc> difference = Generics.newHashSet();
    for (Arc member : block) {
      if (!members.contains(member)) {
        difference.add(member);
      }
    }
    return difference;
  }

  protected ExactBlock<Arc> getBlock(Object o) {
    ExactBlock<Arc> result = memberToBlock.get(o);
    if (result == null) {
      throw new RuntimeException("memberToBlock had null block");
    }
    return result;
  }

  protected Collection<Object> getInverseImages(ExactBlock<Arc> block, Object symbol) {
    List<Object> inverseImages = new ArrayList<>();
    for (Arc member : block.getMembers()) {
      Collection<Arc> arcs = null;
      if (member != SINK_NODE) {
        arcs = getUnminimizedFA().getArcsByTargetAndInput(member, symbol);
      } else {
        arcs = getUnminimizedFA().getArcsByInput(symbol);
        if (!sparseMode) {
          arcs = difference(getUnminimizedFA().getArcs(), arcs);
        }
      }
      if (arcs == null) {
        continue;
      }
      for (Arc arc : arcs) {
        Object source = arc.getSourceNode();
        inverseImages.add(source);
      }
    }
    return inverseImages;
  }

  protected void makeInitialBlocks() {
    // sink block (for if the automaton isn't complete
    makeBlock(Collections.singleton(SINK_NODE));
    // accepting block
    Set<Arc> endNodes = getUnminimizedFA().getEndNodes();
    makeBlock(endNodes);
    // main block
    Collection<Arc> nonFinalNodes = Generics.newHashSet(getUnminimizedFA().getNodes());
    nonFinalNodes.removeAll(endNodes);
    makeBlock(nonFinalNodes);
  }

  protected void minimize() {
    makeInitialBlocks();
    while (hasActivePair()) {
      Pair<ExactBlock<Arc>, ?> activePair = getActivePair();
      ExactBlock<Arc> activeBlock = activePair.first();
      Object symbol = activePair.second();
      Collection<Object> inverseImages = getInverseImages(activeBlock, symbol);
      Map<ExactBlock<Arc>, Set<Object>> inverseImagesByBlock = sortIntoBlocks(inverseImages);
      for (ExactBlock<Arc> block : inverseImagesByBlock.keySet()) {
        if (block == null) {
          throw new RuntimeException("block was null");
        }
        Collection members = inverseImagesByBlock.get(block);
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

  public ExactAutomatonMinimizer(boolean sparseMode) {
    this.sparseMode = sparseMode;
  }

  public ExactAutomatonMinimizer() {
    this(false);
  }


  private static class ExactBlock<E> implements Block<E> {

    private final Set<E> members;

    public Set<E> getMembers() {
      return members;
    }

    public ExactBlock(Set<E> members) {
      if (members == null) {
        throw new IllegalArgumentException("tried to create block with null members.");
      }
      this.members = members;
    }

    @Override
    public String toString() {
      return "Block: " + members.toString();
    }

  } // end static class ExactBlock


  public static void main(String[] args) {
    TransducerGraph fa = new TransducerGraph();
    fa.addArc(fa.getStartNode(), "1", "a", "");
    fa.addArc(fa.getStartNode(), "2", "b", "");
    fa.addArc(fa.getStartNode(), "3", "c", "");
    fa.addArc("1", "4", "a", "");
    fa.addArc("2", "4", "a", "");
    fa.addArc("3", "5", "c", "");
    fa.addArc("4", "6", "c", "");
    fa.addArc("5", "6", "c", "");
    fa.setEndNode("6");
    System.out.println(fa);
    ExactAutomatonMinimizer minimizer = new ExactAutomatonMinimizer();
    System.out.println(minimizer.minimizeFA(fa));
    System.out.println("Starting...");
    Timing.startTime();
    TransducerGraph randomFA = TransducerGraph.createRandomGraph(100, 10, 1.0, 10, new ArrayList<>());
    TransducerGraph minimizedRandomFA = minimizer.minimizeFA(randomFA);
    System.out.println(randomFA);
    System.out.println(minimizedRandomFA);
    Timing.tick("done. ( " + randomFA.getArcs().size() + " arcs to " + minimizedRandomFA.getArcs().size() + " arcs)");
  }

}
