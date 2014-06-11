package edu.stanford.nlp.semgraph.semgrex;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.Generics;

/**
 * A SemgrexPattern is a <code>tgrep</code>-type pattern for matching node
 * configurations in one of the SemanticGraph structures.  Unlike
 * <code>tgrep</code> but like Unix <code>grep</code>, there is no pre-indexing
 * of the data to be searched.  Rather there is a linear scan through the graph
 * where matches are sought. <p/>
 *
 * SemgrexPattern instances can be matched against instances of the {@link
 * IndexedWord} class. <p/>
 *
 * A node is represented by a set of attributes and their values contained by
 * curly braces: {attr1:value1;attr2:value2;...}.  Therefore, {} represents any
 * node in the graph.  Attributes must be plain strings; values can be strings
 * or regular expressions blocked off by "/".  (I think regular expressions must
 * match the whole attribute value; so that /NN/ matches "NN" only, while /NN.* /
 * matches "NN", "NNS", "NNP", etc. --wcmac) <p/>
 *
 * For example, <code>{lemma:slice;tag:/VB.* /}</code> represents any verb nodes
 * with "slice" as their lemma. <p/>
 * 
 * The root of the graph can be marked by the $ sign, that is <code>{$}</code>
 * represents the root node. <p/>
 *  
 * Relations are defined by a symbol representing the type of relationship and a
 * string, or regular expression representing the value of the relationship. A
 * relationship string of <code>%</code> means any relationship.  It is
 * also OK simply to omit the relationship symbol altogether.
 * <p/>
 *
 * Currently supported node relations and their symbols: <p/>
 *
 * <table border = "1">
 * <tr><th>Symbol<th>Meaning
 * <tr><td>A &lt;reln B <td> A is the dependent of a relation reln with B
 * <tr><td>A &gt;reln B <td>A is the governer of a relation reln with B
 * <tr><td>A &lt;&lt;reln B <td>A is the dependent of a relation reln in a chain to B following dep-&gt;gov paths
 * <tr><td>A &gt;&gt;reln B <td>A is the governer of a relation reln in a chain to B following gov-&gt;dep paths
 * <tr><td>A x,y&lt;&lt;reln B <td>A is the dependent of a relation reln in a chain to B following dep-&gt;gov paths between distances of x and y
 * <tr><td>A x,y&gt;&gt;reln B <td>A is the governer of a relation reln in a chain to B following gov-&gt;dep paths between distances of x and y
 * <tr><td>A @ B <td>A is aligned to B
 * </table>
 * <p/>
 *
 * In a chain of relations, all relations are relative to the first
 * node in the chain. For example, "<code>{} &gt;nsubj {} &gt;dobj
 * {}</code>" means "any node that is the governer of both a nsubj and
 * a dobj relation".  If instead what you want is a node that is the
 * governer of a nsubj relation with a node that is itself the
 * governer of dobj relation, you should write: "<code>{} &gt;nsubj
 * ({} &gt;dobj {})</code>". <p/>
 * 
 * If a relation type is specified for the &lt;&lt; relation, the
 * relation type is only used for the first relation in the sequence.
 * Therefore, if B depends on A with the relation type foo, the
 * pattern <code>{} &lt;&lt;foo {}</code> will then match B and
 * everything that depends on B. <p/>
 *
 * Similarly, if a relation type is specified for the &gt;&gt;
 * relation, the relation type is only used for the last relation in
 * the sequence.  Therefore, if A governs B with the relation type
 * foo, the pattern <code>{} &gt;&gt;foo {}</code> will then match A
 * and all of the nodes which have a sequence leading to A. <p/>
 *
 * <h3>Boolean relational operators</h3>
 *
 * Relations can be combined using the '&amp;' and '|' operators, negated with
 * the '!' operator, and made optional with the '?' operator. <p/>
 *
 * Relations can be grouped using brackets '[' and ']'.  So the
 * expression
 *
 * <blockquote>
 * <code> {} [&lt;subj {} | &lt;agent {}] &amp; @ {} </code>
 * </blockquote>
 *
 * matches a node that is either the dep of a subj or agent relationship and
 * has an alignment to some other node.
 *
 * <p> Relations can be negated with the '!' operator, in which case the
 * expression will match only if there is no node satisfying the relation.
 *
 * <p> Relations can be made optional with the '?' operator.  This way the
 * expression will match even if the optional relation is not satisfied.
 *
 * <p> The operator ":" partitions a pattern into separate patterns,
 * each of which must be matched.  For example, the following is a
 * pattern where the matched node must have both "foo" and "bar" as
 * descendants:
 *
 * <blockquote>
 * <code> {}=a &gt;&gt; {word:foo} : {}=a &gt;&gt; {word:bar} </code>
 * </blockquote>
 *
 * This pattern could have been written
 *
 * <blockquote>
 * <code> {}=a &gt;&gt; {word:foo} &gt;&gt; {word:bar} </code>
 * </blockquote>
 *
 * However, for more complex examples, partitioning a pattern may make
 * it more readable.
 *
 * <p><h3>Naming nodes</h3>
 *
 * Nodes can be given names (a.k.a. handles) using '='.  A named node will
 * be stored in a map that maps names to nodes so that if a match is found, the
 * node corresponding to the named node can be extracted from the map.  For
 * example <code> ({tag:NN}=noun) </code> will match a singular noun node and
 * after a match is found, the map can be queried with the name to retreived the
 * matched node using {@link SemgrexMatcher#getNode(String o)} with (String)
 * argument "noun" (<it>not</it> "=noun").  Note that you are not allowed to
 * name a node that is under the scope of a negation operator (the semantics
 * would be unclear, since you can't store a node that never gets matched to).
 * Trying to do so will cause a {@link ParseException} to be thrown. Named nodes
 * <it>can be put within the scope of an optionality operator</it>. <p/>
 *
 * Named nodes that refer back to previous named nodes need not have a node
 * description -- this is known as "backreferencing".  In this case, the
 * expression will match only when all instances of the same name get matched to
 * the same node.  For example: the pattern 
 * <code>{} &gt;dobj ({} &gt; {}=foo) &gt;mod ({} &gt; {}=foo) </code>
 * will match a graph in which there are two nodes, <code>X</code> and
 * <code>Y</code>, for which <code>X</code> is the grandparent of
 * <code>Y</code> and there are two paths to <code>Y</code>, one of
 * which goes through a <code>dobj</code> and one of which goes
 * through a <code>mod</code>. <p/>
 *
 * @author Chloe Kiddon
 */
