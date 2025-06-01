package edu.stanford.nlp.trees.tregex;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.VariableStrings;

import java.util.Iterator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

class CoordinationPattern extends TregexPattern {

  private final boolean isConj;
  private final List<TregexPattern> children;

  /* if isConj is true, then it is an "AND" ; if it is false, it is an "OR".*/
  public CoordinationPattern(List<TregexPattern> children, boolean isConj) {
    if (children.size() < 2) {
      throw new RuntimeException("Coordination node must have at least 2 children.");
    }
    this.children = children;
    this.isConj = isConj;
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

    if (isNegated() && isOptional()) {
      throw new AssertionError("Shenanigans!  The parser should not allow a pattern which is both negated and optional");
    }

    if (isConj) {
      if (isNegated()) {
        sb.append("!(");
      }
      if (isOptional()) {
        sb.append("?(");
      }
      for (TregexPattern node : children) {
        sb.append(node.toString());
      }
      if (isNegated() || isOptional()) {
        sb.append(")");
      }
    } else {
      if (isNegated()) {
        sb.append("!");
      }
      if (isOptional()) {
        sb.append("?");
      }
      sb.append('[');
      for (Iterator<TregexPattern> iter = children.iterator(); iter.hasNext();) {
        TregexPattern node = iter.next();
        sb.append(node.toString());
        if (iter.hasNext()) {
          sb.append(" | ");
        }
      }
      sb.append(']');
    }
    return sb.toString();
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
    boolean isReset() {
      // if we partially or completely went through the node,
      // we are obviously not reset
      if (currChild != 0) {
        return false;
      }
      // if we're at the start, and the first child is not
      // initialized, we haven't done anything yet
      if (children[0] == null) {
        return true;
      }
      // otherwise, we may have initialized the child
      // on a previous time through, then reset it
      return (children[0].isReset());
    }

    // find the next local match
    @Override
    public boolean matches() {  // also known as "FUN WITH LOGIC"
      if (considerAll) {
        // these are the cases where all children must be considered to match
        if (currChild < 0) {
          // A past call to this node either got that it failed
          // matching or that it was a negative match that succeeded,
          // which we only want to accept once
          // Note that in the case of isOptional nodes, we want to NOT
          // match again.  The previous time through failed to match,
          // but was already returned as true because of isOptional().
          // If we match again, it will infinite loop because it
          // keeps "succeeding" at the optional match
          return false;
        }

        // we must have happily reached the end of a match the last
        // time we were here
        // we track pastSuccess so that if we reach a failure in
        // an optional node after already succeeding, we don't return
        // another success, which would be a spurious extra match
        final boolean pastSuccess;
        if (currChild == children.length) {
          --currChild;
          pastSuccess = true;
        } else {
          pastSuccess = false;
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
          } else if (!myNode.isNegated()) {
            // oops, this didn't work - positive conjunction version
            children[currChild].resetChildIter();
            // go backwards to see if we can continue matching from an
            // earlier location.
            --currChild;
            if (currChild < 0) {
              return myNode.isOptional() && !pastSuccess;
            }
          } else {
            // oops, this didn't work - negated disjunction version
            // here we just fail
            // any previous children had to fail to get to this point,
            // which means those children have only the one correct state.
            // backtracking to find other correct states is pointless
            // and in fact causes an infinite loop of going backwards,
            // then advancing back to this child and failing again
            currChild = -1;
            return myNode.isOptional();
          }
        }
      } else {
        // Track the first time through this loop
        // This will let us handle optional disjunctions
        // Note that we can't just check currChild, since it might be 0
        // for a match that already hit once on the first child
        // We also can't check that children[0] is not created, since
        // it might be created and then later reset if this node is
        // reached a second time after something higher in the tree
        // already matched
        final boolean firstTime = isReset();

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
        // only accept an optional disjunction if this is the first time through
        // otherwise, we'd be accepting the same disjunction over and over
        return firstTime && myNode.isOptional();
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
