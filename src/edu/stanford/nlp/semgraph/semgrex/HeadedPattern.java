package edu.stanford.nlp.semgraph.semgrex;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.VariableStrings;

/**
 * A head pattern, together with the relations which hang off the node it binds.
 *<br>
 * A pattern such as {@code {word:X} &lt; {word:Y}} is a NodePattern for X
 * with this wrapped around it carrying the {@code &lt; {word:Y}}.  The head
 * establishes a node; the relations are then matched from that node, and
 * are matched again each time the head moves to a different one.
 *<br>
 * The head does not have to be a single node.  {@code [A | B] &lt; Z} has a
 * CoordinationPattern as its head, and {@code (X &lt; Y) &lt; Z} has another
 * HeadedPattern, which is how a pattern accumulates relations: each set of
 * relations wraps what came before rather than being merged into it.
 *<br>
 * Only this class has relations hanging off it.  A NodePattern is a
 * relation and a description, a CoordinationPattern is a list of
 * alternatives, and neither carries anything else, so getChildren means
 * one thing everywhere.
 *
 * @author John Bauer
 */
class HeadedPattern extends SemgrexPattern {

  private static final long serialVersionUID = 1L;

  private final SemgrexPattern head;
  private final SemgrexPattern child;

  public HeadedPattern(SemgrexPattern head, SemgrexPattern child) {
    if (head == null || child == null) {
      throw new IllegalArgumentException("A HeadedPattern needs both a head and a child");
    }
    this.head = head;
    this.child = child;
  }

  public SemgrexPattern getHead() {
    return head;
  }

  /**
   * The relations below a head are matched in whichever graph the head
   * arrived in, so only the head is asked, never the relations themselves.
   */
  @Override
  GraphRelation arrivalRelation() {
    return head.arrivalRelation();
  }

  @Override
  List<SemgrexPattern> getChildren() {
    return Arrays.asList(head, child);
  }

  @Override
  String localString() {
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
    if (head instanceof NodePattern) {
      sb.append(' ');
    }
    if (isNegated()) {
      sb.append('!');
    }
    if (isOptional()) {
      sb.append('?');
    }

    // the relation which arrives at the head goes outside the parentheses
    // and the relations which hang off it go inside, which is how the
    // pattern was written: ">obj ({} >expl {})"
    String description;
    if (head instanceof NodePattern) {
      sb.append(((NodePattern) head).relnString());
      description = ((NodePattern) head).descriptionString();
    } else {
      description = head.toString(true);
    }

    if (!addChild) {
      sb.append(description);
      return sb.toString();
    }
    if (!hasPrecedence) {
      sb.append('(');
    }
    sb.append(description);
    // most patterns render with a leading space of their own; a bracketed
    // disjunction does not, so it needs one here
    String childString = child.toString(false);
    if (!childString.startsWith(" ")) {
      sb.append(' ');
    }
    sb.append(childString);
    if (!hasPrecedence) {
      sb.append(')');
    }
    return sb.toString();
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord node,
                                Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, boolean ignoreCase) {
    return new HeadedMatcher(this, sg, null, null, true, node, namesToNodes, namesToRelations,
                             namesToEdges, variableStrings, ignoreCase);
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, Alignment alignment, SemanticGraph sg_align, boolean hyp,
                                IndexedWord node, Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, boolean ignoreCase) {
    return new HeadedMatcher(this, sg, alignment, sg_align, hyp, node, namesToNodes, namesToRelations,
                             namesToEdges, variableStrings, ignoreCase);
  }

  private static class HeadedMatcher extends SemgrexMatcher {
    private final HeadedPattern myNode;
    private final boolean ignoreCase;
    private final SemgrexMatcher headMatcher;
    /** null until the head has bound a node for the relations to start from */
    private SemgrexMatcher childMatcher;
    /** true once the negated or optional case has been reported, so it is reported once */
    private boolean finished = false;
    private boolean matchedAny = false;

    public HeadedMatcher(HeadedPattern n, SemanticGraph sg, Alignment alignment, SemanticGraph sg_align,
                         boolean hyp, IndexedWord node, Map<String, IndexedWord> namesToNodes,
                         Map<String, String> namesToRelations, Map<String, SemanticGraphEdge> namesToEdges,
                         VariableStrings variableStrings, boolean ignoreCase) {
      super(sg, alignment, sg_align, hyp, node, namesToNodes, namesToRelations, namesToEdges, variableStrings);
      this.myNode = n;
      this.ignoreCase = ignoreCase;
      this.headMatcher = n.head.matcher(sg, alignment, sg_align, hyp, node, namesToNodes,
                                        namesToRelations, namesToEdges, variableStrings, ignoreCase);
    }

    @Override
    void resetChildIter() {
      headMatcher.resetChildIter();
      childMatcher = null;
      finished = false;
      matchedAny = false;
    }

    @Override
    void resetChildIter(IndexedWord node) {
      this.node = node;
      headMatcher.resetChildIter(node);
      childMatcher = null;
      finished = false;
      matchedAny = false;
    }

    /**
     * Anchors the relations to whatever node the head has just bound.
     *<br>
     * The alignment relation swaps which graph is being searched, so the
     * relations below one are matched against the other graph.
     */
    private void resetChild() {
      GraphRelation reln = myNode.arrivalRelation();
      boolean childHyp = (reln instanceof GraphRelation.ALIGNMENT) ? !hyp : hyp;
      childMatcher = myNode.child.matcher(sg, alignment, sg_aligned, childHyp, headMatcher.getMatch(),
                                          namesToNodes, namesToRelations, namesToEdges,
                                          variableStrings, ignoreCase);
    }

    @Override
    public boolean matches() {
      if (finished) {
        return false;
      }
      while (true) {
        if (childMatcher == null) {
          if (!headMatcher.matches()) {
            break;
          }
          resetChild();
        }
        if (childMatcher.matches()) {
          if (myNode.isNegated()) {
            // one match is enough to fail a negated pattern
            finished = true;
            return false;
          }
          matchedAny = true;
          return true;
        }
        // this node of the head is used up; ask the head for another
        childMatcher = null;
      }

      finished = true;
      if (myNode.isNegated()) {
        return true;
      }
      // an optional pattern which matched nothing still lets the match
      // through, with its names unbound
      return myNode.isOptional() && !matchedAny;
    }

    @Override
    public IndexedWord getMatch() {
      return headMatcher.getMatch();
    }

    @Override
    public String toString() {
      return "headed matcher for: " + myNode.localString();
    }
  }
}