public abstract class SemgrexPattern implements Serializable {
  private static final long serialVersionUID = 1722052832350596732L;
  private boolean neg = false;
  private boolean opt = false;
  private String patternString;

  // package private constructor
  SemgrexPattern() {
  }

  // NodePattern will return its one child, CoordinationPattern will
  // return the list of children it conjuncts or disjuncts
  abstract List<SemgrexPattern> getChildren();

  abstract String localString();

  abstract void setChild(SemgrexPattern child);

  void negate() {
    neg = true;
    if (neg && opt) {
      throw new RuntimeException("Node cannot be both negated and optional.");
    }
  }

  void makeOptional() {
    opt = true;
    if (neg && opt) {
      throw new RuntimeException("Node cannot be both negated and optional.");
    }
  }

  boolean isNegated() {
    return neg;
  }

  boolean isOptional() {
    return opt;
  }

  // matcher methods
  // ------------------------------------------------------------

  // These get implemented in semgrex.CoordinationMatcher and NodeMatcher
  abstract SemgrexMatcher matcher(SemanticGraph sg, IndexedWord node, Map<String, IndexedWord> namesToNodes,
      Map<String, String> namesToRelations, VariableStrings variableStrings, boolean ignoreCase);

  abstract SemgrexMatcher matcher(SemanticGraph sg, Alignment alignment, SemanticGraph sg_align, boolean hypToText,
      IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
      VariableStrings variableStrings, boolean ignoreCase);

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph.
   * 
   * @param sg
   *          the SemanticGraph to match on
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph sg) {
    return matcher(sg, sg.getFirstRoot(), Generics.<String, IndexedWord>newHashMap(), Generics.<String, String>newHashMap(),
        new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph, with some
   * initial conditions on the variable assignments
   */
  public SemgrexMatcher matcher(SemanticGraph sg, Map<String, IndexedWord> variables) {
    return matcher(sg, sg.getFirstRoot(), variables, Generics.<String, String>newHashMap(), new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph.
   * 
   * @param sg
   *          the SemanticGraph to match on
   * @param ignoreCase
   *          will ignore case for matching a pattern with a node; not
   *          implemented by Coordination Pattern
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph sg, boolean ignoreCase) {
    return matcher(sg, sg.getFirstRoot(), Generics.<String, IndexedWord>newHashMap(), Generics.<String, String>newHashMap(),
        new VariableStrings(), ignoreCase);
  }

  public SemgrexMatcher matcher(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph) {
    return matcher(hypGraph, alignment, txtGraph, true, hypGraph.getFirstRoot(), Generics.<String, IndexedWord>newHashMap(),
        Generics.<String, String>newHashMap(), new VariableStrings(), false);
  }

  public SemgrexMatcher matcher(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph, boolean ignoreCase) {
    return matcher(hypGraph, alignment, txtGraph, true, hypGraph.getFirstRoot(), Generics.<String, IndexedWord>newHashMap(),
        Generics.<String, String>newHashMap(), new VariableStrings(), ignoreCase);
  }

  // compile method
  // -------------------------------------------------------------

  /**
   * Creates a pattern from the given string.
   * 
   * @param semgrex
   *          the pattern string
   * @return a SemgrexPattern for the string.
   */
  public static SemgrexPattern compile(String semgrex) {
    // TODO: make this threadsafe by making SemgrexParser threadsafe.
    // TODO: make semgrex parser objects non-public
    try {
      SemgrexParser parser = new SemgrexParser(new StringReader(semgrex + "\n"));
      SemgrexPattern newPattern = parser.Root();
      newPattern.setPatternString(semgrex);
      return newPattern;
    } catch (ParseException ex) {
      throw new SemgrexParseException("Error parsing semgrex pattern " + semgrex, ex);
    } catch (TokenMgrError er) {
      throw new SemgrexParseException("Error parsing semgrex pattern " + semgrex, er);
    }
  }

  public String pattern() {
    return patternString;
  }

  public void setPatternString(String patternString) {
    this.patternString = patternString;
  }

  // printing methods
  // -----------------------------------------------------------

  /**
   * @return A single-line string representation of the pattern
   */
  @Override
  abstract public String toString();

  /**
   * hasPrecedence indicates that this pattern has precedence in terms
   * of "order of operations", so there is no need to parenthesize the
   * expression
   */
  abstract public String toString(boolean hasPrecedence);

  private void prettyPrint(PrintWriter pw, int indent) {
    for (int i = 0; i < indent; i++) {
      pw.print("   ");
    }
    pw.println(localString());
    for (Iterator<SemgrexPattern> iter = getChildren().iterator(); iter.hasNext();) {
      SemgrexPattern child = iter.next();
      child.prettyPrint(pw, indent + 1);
    }
  }

  /**
   * Print a multi-line respresentation of the pattern illustrating its syntax.
   */
  public void prettyPrint(PrintWriter pw) {
    prettyPrint(pw, 0);
  }

  /**
   * Print a multi-line respresentation of the pattern illustrating its syntax.
   */
  public void prettyPrint(PrintStream ps) {
    prettyPrint(new PrintWriter(new OutputStreamWriter(ps), true));
  }

  /**
   * Print a multi-line respresentation of the pattern illustrating its syntax
   * to <code>System.out</code>.
   */
  public void prettyPrint() {
    prettyPrint(System.out);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SemgrexPattern)) return false;
    if (((SemgrexPattern) o).toString().equals(this.toString()))
      return true;
    else
      return false;
  }

  @Override
  public int hashCode() {
    if (this == null) return 0;
    return this.toString().hashCode();

  }
}
