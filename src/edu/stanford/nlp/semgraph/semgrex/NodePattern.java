package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

public class NodePattern extends SemgrexPattern  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(NodePattern.class);

  private static final long serialVersionUID = -5981133879119233896L;

  private final GraphRelation reln;
  private final boolean negDesc;
  /**
   *  A hash map from a key to a pair (case_sensitive_pattern, case_insensitive_pattern)
   *  If the type of the entry is a String, then string comparison is safe.
   *  If the type is a Boolean, it will always either match or not match corresponding to the Boolean
   *  value.
   *  Otherwise, the type will be a Pattern, and you must use Pattern.matches().
   */
  private final Map<String, Pair<Object, Object>> attributes;
  private final boolean isRoot;
  private boolean isLink;
  private boolean isEmpty;
  private final String name;
  private String descString;
  SemgrexPattern child;
  // specifies the groups in a regex that are captured as
  // matcher-global string variables
  private List<Pair<Integer, String>> variableGroups;

  public NodePattern(GraphRelation r, boolean negDesc,
                     Map<String, String> attrs,
                     boolean root, boolean empty, String name) {
    this(r, negDesc, attrs, root, empty, name,
            new ArrayList<>(0));
  }

  // TODO: there is no capacity for named variable groups in the parser right now
  public NodePattern(GraphRelation r, boolean negDesc,
                     Map<String, String> attrs,
                     boolean root, boolean empty, String name,
                     List<Pair<Integer, String>> variableGroups) {
    this.reln = r;
    this.negDesc = negDesc;
    attributes = Generics.newHashMap();
    descString = "{";
    for (Map.Entry<String, String> entry : attrs.entrySet()) {
      if (!descString.equals("{"))
        descString += ";";
      String key = entry.getKey();
      String value = entry.getValue();

      // Add the attributes for this key
      if (value.equals("__")) {
        attributes.put(key, Pair.makePair(true, true));
      } else if (value.matches("/.*/")) {
        boolean isRegexp = false;
        for (int i = 1; i < value.length() - 1; ++i) {
          char chr = value.charAt(i);
          if ( !( (chr >= 'A' && chr <= 'Z') || (chr >= 'a' && chr <= 'z') || (chr >= '0' && chr <= '9') ) ) {
            isRegexp = true;
            break;
          }
        }
        String patternContent = value.substring(1, value.length() - 1);
        if (isRegexp) {
          attributes.put(key, Pair.makePair(
              Pattern.compile(patternContent),
              Pattern.compile(patternContent, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))
          );
        } else {
          attributes.put(key, Pair.makePair(patternContent, patternContent));
        }
      } else { // raw description
        attributes.put(key, Pair.makePair(value, value));
      }



//      if (value.equals("__")) {
//        attributes.put(key, Pair.makePair(Pattern.compile(".*"), Pattern.compile(".*", Pattern.CASE_INSENSITIVE)));
//      } else if (value.matches("/.*/")) {
//        attributes.put(key, Pair.makePair(
//            Pattern.compile(value.substring(1, value.length() - 1)),
//            Pattern.compile(value.substring(1, value.length() - 1), Pattern.CASE_INSENSITIVE))
//        );
//      } else { // raw description
//        attributes.put(key, Pair.makePair(
//            Pattern.compile("^(" + value + ")$"),
//            Pattern.compile("^(" + value + ")$", Pattern.CASE_INSENSITIVE))
//        );
//      }
      descString += (key + ':' + value);
    }
    if (root)
      descString += "$";
    else if (empty)
      descString += "#";
    descString += '}';

    this.name = name;
    this.child = null;
    this.isRoot = root;
    this.isEmpty = empty;

    this.variableGroups = variableGroups;
  }

  @SuppressWarnings("unchecked")
  public boolean nodeAttrMatch(IndexedWord node, final SemanticGraph sg, boolean ignoreCase) {
    // System.out.println(node.word());
    if (isRoot)
      return (negDesc ? !sg.getRoots().contains(node) : sg.getRoots().contains(node));
    // System.out.println("not root");
    if (isEmpty)
      return (negDesc ? !node.equals(IndexedWord.NO_WORD) : node.equals(IndexedWord.NO_WORD));

    // log.info("Attributes are: " + attributes);
    for (Map.Entry<String, Pair<Object, Object>> attr : attributes.entrySet()) {
      String key = attr.getKey();
      // System.out.println(key);
      String nodeValue;
      // if (key.equals("idx"))
      // nodeValue = Integer.toString(node.index());
      // else {

      Class c = Env.lookupAnnotationKey(env, key);
      //find class for the key

      Object value = node.get(c);
      if (value == null)
        nodeValue = null;
      else
        nodeValue = value.toString();
      // }
      // System.out.println(nodeValue);
      if (nodeValue == null)
        return negDesc;

      // Get the node pattern
      Object toMatch = ignoreCase ? attr.getValue().second : attr.getValue().first;
      boolean matches;
      if (toMatch instanceof Boolean) {
        matches = ((Boolean) toMatch);
      } else if (toMatch instanceof String) {
        if (ignoreCase) {
          matches = nodeValue.equalsIgnoreCase(toMatch.toString());
        } else {
          matches = nodeValue.equals(toMatch.toString());
        }
      } else if (toMatch instanceof Pattern) {
        matches = ((Pattern) toMatch).matcher(nodeValue).matches();
      } else {
        throw new IllegalStateException("Unknown matcher type: " + toMatch + " (of class + " + toMatch.getClass() + ")");
      }

      if (!matches) {
        // System.out.println("doesn't match");
        // System.out.println("");
        return negDesc;
      }
    }
    // System.out.println("matches");
    // System.out.println("");
    return !negDesc;
  }

  public void makeLink() {
    isLink = true;
  }

  public boolean isRoot() {
    return isRoot;
  }

  public boolean isNull() {
    return isEmpty;
  }

  @Override
  public String localString() {
    return toString(true, false);
  }

  @Override
  public String toString() {
    return toString(true, true);
  }

  @Override
  public String toString(boolean hasPrecedence) {
    return toString(hasPrecedence, true);
  }

  public String toString(boolean hasPrecedence, boolean addChild) {
    StringBuilder sb = new StringBuilder();
    if (isNegated()) {
      sb.append('!');
    }
    if (isOptional()) {
      sb.append('?');
    }
    sb.append(' ');
    if (reln != null) {
      sb.append(reln);
      sb.append(' ');
    }
    if (!hasPrecedence && addChild && child != null) {
      sb.append('(');
    }
    if (negDesc) {
      sb.append('!');
    }
    sb.append(descString);
    if (name != null) {
      sb.append('=').append(name);
    }
    if (addChild && child != null) {
      sb.append(' ');
      sb.append(child.toString(false));
      if (!hasPrecedence) {
        sb.append(')');
      }
    }
    return sb.toString();
  }

  @Override
  public void setChild(SemgrexPattern n) {
    child = n;
  }

  @Override
  public List<SemgrexPattern> getChildren() {
    if (child == null) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(child);
    }
  }

  public String getName() {
    return name;
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord node,
                                Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations,
                                VariableStrings variableStrings,
                                boolean ignoreCase) {
    return new NodeMatcher(this, sg, null, null, true, node, namesToNodes, namesToRelations, variableStrings,
        ignoreCase);
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg,
                                Alignment alignment, SemanticGraph sg_align,
                                boolean hyp, IndexedWord node,
                                Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations,
                                VariableStrings variableStrings,
                                boolean ignoreCase) {
    // log.info("making matcher: " +
    // ((reln.equals(GraphRelation.ALIGNED_ROOT)) ? false : hyp));
    return new NodeMatcher(this, sg, alignment, sg_align,
                           (reln.equals(GraphRelation.ALIGNED_ROOT)) ? false : hyp,
                           (reln.equals(GraphRelation.ALIGNED_ROOT)) ? sg_align.getFirstRoot() : node,
                           namesToNodes, namesToRelations,
                           variableStrings, ignoreCase);
  }

  private static class NodeMatcher extends SemgrexMatcher {

    /**
     * when finished = true, it means I have exhausted my potential
     * node match candidates.
     */
    private boolean finished = false;
    private Iterator<IndexedWord> nodeMatchCandidateIterator = null;
    private final NodePattern myNode;
    /**
     * a NodeMatcher only has a single child; if it is the left side
     * of multiple relations, a CoordinationMatcher is used.
     */
    private SemgrexMatcher childMatcher;
    private boolean matchedOnce = false;
    private boolean committedVariables = false;

    private String nextMatchReln = null;
    private IndexedWord nextMatch = null;

    private boolean namedFirst = false;
    private boolean relnNamedFirst = false;

    private boolean ignoreCase = false;

    // universal: childMatcher is null if and only if
    // myNode.child == null OR resetChild has never been called

    public NodeMatcher(NodePattern n, SemanticGraph sg, Alignment alignment, SemanticGraph sg_align, boolean hyp,
        IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
        VariableStrings variableStrings, boolean ignoreCase) {
      super(sg, alignment, sg_align, hyp, node, namesToNodes, namesToRelations, variableStrings);
      myNode = n;
      this.ignoreCase = ignoreCase;
      resetChildIter();
    }

    @Override
    void resetChildIter() {
      nodeMatchCandidateIterator = myNode.reln.searchNodeIterator(node, hyp ? sg : sg_aligned);
      if (myNode.reln instanceof GraphRelation.ALIGNMENT)
        ((GraphRelation.ALIGNMENT) myNode.reln).setAlignment(alignment, hyp,
            (GraphRelation.SearchNodeIterator) nodeMatchCandidateIterator);
      finished = false;
      if (nextMatch != null) {
        decommitVariableGroups();
        decommitNamedNodes();
        decommitNamedRelations();
      }
      nextMatch = null;

    }

    private void resetChild() {
      if (childMatcher == null) {
        if (myNode.child == null) {
          matchedOnce = false;
        } else {
          childMatcher = myNode.child.matcher(sg, alignment, sg_aligned,
              (myNode.reln instanceof GraphRelation.ALIGNMENT) ? !hyp : hyp, nextMatch, namesToNodes, namesToRelations,
              variableStrings, ignoreCase);
        }
      } else {
        childMatcher.resetChildIter(nextMatch);
      }
    }

    /*
     * goes to the next node in the tree that is a successful match to my
     * description pattern
     */
    // when finished = false; break; is called, it means I successfully matched.
    @SuppressWarnings("null")
    private void goToNextNodeMatch() {
      decommitVariableGroups(); // make sure variable groups are free.
      decommitNamedNodes();
      decommitNamedRelations();
      finished = true;
      Matcher m = null;
      while (nodeMatchCandidateIterator.hasNext()) {
        if (myNode.reln.getName() != null) {
          String foundReln = namesToRelations.get(myNode.reln.getName());
          nextMatchReln = ((GraphRelation.SearchNodeIterator) nodeMatchCandidateIterator).getReln();
          if ((foundReln != null) && (!nextMatchReln.equals(foundReln))) {
            nextMatch = nodeMatchCandidateIterator.next();
            continue;
          }
        }

        nextMatch = nodeMatchCandidateIterator.next();
        // log.info("going to next match: " + nextMatch.word() + " " +
        // myNode.descString + " " + myNode.isLink);
        if (myNode.descString.equals("{}") && myNode.isLink) {
          IndexedWord otherNode = namesToNodes.get(myNode.name);
          if (otherNode != null) {
            if (otherNode.equals(nextMatch)) {
              if ( ! myNode.negDesc) {
                finished = false;
                break;
              }
            } else {
              if (myNode.negDesc) {
                finished = false;
                break;
              }
            }
          } else {
            boolean found = myNode.nodeAttrMatch(nextMatch,
                                                 hyp ? sg : sg_aligned,
                                                 ignoreCase);
            if (found) {
              for (Pair<Integer, String> varGroup : myNode.variableGroups) {
                // if variables have been captured from a regex, they
                // must match any previous matchings
                String thisVariable = varGroup.second();
                String thisVarString = variableStrings.getString(thisVariable);
                if (thisVarString != null &&
                    !thisVarString.equals(m.group(varGroup.first()))) {
                  // failed to match a variable
                  found = false;
                  break;
                }
              }

              // nodeAttrMatch already checks negDesc, so no need to
              // check for that here
              finished = false;
              break;
            }
          }
        } else { // try to match the description pattern.
          boolean found = myNode.nodeAttrMatch(nextMatch,
                                               hyp ? sg : sg_aligned,
                                               ignoreCase);
          if (found) {
            for (Pair<Integer, String> varGroup : myNode.variableGroups) {
              // if variables have been captured from a regex, they
              // must match any previous matchings
              String thisVariable = varGroup.second();
              String thisVarString = variableStrings.getString(thisVariable);
              if (thisVarString != null &&
                  !thisVarString.equals(m.group(varGroup.first()))) {
                // failed to match a variable
                found = false;
                break;
              }
            }

            // nodeAttrMatch already checks negDesc, so no need to
            // check for that here
            finished = false;
            break;
          }
        }
      } // end while

      if ( ! finished) { // I successfully matched.
        resetChild();
        if (myNode.name != null) {
          // note: have to fill in the map as we go for backreferencing
          if (!namesToNodes.containsKey(myNode.name)) {
            // log.info("making namedFirst");
            namedFirst = true;
          }
          // log.info("adding named node: " + myNode.name + "=" +
          // nextMatch.word());
          namesToNodes.put(myNode.name, nextMatch);
        }
        if (myNode.reln.getName() != null) {
          if (!namesToRelations.containsKey(myNode.reln.getName()))
            relnNamedFirst = true;
          namesToRelations.put(myNode.reln.getName(), nextMatchReln);
        }
        commitVariableGroups(m); // commit my variable groups.
      }
      // finished is false exiting this if and only if nextChild exists
      // and has a label or backreference that matches
      // (also it will just have been reset)
    }

    private void commitVariableGroups(Matcher m) {
      committedVariables = true; // commit all my variable groups.
      for (Pair<Integer, String> varGroup : myNode.variableGroups) {
        String thisVarString = m.group(varGroup.first());
        variableStrings.setVar(varGroup.second(), thisVarString);
      }
    }

    private void decommitVariableGroups() {
      if (committedVariables) {
        for (Pair<Integer, String> varGroup : myNode.variableGroups) {
          variableStrings.unsetVar(varGroup.second());
        }
      }
      committedVariables = false;
    }

    private void decommitNamedNodes() {
      if (namesToNodes.containsKey(myNode.name) && namedFirst) {
        namedFirst = false;
        namesToNodes.remove(myNode.name);
      }
    }

    private void decommitNamedRelations() {
      if (namesToRelations.containsKey(myNode.reln.name) && relnNamedFirst) {
        relnNamedFirst = false;
        namesToRelations.remove(myNode.reln.name);
      }
    }

    /*
     * tries to match the unique child of the NodePattern node to a node.
     * Returns "true" if succeeds.
     */
    private boolean matchChild() {
      // entering here (given that it's called only once in matches())
      // we know finished is false, and either nextChild == null
      // (meaning goToNextChild has not been called) or nextChild exists
      // and has a label or backreference that matches
      if (nextMatch == null) { // I haven't been initialized yet, so my child
                               // certainly can't be matched yet.
        return false;
      }
      if (childMatcher == null) {
        if (!matchedOnce) {
          matchedOnce = true;
          return true;
        }
        return false;
      }
      // childMatcher.namesToNodes.putAll(this.namesToNodes);
      // childMatcher.namesToRelations.putAll(this.namesToRelations);
      boolean match = childMatcher.matches();
      if (match) {
        // namesToNodes.putAll(childMatcher.namesToNodes);
        // namesToRelations.putAll(childMatcher.namesToRelations);
        // System.out.println(node.word() + " " +
        // namesToNodes.get("partnerTwo"));
      } else {
        if (nextMatch != null) {
          decommitVariableGroups();
          decommitNamedNodes();
          decommitNamedRelations();
        }
      }
      return match;
    }

    // find the next local match
    @Override
    public boolean matches() {
      // System.out.println(toString());
      // System.out.println(namesToNodes);
      // log.info("matches: " + myNode.reln);
      // this is necessary so that a negated/optional node matches only once
      if (finished) {
        // System.out.println(false);
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
            // System.out.println(true);
            return true;
          }
        } else {
          goToNextNodeMatch();
        }
      }
      if (myNode.isNegated()) { // couldn't match my relation/pattern, so succeeded!
        return true;
      } else { // couldn't match my relation/pattern, so failed!
        nextMatch = null;
        decommitVariableGroups();
        decommitNamedNodes();
        decommitNamedRelations();
        // didn't match, but return true anyway if optional
        return myNode.isOptional();
      }
    }

    @Override
    public IndexedWord getMatch() {
      return nextMatch;
    }

    @Override
    public String toString() {
      return "node matcher for: " + myNode.localString();
    }

  } // end static class NodeMatcher

}
