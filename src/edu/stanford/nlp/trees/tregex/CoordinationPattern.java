package edu.stanford.nlp.trees.tregex;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;

import java.util.Iterator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

class CoordinationPattern extends TregexPattern {

  private final boolean isConj;
  private final List<TregexPattern> children;
  private final boolean changesVariables;

  /* if isConj is true, then it is an "AND" ; if it is false, it is an "OR".*/
  public CoordinationPattern(List<TregexPattern> children, boolean isConj) {
    if (children.size() < 2) {
      throw new RuntimeException("Coordination node must have at least 2 children.");
    }
    this.children = children;
    this.isConj = isConj;
    boolean changesVars = false;
    for (TregexPattern child : children) {
      if (child.getChangesVariables()) {
        changesVars = true;
      }
    }
    this.changesVariables = changesVars;
    //    System.out.println("Made " + (isConj ? "and " : "or ") + "node with " + children.size() + " children.");
  }

  @Override
  public List<TregexPattern> getChildren() {
    return children;
  }

  @Override
  public String localString() {
    return (isConj ? "and" : "or");
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (isConj) {
      if (isNegated()) {
        sb.append("!(");
      }
      for (TregexPattern node : children) {
        sb.append(node.toString());
      }
      if (isNegated()) {
        sb.append(")");
      }
    } else {
      if (isNegated()) {
        sb.append("!");
      }
      sb.append('[');
      for (Iterator<TregexPattern> iter = children.iterator(); iter.hasNext();) {
        TregexPattern node = iter.next();
        sb.append(node.toString());
        if (iter.hasNext()) {
          sb.append(" |");
        }
      }
      sb.append(']');
    }
    return sb.toString();
  }

  @Override
  boolean getChangesVariables() {
    return changesVariables;
  }

  @Override
  public TregexMatcher matcher(Tree root, Tree tree,
                               IdentityHashMap<Tree, Tree> nodesToParents,
                               Map<String, Tree> namesToNodes,
                               VariableStrings variableStrings,
                               HeadFinder headFinder) {
    return new CoordinationMatcher(this, root, tree, nodesToParents, namesToNodes, variableStrings, headFinder);
  }

  private static class CoordinationMatcher extends TregexMatcher {
    private TregexMatcher[] children;
    private final CoordinationPattern myNode;
    private int currChild;
    private final boolean considerAll;
    // do all con/dis-juncts have to be considered to determine a match?
    // i.e. true if conj and not negated or disj and negated

    public CoordinationMatcher(CoordinationPattern n, Tree root, Tree tree,
                               IdentityHashMap<Tree, Tree> nodesToParents,
                               Map<String, Tree> namesToNodes,
                               VariableStrings variableStrings, 
                               HeadFinder headFinder) {
      super(root, tree, nodesToParents, namesToNodes, variableStrings, headFinder);
      myNode = n;
      children = new TregexMatcher[myNode.children.size()];
      // lazy initialize the children... don't set children[i] yet

      //for (int i = 0; i < children.length; i++) {
      //  TregexPattern node = myNode.children.get(i);
      //  children[i] = node.matcher(root, tree, nodesToParents,
      //                             namesToNodes, variableStrings);
      //}
      currChild = 0;
      considerAll = myNode.isConj ^ myNode.isNegated();
    }

    @Override
    void resetChildIter() {
      currChild = 0;
      for (TregexMatcher child : children) {
        if (child != null) {
          child.resetChildIter();
        }
      }
    }

    @Override
    void resetChildIter(Tree tree) {
      this.tree = tree;
      currChild = 0;
      for (TregexMatcher child : children) {
        if (child != null) {
          child.resetChildIter(tree);
        }
      }
    }

    @Override
    boolean getChangesVariables() {
      return myNode.getChangesVariables();
    }

    // find the next local match
    @Override
    public boolean matches() {  // also known as "FUN WITH LOGIC"
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
          if (children[currChild] == null) {
            children[currChild] = myNode.children.get(currChild).matcher(root, tree, nodesToParents, namesToNodes, variableStrings, headFinder);
            children[currChild].resetChildIter(tree);
          }
          if (myNode.isNegated() != children[currChild].matches()) {
            // This node is set correctly.  Move on to the next node
            ++currChild;

            if (currChild == children.length) {
              // yay, all nodes matched.
              if (myNode.isNegated()) {
                // a negated node should only match once (before being reset)
                currChild = -1;
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
      } else {
        // these are the cases where a single child node can make you match
        for (; currChild < children.length; currChild++) {
          if (children[currChild] == null) {
            children[currChild] = myNode.children.get(currChild).matcher(root, tree, nodesToParents, namesToNodes, variableStrings, headFinder);
            children[currChild].resetChildIter(tree);
          }
          if (myNode.isNegated() != children[currChild].matches()) {
            // a negated node should only match once (before being reset)
            // otherwise you get repeated matches for every node that
            // causes the negated match to pass, which would be silly
            if (myNode.isNegated()) {
              currChild = children.length;
            }
            return true;
          }
        }
        if (myNode.isNegated()) {
          currChild = children.length;
        }
        for (int resetChild = 0; resetChild < currChild; ++resetChild) {
          // clean up variables that may have been set in previously
          // accepted nodes
          if (children[resetChild] != null) {
            children[resetChild].resetChildIter();
          }
        }
        return myNode.isOptional();
      }
    }

    @Override
    public Tree getMatch() {
      // in general, only DescriptionNodes can match
      // exception: if we are a positive disjunction, we care about
      // exactly one of the children, so we return its match
      if (!myNode.isConj && !myNode.isNegated()) {
        if (currChild >= children.length || currChild < 0 || children[currChild] == null) {
          return null;
        } else {
          return children[currChild].getMatch();
        }
      } else {
        throw new UnsupportedOperationException();
      }
    }
  } // end private class CoordinationMatcher

  private static final long serialVersionUID = -7797084959452603087L;

}
