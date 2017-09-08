package old.edu.stanford.nlp.trees.tregex;

import old.edu.stanford.nlp.trees.Tree;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

class CoordinationPattern extends TregexPattern {

  private boolean isConj;
  private List<TregexPattern> children;

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
    if (isConj) {
      for (TregexPattern node : children) {
        sb.append(node.toString());
      }
    } else {
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
  public TregexMatcher matcher(Tree root, Tree tree, Map<String, Tree> namesToNodes, VariableStrings variableStrings) {
    return new CoordinationMatcher(this, root, tree, namesToNodes,variableStrings);
  }

  private static class CoordinationMatcher extends TregexMatcher {
    private TregexMatcher[] children;
    private final CoordinationPattern myNode;
    private int currChild;
    private final boolean considerAll;
    // do all con/dis-juncts have to be considered to determine a match?
    // i.e. true if conj and not negated or disj and negated

    public CoordinationMatcher(CoordinationPattern n, Tree root, Tree tree, Map<String, Tree> namesToNodes, VariableStrings variableStrings) {
      super(root, tree, namesToNodes,variableStrings);
      myNode = n;
      children = new TregexMatcher[myNode.children.size()];
      for (int i = 0; i < children.length; i++) {
        TregexPattern node = myNode.children.get(i);
        children[i] = node.matcher(root, tree, namesToNodes,variableStrings);
      }
      currChild = 0;
      considerAll = myNode.isConj ^ myNode.isNegated();
    }

    @Override
    void resetChildIter() {
      currChild = 0;
      for (int i = 0; i < children.length; i++) {
        children[i].resetChildIter();
      }
    }

    @Override
    void resetChildIter(Tree tree) {
      this.tree = tree;
      currChild = 0;
      for (int i = 0; i < children.length; i++) {
        children[i].resetChildIter(tree);
      }
    }

    // find the next local match
    @Override
    public boolean matches() {  // also known as "FUN WITH LOGIC"
      if (considerAll) {
        // these are the cases where all children must be considered to match

        // first iterate backward (if necessary) to get beginning
        // of next possible match
        for (; currChild >= 0; currChild--) {
          if (myNode.isNegated() != children[currChild].matches()) {
            break;
          } else {
            children[currChild].resetChildIter();
          }
        }
        if (currChild < 0) {
          return myNode.isOptional();
        }

        // now try to satisfy the rest (if any)
        while (currChild + 1 < children.length) {
          currChild++;
          if (myNode.isNegated() == children[currChild].matches()) {
            currChild = -1;  // this node will not match (unless reset)
            return myNode.isOptional();
          }
        }

        // a negated node should only match once (before being reset)
        if (myNode.isNegated()) {
          currChild = -1;
        }
        return true;

      } else { // these are the cases where a single child node can make you match
        for (; currChild < children.length; currChild++) {
          if (myNode.isNegated() != children[currChild].matches()) {
            // a negated node should only match once (before being reset)
            if (myNode.isNegated()) {
              currChild = children.length;
            }
            return true;
          }
        }
        if (myNode.isNegated()) {
          currChild = children.length;
        }
        return myNode.isOptional();
      }
    }

    @Override
    public Tree getMatch() {
      // only DescriptionNodes can match
      throw new UnsupportedOperationException();
    }
  } // end private class CoordinationMatcher

  private static final long serialVersionUID = -7797084959452603087L;

}
