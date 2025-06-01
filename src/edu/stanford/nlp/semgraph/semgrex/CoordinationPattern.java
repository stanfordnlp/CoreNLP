package edu.stanford.nlp.semgraph.semgrex; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.VariableStrings;

/** @author Chloe Kiddon */
public class CoordinationPattern extends SemgrexPattern  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CoordinationPattern.class);

  private static final long serialVersionUID = -3122330899634961002L;
  private final boolean isConj;
  private final boolean isNodeCoord;
  /**
   * Represents whether this is the root coordination.  If so, the
   * children have a higher operation priority, as the : operator is
   * the lowest precedence possible.
   */
  private final boolean isRoot;
  private final List<SemgrexPattern> children;

  /* if isConj is true, then it is an "AND" ; if it is false, it is an "OR".*/
  /* if isNodeCoord is true, then it is a node coordination conj; if it is false, then
           * 	it is a relation coordination conj. */
  public CoordinationPattern(boolean isNodeCoord, List<SemgrexPattern> children, boolean isConj, boolean isRoot) {
    if (children.size() < 2) {
      throw new RuntimeException("Coordination node must have at least 2 children.");
    }
    this.children = Collections.unmodifiableList(children);
    this.isConj = isConj;
    this.isNodeCoord = isNodeCoord;
    this.isRoot = isRoot;
  }

  public boolean isNodeCoord() { return isNodeCoord; }

  @Override
  public void setChild(SemgrexPattern child) {
    if (isNodeCoord) {
      for (Object c : children) {
        if (c instanceof NodePattern)
          ((NodePattern)c).setChild(child);
      }
    } else {

    }
  }

  @Override
  public List<SemgrexPattern> getChildren() {
    return children;
  }

  @Override
  public String localString() {
    StringBuilder sb = new StringBuilder();
    if (isNegated()) {
      sb.append('!');
    }
    if (isOptional()) {
      sb.append('?');
    }
    sb.append((isConj ? "and" : "or"));
    sb.append(" ");
    sb.append((isNodeCoord ? "node coordination" : "reln coordination"));
    return sb.toString();
  }

  @Override
  public String toString() {
    return toString(true);
  }

  @Override
  public String toString(boolean hasPrecedence) {
    StringBuilder sb = new StringBuilder();
    if (isConj) {
      for (SemgrexPattern node : children) {
        // if the children have children, they will need () to represent that
        // exception: if this was a : operation, then no () needed
        sb.append(node.toString(isRoot));
      }
    } else {
      sb.append('[');
      for (Iterator<SemgrexPattern> iter = children.iterator(); iter.hasNext();) {
        SemgrexPattern node = iter.next();
        sb.append(node.toString(isRoot));
        if (iter.hasNext()) {
          sb.append(" |");
        }
      }
      sb.append(']');
    }
    return sb.toString();
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord node,
                                Map<String, IndexedWord> namesToNodes, 
                                Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, 
                                boolean ignoreCase) {
    return new CoordinationMatcher(this, sg, null, null, true, node, 
                                   namesToNodes, namesToRelations, namesToEdges,
                                   variableStrings, ignoreCase);
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, 
                                Alignment alignment, SemanticGraph sg_align,
                                boolean hypToText, IndexedWord node, 
                                Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations, 
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, 
                                boolean ignoreCase) {
    return new CoordinationMatcher(this, sg, alignment, sg_align, 
                                   hypToText, node,
                                   namesToNodes, namesToRelations, namesToEdges,
                                   variableStrings, ignoreCase);
  }


  private static class CoordinationMatcher extends SemgrexMatcher {
    private SemgrexMatcher[] children;
    private final CoordinationPattern myNode;
    private int currChild;
    private final boolean considerAll;
    private IndexedWord nextNodeMatch = null;
    // do all con/dis-juncts have to be considered to determine a match?
    // i.e. true if conj and not negated or disj and negated

    public CoordinationMatcher(CoordinationPattern c, SemanticGraph sg, Alignment alignment,
                               SemanticGraph sg_align, boolean hypToText, IndexedWord n,
                               Map<String, IndexedWord> namesToNodes,
                               Map<String, String> namesToRelations,
                               Map<String, SemanticGraphEdge> namesToEdges,
                               VariableStrings variableStrings,
                               boolean ignoreCase) {
      super(sg, alignment, sg_align, hypToText, n, namesToNodes, namesToRelations, namesToEdges, variableStrings);
      myNode = c;
      children = new SemgrexMatcher[myNode.children.size()];
      for (int i = 0; i < children.length; i++) {
        SemgrexPattern node = myNode.children.get(i);
        children[i] = node.matcher(sg, alignment, sg_align, hypToText,
                                   n, namesToNodes,
                                   namesToRelations, namesToEdges, variableStrings, ignoreCase);
      }
      currChild = 0;
      considerAll = myNode.isConj ^ myNode.isNegated();
    }

    @Override
    void resetChildIter() {
      currChild = 0;
      for (SemgrexMatcher aChildren : children) {
        aChildren.resetChildIter();
      }
      nextNodeMatch = null;
    }

    @Override
    void resetChildIter(IndexedWord node) {
      // this.tree = node;
      currChild = 0;
      for (SemgrexMatcher aChildren : children) {
        aChildren.resetChildIter(node);
      }
    }

    // find the next local match
    @Override
    public boolean matches() {  // also known as "FUN WITH LOGIC"

      //log.info(myNode.toString());
      //log.info("consider all: " + considerAll);
      if (considerAll) {
        // these are the cases where all children must be considered to match
        if (currChild < 0) {
          // a past call to this node either got that it failed
          // matching or that it was a negative match that succeeded,
          // which we only want to accept once
          return myNode.isOptional();
        }

        // we must have happily reached the end of a match the last
        // time we were here
        if (currChild == children.length) {
          --currChild;
        }

        while (true) {
          if (myNode.isNegated() != children[currChild].matches()) {
            // This node is set correctly.  Move on to the next node
            ++currChild;

            if (currChild == children.length) {
              // yay, all nodes matched.
              if (myNode.isNegated()) {
                // a negated node should only match once (before being reset)
                currChild = -1;
              } else if (myNode.isNodeCoord) {
                nextNodeMatch = children[0].getMatch();
              }
              return true;
            }
          } else {
            // oops, this didn't work.
            children[currChild].resetChildIter();
            // go backwards to see if we can continue matching from an
            // earlier location.
            // TODO: perhaps there should be a version where we only
            // care about new assignments to the root, or new
            // assigments to the root and variables, in which case we
            // could make use of getChangesVariables() to optimize how
            // many nodes we can skip past
            --currChild;
            if (currChild < 0) {
              return myNode.isOptional();
            }
          }
        }
      } else { // these are the cases where a single child node can make you match
        for (; currChild < children.length; currChild++) {
          //   	namesToNodes.putAll(namesToNodesOld);
          //    namesToRelations.putAll(namesToRelationsOld);
          if (myNode.isNegated() != children[currChild].matches()) {
            // a negated node should only match once (before being reset)
            if (myNode.isNegated()) {
              currChild = children.length;
            }
            if (myNode.isNodeCoord)
              nextNodeMatch = children[currChild].getMatch();
            //    this.namesToNodes.putAll(children[currChild].namesToNodes);
            //   this.namesToRelations.putAll(children[currChild].namesToRelations);
            return true;
          }
          children[currChild].resetChildIter();
        }
        if (myNode.isNegated()) {
          currChild = children.length;
        }
        return myNode.isOptional();
      }
    }

    @Override
    public IndexedWord getMatch() {
      if (myNode.isNodeCoord && !myNode.isNegated()) {
        return nextNodeMatch;
      } else {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public String toString() {
      String ret = "coordinate matcher for: ";
      for (SemgrexMatcher child : children)
        ret += child.toString() + " ";
      return ret;
    }

  }



}
