package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Quadruple;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.VariableStrings;
import edu.stanford.nlp.util.logging.Redwood;

public class NodePattern extends SemgrexPattern  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(NodePattern.class);

  private static final long serialVersionUID = -5981133879119233896L;

  private final GraphRelation reln;
  private final boolean negDesc;
  /**
   *  A list of Attribute - key, case_sensitive_pattern, case_insensitive_pattern, negated
   *
   *  If the type of the entry is a String, then string comparison is safe.
   *  If the type is a Boolean, it will always either match or not match corresponding to the Boolean value.
   *  Otherwise, the type will be a Pattern, and you must use Pattern.matches().
   */
  private final List<Attribute> attributes;
  /**
   * Attributes which represent Maps (eg CoNLLUFeats)
   * and only partial matches are necessary
   */
  private final List<Pair<String, Attribute>> partialAttributes;
  private final List<RegexPartialAttribute> regexPartialAttributes;
  private final boolean isRoot;
  private final boolean isLink;
  private final boolean isEmpty;
  private final String name;
  private String descString;
  SemgrexPattern child;

  public NodePattern(GraphRelation r, boolean negDesc,
                     NodeAttributes attrs, boolean isLink, String name) {
    this.reln = r;
    this.negDesc = negDesc;
    this.isLink = isLink;
    // order the attributes so that the pattern stays the same when
    // printing a compiled pattern
    this.attributes = new ArrayList<>();
    // same with partial attributes
    this.partialAttributes = new ArrayList<>();
    this.regexPartialAttributes = new ArrayList<>();

    descString = "{";
    for (Quadruple<String, String, Boolean, List<Pair<Integer, String>>> entry : attrs.attributes()) {
      if (!descString.equals("{"))
        descString += ";";
      String key = entry.first();
      String value = entry.second();
      boolean negated = entry.third();
      List<Pair<Integer, String>> varGroups = entry.fourth();

      // Add the attributes for this key
      if (value.equals("__")) {
        attributes.add(new Attribute(key, true, true, negated, varGroups));
      } else if (value.matches("/.*/")) {
        attributes.add(buildRegexAttribute(key, value, negated, varGroups));
      } else { // raw description
        attributes.add(new Attribute(key, value, value, negated, varGroups));
      }

      if (negated) {
        descString += (key + "!:" + value);
      } else {
        descString += (key + ':' + value);
      }
    }

    for (Quadruple<String, String, String, Boolean> entry : attrs.contains()) {
      String annotation = entry.first();
      String key = entry.second();
      String value = entry.third();
      boolean negated = entry.fourth();
      // TODO: can add varGroups, especially for the regex matches
      List<Pair<Integer, String>> varGroups = Collections.emptyList();

      Class<?> clazz = AnnotationLookup.getValueType(AnnotationLookup.toCoreKey(annotation));
      boolean isMap = clazz != null && Map.class.isAssignableFrom(clazz);
      if (!isMap) {
        throw new SemgrexParseException("Cannot process a single key/value from annotation " + annotation + " as it is not a Map"); 
      }

      final Attribute attr;
      if (key.equals("__")) {
        regexPartialAttributes.add(new RegexPartialAttribute(annotation, "/.*/", value, negated));
      } else if (key.matches("/.*/")) {
        regexPartialAttributes.add(new RegexPartialAttribute(annotation, key, value, negated));
      } else {
        // Add the attributes for this key
        if (value.equals("__")) {
          attr = new Attribute(key, true, true, negated, varGroups);
        } else if (value.matches("/.*/")) {
          attr = buildRegexAttribute(key, value, negated, varGroups);
        } else { // raw description
          attr = new Attribute(key, value, value, negated, varGroups);
        }
        partialAttributes.add(new Pair<>(annotation, attr));
      }

      if (!descString.equals("{"))
        descString += ";";
      String separator = negated ? "!:" : ":";
      // TODO: the descString might look nicer if multiple contains
      // for the same attribute were collapsed into the same map
      descString += (annotation + ":{" + key + ":" + value + "}");
    }

    if (attrs.root()) {
      if (!descString.equals("{"))
        descString += ";";
      descString += "$";
    }
    if (attrs.empty()) {
      if (!descString.equals("{"))
        descString += ";";
      descString += "#";
    }
    descString += '}';

    this.name = name;
    this.child = null;
    this.isRoot = attrs.root();
    this.isEmpty = attrs.empty();
  }

  /**
   * Tests the value to see if it's really a regex, or just a string wrapped in regex.
   * Return an Attribute which matches this expression
   */
  private Attribute buildRegexAttribute(String key, String value, boolean negated, List<Pair<Integer, String>> varGroups) {
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
      return new Attribute(key,
                           Pattern.compile(patternContent),
                           Pattern.compile(patternContent, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),
                           negated, varGroups);
    } else {
      return new Attribute(key, patternContent, patternContent, negated, varGroups);
    }
  }

  private static boolean checkVarMatch(String key, String matchedString,
                                       VariableStrings variableStrings, VariableStrings tempVariableStrings) {
    String existingString = variableStrings.getString(key);
    if (existingString == null) {
      existingString = tempVariableStrings.getString(key);
    }
    if (existingString != null && !existingString.equals(matchedString)) {
      return false;
    }
    if (matchedString != null) {
      tempVariableStrings.setVar(key, matchedString);
    }
    return true;
  }

  private boolean checkMatch(Attribute attr, boolean ignoreCase, String nodeValue,
                             VariableStrings variableStrings, VariableStrings tempVariableStrings) {
    if (nodeValue == null) {
      // treat non-existent attributes has having matched a negated expression
      // so for example, `cpos!:NUM` matches not having a cpos at all
      return attr.negated;
    }

    // Get the node pattern
    Object toMatch = ignoreCase ? attr.caseless : attr.cased;
    boolean matches;
    if (toMatch instanceof Boolean) {
      matches = ((Boolean) toMatch);

      if (matches) {
        for (Pair<Integer, String> varGroup : attr.variableGroups) {
          // TODO possibly a bug here - it is not honoring ignoreCase
          String matchedString = nodeValue;
          String key = varGroup.second();
          if (!checkVarMatch(key, matchedString, variableStrings, tempVariableStrings)) {
            matches = false;
            break;
          }
        }
      }
    } else if (toMatch instanceof String) {
      if (ignoreCase) {
        matches = nodeValue.equalsIgnoreCase(toMatch.toString());
      } else {
        matches = nodeValue.equals(toMatch.toString());
      }

      if (matches) {
        for (Pair<Integer, String> varGroup : attr.variableGroups) {
          // TODO possibly a bug here - it is not honoring ignoreCase
          String matchedString = nodeValue;
          String key = varGroup.second();
          if (!checkVarMatch(key, matchedString, variableStrings, tempVariableStrings)) {
            matches = false;
            break;
          }
        }
      }
    } else if (toMatch instanceof Pattern) {
      Matcher matcher = ((Pattern) toMatch).matcher(nodeValue);
      if (matcher.matches()) {
        matches = true;
        for (Pair<Integer, String> varGroup : attr.variableGroups) {
          String matchedString = matcher.group(varGroup.first());
          String key = varGroup.second();
          if (!checkVarMatch(key, matchedString, variableStrings, tempVariableStrings)) {
            matches = false;
            break;
          }
        }
      } else {
        matches = false;
      }
    } else {
      throw new IllegalStateException("Unknown matcher type: " + toMatch + " (of class + " + toMatch.getClass() + ")");
    }
    if (attr.negated) {
      matches = !matches;
    }
    return matches;
  }

  @SuppressWarnings("unchecked")
  public boolean nodeAttrMatch(IndexedWord node, final SemanticGraph sg, boolean ignoreCase,
                               VariableStrings variableStrings, VariableStrings tempVariableStrings) {
    // System.out.println(node.word());
    if (isRoot) {
      // System.out.println("checking root");
      // if negDesc and we are a root, we can't possibly satisfy the expression, so return false
      // similarly if !negDesc and we are not a root
      // otherwise we fall through so any possible node attributes are matched
      if (negDesc == sg.getRoots().contains(node)) {
        return false;
      }
    }
    if (isEmpty)
      return (negDesc ? !node.equals(IndexedWord.NO_WORD) : node.equals(IndexedWord.NO_WORD));

    // log.info("Attributes are: " + attributes);
    for (Attribute attr : attributes) {
      String key = attr.key;
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

      boolean matches = checkMatch(attr, ignoreCase, nodeValue, variableStrings, tempVariableStrings);

      if (!matches) {
        // System.out.println("doesn't match");
        // System.out.println("");
        return negDesc;
      }
    }
    for (Pair<String, Attribute> partialAttribute : partialAttributes) {
      String annotation = partialAttribute.first();
      Attribute attr = partialAttribute.second();

      Class clazz = Env.lookupAnnotationKey(env, annotation);
      Object rawmap = node.get(clazz);
      final String nodeValue;
      if (rawmap == null) {
        nodeValue = null;
      } else {
        if (!(rawmap instanceof Map))
          throw new RuntimeException("Can only use partial attributes with Maps... this should have been checked at creation time!");
        Map<String, ?> map = (Map) rawmap;

        // TODO: allow for regex match on the keys?
        Object value = map.get(attr.key);
        nodeValue = (value == null) ? null : value.toString();
      }

      // TODO: not connected to varGroups yet
      boolean matches = checkMatch(attr, ignoreCase, nodeValue, variableStrings, tempVariableStrings);
      if (!matches) {
        return negDesc;
      }
    }
    for (RegexPartialAttribute partialAttribute : regexPartialAttributes) {
      Class clazz = Env.lookupAnnotationKey(env, partialAttribute.annotation);
      Object rawmap = node.get(clazz);
      final Map<?, ?> map;
      if (rawmap == null) {
        map = null;
      } else {
        if (!(rawmap instanceof Map))
          throw new RuntimeException("Can only use partial attributes with Maps... this should have been checked at creation time!");
        map = (Map) rawmap;
      }
      // TODO: check varGroups here
      boolean matches = partialAttribute.checkMatches(map, ignoreCase);
      if (!matches) {
        return negDesc;
      }
    }

    // System.out.println("matches");
    // System.out.println("");
    return !negDesc;
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
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings,
                                boolean ignoreCase) {
    return new NodeMatcher(this, sg, null, null, true, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg,
                                Alignment alignment, SemanticGraph sg_align,
                                boolean hyp, IndexedWord node,
                                Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings,
                                boolean ignoreCase) {
    // log.info("making matcher: " +
    // ((reln.equals(GraphRelation.ALIGNED_ROOT)) ? false : hyp));
    return new NodeMatcher(this, sg, alignment, sg_align,
                           (reln.equals(GraphRelation.ALIGNED_ROOT)) ? false : hyp,
                           (reln.equals(GraphRelation.ALIGNED_ROOT)) ? sg_align.getFirstRoot() : node,
                           namesToNodes, namesToRelations, namesToEdges,
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
    private VariableStrings localVariableStrings = null;

    private String nextMatchReln = null;
    private SemanticGraphEdge nextMatchEdge = null;
    private IndexedWord nextMatch = null;

    private boolean namedFirst = false;
    private boolean relnNamedFirst = false;
    private boolean edgeNamedFirst = false;

    private final boolean ignoreCase;

    // universal: childMatcher is null if and only if
    // myNode.child == null OR resetChild has never been called

    public NodeMatcher(NodePattern n, SemanticGraph sg, Alignment alignment, SemanticGraph sg_align, boolean hyp,
                       IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
                       Map<String, SemanticGraphEdge> namesToEdges,
                       VariableStrings variableStrings, boolean ignoreCase) {
      super(sg, alignment, sg_align, hyp, node, namesToNodes, namesToRelations, namesToEdges, variableStrings);
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
                                              (myNode.reln instanceof GraphRelation.ALIGNMENT) ? !hyp : hyp,
                                              nextMatch, namesToNodes, namesToRelations, namesToEdges,
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
      VariableStrings tempVariableStrings = new VariableStrings();

      while (nodeMatchCandidateIterator.hasNext()) {
        if (myNode.reln.getName() != null) {
          String foundReln = namesToRelations.get(myNode.reln.getName());
          nextMatchReln = ((GraphRelation.SearchNodeIterator) nodeMatchCandidateIterator).getReln();
          if ((foundReln != null) && (!nextMatchReln.equals(foundReln))) {
            nextMatch = nodeMatchCandidateIterator.next();
            continue;
          }
        }

        if (myNode.reln.getEdgeName() != null) {
          SemanticGraphEdge foundEdge = namesToEdges.get(myNode.reln.getEdgeName());
          nextMatchEdge = ((GraphRelation.SearchNodeIterator) nodeMatchCandidateIterator).getEdge();
          if ((foundEdge != null) && (!nextMatchEdge.equals(foundEdge))) {
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
                                                 ignoreCase, variableStrings, tempVariableStrings);
            if (found) {
              // nodeAttrMatch already checks negDesc, so no need to
              // check for that here
              finished = false;
              break;
            }
          }
        } else { // try to match the description pattern.
          boolean found = myNode.nodeAttrMatch(nextMatch,
                                               hyp ? sg : sg_aligned,
                                               ignoreCase, variableStrings, tempVariableStrings);
          if (found) {
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
        if (myNode.reln.getEdgeName() != null) {
          if (!namesToEdges.containsKey(myNode.reln.getEdgeName()))
            edgeNamedFirst = true;
          namesToEdges.put(myNode.reln.getEdgeName(), nextMatchEdge);
        }
        commitVariableGroups(tempVariableStrings); // commit my variable groups.
      }
      // finished is false exiting this if and only if nextChild exists
      // and has a label or backreference that matches
      // (also it will just have been reset)
    }

    private void commitVariableGroups(VariableStrings tempVariableStrings) {
      committedVariables = true; // commit all my variable groups.
      localVariableStrings = tempVariableStrings;
      variableStrings.setVars(tempVariableStrings);
    }

    private void decommitVariableGroups() {
      if (committedVariables) {
        variableStrings.unsetVars(localVariableStrings);
        localVariableStrings = null;
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
      if (namesToEdges.containsKey(myNode.reln.edgeName) && edgeNamedFirst) {
        edgeNamedFirst = false;
        namesToEdges.remove(myNode.reln.edgeName);
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
