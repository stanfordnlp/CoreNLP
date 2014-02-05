package edu.stanford.nlp.trees.tregex;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ArrayStringFilter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Filter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DescriptionPattern extends TregexPattern {

  enum DescriptionMode {
    PATTERN, STRINGS, EXACT, ANYTHING
  }

  private final Relation rel;
  private final boolean negDesc;

  private final DescriptionMode descriptionMode;
  private final String exactMatch;
  private final Pattern descPattern;
  private final Filter<String> stringFilter;

  // what size string matchers to use before switching to regex for
  // disjunction matches
  private static final int MAX_STRING_MATCHER_SIZE = 6;

  private final String stringDesc;
  /** The name to give the matched node */
  private final String name;
  /** If this pattern is a link, this is the node linked to */
  private final String linkedName;
  private final boolean isLink;
  // todo: conceptually final, but we'd need to rewrite TregexParser
  // to make it so.
  private TregexPattern child;
  // also conceptually final, but it depends on the child
  /**
   * whether or not this node can change variables.  helps determine
   * which nodes to change when backtracking
   */
  private boolean changesVariables;
  private final List<Pair<Integer,String>> variableGroups; // specifies the groups in a regex that are captured as matcher-global string variables

  private final Function<String, String> basicCatFunction;

  public DescriptionPattern(Relation rel, boolean negDesc, String desc,
                            String name, boolean useBasicCat,
                            Function<String, String> basicCatFunction,
                            List<Pair<Integer,String>> variableGroups,
                            boolean isLink, String linkedName) {
    this.rel = rel;
    this.negDesc = negDesc;
    this.isLink = isLink;
    this.linkedName = linkedName;
    if (desc != null) {
      stringDesc = desc;
      if (desc.equals("__") || desc.equals("/.*/") || desc.equals("/^.*$/")) {
        descriptionMode = DescriptionMode.ANYTHING;
        descPattern = null;
        exactMatch = null;
        stringFilter = null;
      } else if (desc.matches("/.*/")) {
        descriptionMode = DescriptionMode.PATTERN;
        descPattern = Pattern.compile(desc.substring(1, desc.length() - 1));
        exactMatch = null;
        stringFilter = null;
      } else if (desc.indexOf('|') >= 0) {
        // patterns which contain ORs are a special case; we either
        // promote those to regex match or make a string matcher out
        // of them.  for short enough disjunctions, a simple string
        // matcher can be more efficient than a regex.
        String[] words = desc.split("[|]");
        if (words.length <= MAX_STRING_MATCHER_SIZE) {
          descriptionMode = DescriptionMode.STRINGS;
          descPattern = null;
          exactMatch = null;
          stringFilter = new ArrayStringFilter(words);
        } else {
          descriptionMode = DescriptionMode.PATTERN;
          descPattern = Pattern.compile("^(?:" + desc + ")$");
          exactMatch = null;
          stringFilter = null;
        }
      } else { // raw description
        descriptionMode = DescriptionMode.EXACT;
        descPattern = null;
        exactMatch = desc;
        stringFilter = null;
      }
    } else {
      if (name == null && linkedName == null) {
        throw new AssertionError("Illegal description pattern.  Does not describe a node or link/name a variable");
      }
      stringDesc = " ";
      descriptionMode = null;
      descPattern = null;
      exactMatch = null;
      stringFilter = null;
    }
    this.name = name;
    setChild(null);
    this.basicCatFunction = (useBasicCat ? basicCatFunction : null);
    //    System.out.println("Made " + (negDesc ? "negated " : "") + "DescNode with " + desc);
    this.variableGroups = variableGroups;
  }

  @Override
  public String localString() {
    return rel.toString() + ' ' + (negDesc ? "!" : "") + (basicCatFunction != null ? "@" : "") + stringDesc + (name == null ? "" : '=' + name);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (isNegated()) {
      sb.append('!');
    }
    if (isOptional()) {
      sb.append('?');
    }
    sb.append(rel.toString());
    sb.append(' ');
    if (child != null) {
      sb.append('(');
    }
    if (negDesc) {
      sb.append('!');
    }
    if (basicCatFunction != null) {
      sb.append('@');
    }
    sb.append(stringDesc);
    if (isLink) {
      sb.append('~');
      sb.append(linkedName);
    }
    if (name != null) {
      sb.append('=');
      sb.append(name);
    }
    sb.append(' ');
    if (child != null) {
      sb.append(child.toString());
      sb.append(')');
    }
    return sb.toString();
  }

  public void setChild(TregexPattern n) {
    child = n;
    changesVariables = ((descriptionMode != null || isLink) && name != null);
    changesVariables = (changesVariables ||
                        (child != null && child.getChangesVariables()));
  }

  @Override
  public List<TregexPattern> getChildren() {
    if (child == null) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(child);
    }
  }

  @Override
  boolean getChangesVariables() {
    return changesVariables;
  }

  @Override
  public TregexMatcher matcher(Tree root, Tree tree,
                               IdentityHashMap<Tree, Tree> nodesToParents,
                               Map<String, Tree> namesToNodes,
                               VariableStrings variableStrings) {
    return new DescriptionMatcher(this, root, tree, nodesToParents,
                                  namesToNodes, variableStrings);
  }

  // TODO: Why is this a static class with a pointer to the containing
  // class?  There seems to be no reason for such a thing.
  // cdm: agree: It seems like it should just be a non-static inner class.  Try this and check it works....
  private static class DescriptionMatcher extends TregexMatcher {
    private Iterator<Tree> treeNodeMatchCandidateIterator;
    private final DescriptionPattern myNode;

    // a DescriptionMatcher only has a single child; if it is the left
    // side of multiple relations, a CoordinationMatcher is used.

    // childMatcher is null until the first time a matcher needs to check the child 

    // myNode.child == null OR resetChild has never been called
    private TregexMatcher childMatcher;
    private Tree nextTreeNodeMatchCandidate; // the Tree node that this DescriptionMatcher node is trying to match on.
    private boolean finished = false; // when finished = true, it means I have exhausted my potential tree node match candidates.
    private boolean matchedOnce = false;
    private boolean committedVariables = false;


    public DescriptionMatcher(DescriptionPattern n, Tree root, Tree tree,
                              IdentityHashMap<Tree, Tree> nodesToParents,
                              Map<String, Tree> namesToNodes,
                              VariableStrings variableStrings) {
      super(root, tree, nodesToParents, namesToNodes, variableStrings);
      myNode = n;
      resetChildIter();
    }

    @Override
    void resetChildIter() {
      decommitVariableGroups();
      removeNamedNodes();
      treeNodeMatchCandidateIterator =
        myNode.rel.searchNodeIterator(tree, this);
      finished = false;
      nextTreeNodeMatchCandidate = null;
      if (childMatcher != null) {
        // need to tell the children to clean up any preexisting data
        childMatcher.resetChildIter();
      }
    }

    private void resetChild() {
      if (childMatcher == null) {
        if (myNode.child == null) {
          matchedOnce = false;
        }
      } else {
        childMatcher.resetChildIter(nextTreeNodeMatchCandidate);
      }
    }

    @Override
    boolean getChangesVariables() {
      return myNode.getChangesVariables();
    }

    /* goes to the next node in the tree that is a successful match to my description pattern.
     * This is the hotspot method in running tregex, but not clear how to make it faster. */
    // when finished = false; break; is called, it means I successfully matched.
    private void goToNextTreeNodeMatch() {
      decommitVariableGroups(); // make sure variable groups are free.
      removeNamedNodes(); // if we named a node, it should now be unnamed
      finished = true;
      Matcher m = null;
      String value = null;
      while (treeNodeMatchCandidateIterator.hasNext()) {
        nextTreeNodeMatchCandidate = treeNodeMatchCandidateIterator.next();
        if (myNode.descriptionMode == null) {
          // this is a backreference or link
          if (myNode.isLink) {
            Tree otherTree = namesToNodes.get(myNode.linkedName);
            if (otherTree != null) {
              String otherValue = myNode.basicCatFunction == null ? otherTree.value() : myNode.basicCatFunction.apply(otherTree.value());
              String myValue = myNode.basicCatFunction == null ? nextTreeNodeMatchCandidate.value() : myNode.basicCatFunction.apply(nextTreeNodeMatchCandidate.value());
              if (otherValue.equals(myValue)) {
                finished = false;
                break;
              }
            }
          } else if (namesToNodes.get(myNode.name) == nextTreeNodeMatchCandidate) {
            finished = false;
            break;
          }
        } else { // try to match the description pattern.
          // cdm: Nov 2006: Check for null label, just make found false
          // String value = (myNode.basicCatFunction == null ? nextTreeNodeMatchCandidate.value() : myNode.basicCatFunction.apply(nextTreeNodeMatchCandidate.value()));
          // m = myNode.descPattern.matcher(value);
          // boolean found = m.find();
          boolean found;
          value = nextTreeNodeMatchCandidate.value();
          if (value == null) {
            found = false;
          } else {
            if (myNode.basicCatFunction != null) {
              value = myNode.basicCatFunction.apply(value);
            }
            switch(myNode.descriptionMode) {
            case EXACT:
              found = value.equals(myNode.exactMatch);
              break;
            case PATTERN:
              m = myNode.descPattern.matcher(value);
              found = m.find();
              break;
            case ANYTHING:
              found = true;
              break;
            case STRINGS:
              found = myNode.stringFilter.accept(value);
              break;
            default:
              throw new IllegalArgumentException("Unexpected match mode");
            }
          }
          if (found) {
            for (Pair<Integer,String> varGroup : myNode.variableGroups) { // if variables have been captured from a regex, they must match any previous matchings
              String thisVariable = varGroup.second();
              String thisVarString = variableStrings.getString(thisVariable);
              if (m != null) {
                if (thisVarString != null &&
                    !thisVarString.equals(m.group(varGroup.first()))) {
                  // failed to match a variable
                  found = false;
                  break;
                }
              } else {
                if (thisVarString != null &&
                    !thisVarString.equals(value)) {
                  // here we treat any variable group # as a match
                  found = false;
                  break;
                }
              }
            }
          }
          if (found != myNode.negDesc) {
            finished = false;
            break;
          }
        }
      }
      if (!finished) { // I successfully matched.
        resetChild(); // reset my unique TregexMatcher child based on the Tree node I successfully matched at.
        // cdm bugfix jul 2009: on next line need to check for descPattern not null, or else this is a backreference or a link to an already named node, and the map should _not_ be updated
        if ((myNode.descriptionMode != null || myNode.isLink) && myNode.name != null) {
          // note: have to fill in the map as we go for backreferencing
          namesToNodes.put(myNode.name, nextTreeNodeMatchCandidate);
        }
        if (m != null) {
          // commit variable groups using a matcher, meaning
          // it extracts the expressions from that matcher
          commitVariableGroups(m);
        } else if (value != null) {
          // commit using a set string (all groups are treated as the string)
          commitVariableGroups(value);
        }
      }
      // finished is false exiting this if and only if nextChild exists
      // and has a label or backreference that matches
      // (also it will just have been reset)
    }

    private void commitVariableGroups(Matcher m) {
      committedVariables = true; // commit all my variable groups.
      for(Pair<Integer,String> varGroup : myNode.variableGroups) {
        String thisVarString = m.group(varGroup.first());
        variableStrings.setVar(varGroup.second(),thisVarString);
      }
    }

    private void commitVariableGroups(String value) {
      committedVariables = true;
      for(Pair<Integer,String> varGroup : myNode.variableGroups) {
        variableStrings.setVar(varGroup.second(), value);
      }
    }

    private void decommitVariableGroups() {
      if (committedVariables) {
        for(Pair<Integer,String> varGroup : myNode.variableGroups) {
          variableStrings.unsetVar(varGroup.second());
        }
      }
      committedVariables = false;
    }

    private void removeNamedNodes() {
      if ((myNode.descPattern != null || myNode.isLink) &&
          myNode.name != null) {
        namesToNodes.remove(myNode.name);
      }
    }


    /* tries to match the unique child of the DescriptionPattern node to a Tree node.  Returns "true" if succeeds.*/
    private boolean matchChild() {
      // entering here (given that it's called only once in matches())
      // we know finished is false, and either nextChild == null
      // (meaning goToNextChild has not been called) or nextChild exists
      // and has a label or backreference that matches
      if (nextTreeNodeMatchCandidate == null) {  // I haven't been initialized yet, so my child certainly can't be matched yet.
        return false;
      }
      // lazy initialization of the child matcher
      if (childMatcher == null && myNode.child != null) {
        childMatcher = myNode.child.matcher(root, nextTreeNodeMatchCandidate, nodesToParents, namesToNodes, variableStrings);
        //childMatcher.resetChildIter();
      }
      if (childMatcher == null) {
        if (!matchedOnce) {
          matchedOnce = true;
          return true;
        }
        return false;
      }
      return childMatcher.matches();
    }

    // find the next local match
    @Override
    public boolean matches() {
      // this is necessary so that a negated/optional node matches only once
      if (finished) {
        return false;
      }
      while (!finished) {
        if (matchChild()) {
          if (myNode.isNegated()) {
            // negated node only has to fail once
            finished = true;
            return false; // cannot be optional and negated
          } else {
            if (myNode.isOptional()) {
              finished = true;
            }
            return true;
          }
        } else {
          goToNextTreeNodeMatch();
        }
      }
      if (myNode.isNegated()) { // couldn't match my relation/pattern, so succeeded!
        return true;
      } else { // couldn't match my relation/pattern, so failed!
        decommitVariableGroups();
        removeNamedNodes();
        nextTreeNodeMatchCandidate = null;
        // didn't match, but return true anyway if optional
        return myNode.isOptional();
      }
    }

    @Override
    public Tree getMatch() {
      return nextTreeNodeMatchCandidate;
    }

  } // end class DescriptionMatcher

  private static final long serialVersionUID = 1179819056757295757L;

}
