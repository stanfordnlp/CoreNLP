package edu.stanford.nlp.semgraph.semgrex;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.trees.ud.CoNLLUDocumentWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.VariableStrings;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * A SemgrexPattern is a pattern for matching node and edge configurations a dependency graph.
 * Patterns are written in a similar style to {@code tgrep} or {@code Tregex} and operate over
 * {@code SemanticGraph} objects, which contain {@code IndexedWord nodes}.  Unlike
 * {@code tgrep} but like Unix {@code grep}, there is no pre-indexing
 * of the data to be searched.  Rather there is a linear scan through the graph
 * where matches are sought.
 *
 * <h2>Nodes</h2>
 *
 * A node is represented by a set of attributes and their values contained by
 * curly braces: {attr1:value1;attr2:value2;...}.  Therefore, {} represents any
 * node in the graph.  Attributes must be plain strings; values can be strings
 * or regular expressions blocked off by "/".  Regular expressions must
 * match the whole attribute value, so that /NN/ matches "NN" only, while /NN.*&#47;
 * matches "NN", "NNS", "NNP", etc.
 * <p>
 * For example, {@code {lemma:slice;tag:/VB.*&#47;}} represents any verb nodes
 * with "slice" as their lemma.  Attributes are extracted using
 * {@link edu.stanford.nlp.ling.AnnotationLookup}.
 * <p>
 * The root of the graph can be marked by the $ sign, that is {@code {$}}
 * represents the root node.
 * <p>
 * A node description can be negated with '!'. {@code !{lemma:boy}} matches any token that isn't "boy".
 * <br>
 * Another way to negate a node description is with a negative
 * lookahead regex, although this starts to look a little ugly.
 * For example, {@code {lemma:/^(?!boy).*$/} } will also match any
 * token with a lemma that isn't "boy".  Note, however, that if you
 * use this style, there needs to be some lemma attached to the token.
 * <br>
 * The special case of an empty text can be tested for with an empty regex.
 * For example, words marked with {@code SpaceAfter=no} will have a blank {@code after} attribute.
 * {@code {after://}} will search for this.
 * <br>
 * The value {@code __} matches any value, so {@code {lemma:__}} matches
 * any word with a lemma.
 * <br>
 * Inside {@code (?i:...)}, attribute values are compared without regard
 * to case, so {@code (?i:{word:bill})} matches "Bill" as well as "bill".
 * The types of relations are always compared with case.
 * <br>
 * It is now also possible to negate individual attributes in Semgrex with {@code !:}
 * For example, this expression will search for a NOUN which is not "boy":
 * {@code {lemma!:boy;pos:NOUN}}
 * <br>
 * Attributes which are maps, in particular the morphological
 * features, can be searched by writing a map, such as
 * {@code {morphofeatures:{Tense:Past;Person!:3}}}
 * This expression will
 * search for words which are past tense but are not in 3rd person.
 * <br>
 * Note that a negated attribute also matches a word which doesn't have
 * that attribute at all, so {@code {lemma!:boy}} matches a word with no
 * lemma, and {@code {morphofeatures:{Person!:3}}} matches a word with no
 * Person feature.
 * <br>
 * An attribute can be made optional with {@code ?:}, which matches a word
 * that either doesn't have the attribute or has it with a matching value:
 * {@code {morphofeatures:{PronType?:Prs}}} matches a word with no
 * PronType as well as one whose PronType is Prs, but not one whose
 * PronType is Dem.  Combined with a variable group and a value of
 * {@code __}, this gives a pattern which matches every word and captures
 * the feature only where it exists:
 * {@code {morphofeatures:{PronType?:__#0%pron}}}
 * <h2>Relations</h2>
 *
 * Relations are defined by a symbol representing the type of relationship and a
 * string or regular expression representing the value of the relationship.
 * The value can be left out to match any relationship, as in {@code {} > {}}.
 * <p>
 * Currently supported node relations and their symbols:
 *
 * <table border = "1">
 * <tr><th>Symbol<th>Meaning
 * <tr><td>{@code A <reln B} <td>A is the dependent of a relation reln with B
 * <tr><td>{@code A >reln B} <td>A is the governor of a relation reln with B
 * <tr><td>{@code A <<reln B} <td>A is the dependent of a relation reln in a chain to B following {@code dep->gov} paths
 * <tr><td>{@code A >>reln B} <td>A is the governor of a relation reln in a chain to B following {@code gov->dep} paths
 * <tr><td>{@code A x,y<<reln B} <td>A is the dependent of a relation reln in a chain to B following {@code dep->gov} paths between distances of x and y
 * <tr><td>{@code A x,y>>reln B} <td>A is the governor of a relation reln in a chain to B following {@code gov->dep} paths between distances of x and y
 * <tr><td>{@code A x<<reln B}, {@code A x>>reln B} <td>the same, at a distance of exactly x
 * <tr><td>{@code A <>reln B} <td>A is connected (either dependent or governor) via relation reln with B
 * <tr><td>{@code A == B} <td>A and B are the same nodes in the same graph
 * <tr><td>{@code A . B} <td>A immediately precedes B, i.e. {@code A.index() == B.index() - 1}
 * <tr><td>{@code A - B} <td>A immediately succeeds B, i.e. {@code A.index() == B.index() + 1}
 * <tr><td>{@code A .. B} <td>A precedes B, i.e. {@code A.index() < B.index()}
 * <tr><td>{@code A -- B} <td>A succeeds B, i.e. {@code A.index() > B.index()}
 * <tr><td>{@code A $+ B} <td>B is a right immediate sibling of A, i.e. A and B have the same parent and {@code A.index() == B.index() - 1}
 * <tr><td>{@code A $- B} <td>B is a left immediate sibling of A, i.e. A and B have the same parent and {@code A.index() == B.index() + 1}
 * <tr><td>{@code A $++ B} <td>B is a right sibling of A, i.e. A and B have the same parent and {@code A.index() < B.index()}
 * <tr><td>{@code A $-- B} <td>B is a left sibling of A, i.e. A and B have the same parent and {@code A.index() > B.index()}
 * <tr><td>{@code A <++ B} <td>B is a right governor of A
 * <tr><td>{@code A <-- B} <td>B is a left governor of A
 * <tr><td>{@code A >++ B} <td>B is a right dependent of A
 * <tr><td>{@code A >-- B} <td>B is a left dependent of A
 * <tr><td>{@code A @ B} <td>A is aligned to B (this is only used when you have two dependency graphs which are aligned)
 * <tr><td>{@code A >reln@graph B} <td>the relation is searched for in the named graph of the sentence, such as {@code >nsubj@enhanced}; see below
 * <caption>Currently supported node relations</caption>
 * </table>
 * <p>
 *
 * In a chain of relations, all relations are relative to the first
 * node in the chain. For example, "{@code {} >nsubj {} >dobj {}}"
 *  means "any node that is the governor of both a nsubj and
 * a dobj relation".  If instead what you want is a node that is the
 * governor of a nsubj relation with a node that is itself the
 * governor of dobj relation, you should use parentheses and write: "{@code {} >nsubj ({} >dobj {})}".
 * <p>
 * If a relation type is specified for {@code A <<reln B} or
 * {@code A >>reln B}, it is only checked on the edge of the chain which
 * touches B.  The other edges of the chain can be anything.  Therefore,
 * if C depends on B with the relation type foo, {@code {}=a <<foo {}=b}
 * matches with C or anything below C as {@code a}, and B as {@code b}.
 * Likewise, {@code {}=a >>foo {}=b} matches with B or anything above B
 * as {@code a}, and C as {@code b}.
 *
 *
 * <h2>Searching more than one graph</h2>
 *
 * A sentence may have more than one dependency graph of it: a basic
 * graph, and an enhanced graph which adds edges the basic one cannot
 * express and may have extra nodes of its own.  A relation is searched
 * for in the basic graph unless it says otherwise, which it does by
 * naming a graph after the relation with {@code @}:
 * <p>
 * {@code {} >nsubj@enhanced {}}
 * <p>
 * The graph names are {@code basic} and {@code enhanced}.  A name which
 * is not one of those is rejected when the pattern is compiled, so a
 * misspelling is reported rather than quietly matching nothing.
 * <p>
 * The choice carries to the relations written below the one which made
 * it.  In {@code {} >nsubj@enhanced ({} >obj {})} the {@code >obj} is
 * searched for in the enhanced graph as well, since it is matched from
 * the node the {@code >nsubj@enhanced} arrived at.  A relation may name
 * a graph of its own to go somewhere else, including back:
 * {@code {} >nsubj@enhanced ({} >obj@basic {})}.
 * <p>
 * Relations written side by side are not below one another, so
 * {@code {} >nsubj@enhanced {} >obj {}} asks for a node with an
 * enhanced nsubj and a basic obj.  Parentheses are what put one relation
 * below another; see the note above about chains of relations.
 * <p>
 * Which graphs a pattern names also decides where a match can start.
 * The nodes searched are those of the graphs the relations at the start
 * of the pattern name, so a pattern which reaches into the enhanced
 * graph can begin at a node only that graph has.  This is how the empty
 * nodes of an enhanced graph are reached:
 * <p>
 * {@code {} <@enhanced {} !< {}}
 * <p>
 * matches a node with a governor in the enhanced graph and none in the
 * basic graph, which is to say a node the basic graph has not got.  A
 * pattern with no relations at all says nothing about where it starts,
 * so it searches every graph of the sentence.
 * <p>
 * A pattern which names a graph the sentence has not got is an error
 * rather than an empty result, since a pattern cannot be matched as
 * written if a graph it uses is missing.
 *
 *
 * <h2>Boolean relational operators</h2>
 *
 * Relations written one after another must all be satisfied.  They can be
 * combined with the '|' operator instead, negated with the '!' operator, and
 * made optional with the '?' operator.  The {@code &} operator is no longer
 * allowed, as writing the relations one after another means the same thing.
 * <p>
 * Relations can be grouped using brackets '[' and ']'.  So the
 * expression
 *
 * <blockquote>
 *{@code {} [<subj {} | <agent {}] @ {} }
 * </blockquote>
 *
 * matches a node that is either the dep of a subj or agent relationship and
 * has an alignment to some other node.
 * <p>
 * Relations can be negated with the '!' operator, in which case the
 * expression will match only if there is no node satisfying the relation.
 * <p>
 * Relations can be made optional with the '?' operator.  This way the
 * expression will match even if the optional relation is not satisfied.
 * <br>
 * In the following example, {@code foo} is matched whether or not it has
 * an {@code nsubj} relation, and if it does, the subject is saved in {@code bar}
 * <blockquote>
 *{@code {word:foo}=foo ?<nsubj {}=bar }
 * </blockquote>
 * <p>
 * The operator ":" partitions a pattern into separate patterns,
 * each of which must be matched.  For example, the following is a
 * pattern where the matched node must have both "foo" and "bar" as
 * descendants:
 *
 * <blockquote>
 * {@code {}=a >> {word:foo} : {}=a >> {word:bar} }
 * </blockquote>
 *
 * This pattern could have been written
 *
 * <blockquote>
 * {@code {}=a >> {word:foo} >> {word:bar} }
 * </blockquote>
 *
 * However, for more complex examples, partitioning a pattern may make
 * it more readable.
 *
 * <h2>Naming nodes</h2>
 *
 * Nodes can be given names (a.k.a. handles) using '='.  A named node will
 * be stored in a map that maps names to nodes so that if a match is found, the
 * node corresponding to the named node can be extracted from the map.  For
 * example {@code ({tag:NN}=noun) } will match a singular noun node and
 * after a match is found, the map can be queried with the name to retrieved the
 * matched node using {@link SemgrexMatcher#getNode(String o)} with (String)
 * argument "noun" (<i>not</i> "=noun").  Note that you are not allowed to
 * name a node that is under the scope of a negation operator (the semantics
 * would be unclear, since you can't store a node that never gets matched to).
 * Trying to do so will cause a {@link SemgrexParseException} to be thrown. Named nodes
 * <i>can be put within the scope of an optionality operator</i>.
 * <p>
 * Named nodes that refer back to previously named nodes need not have a node
 * description -- this is known as "backreferencing".  In this case, the
 * expression will match only when all instances of the same name get matched to
 * the same node.</p>
 * <p>
 * For example:
 * <blockquote>
 * {@code {} >dobj ({} > {}=foo) >mod ({} > {}=foo) }
 * </blockquote>
 * will match a graph in which there are two nodes, {@code X} and
 * {@code Y}, for which {@code X} is the grandparent of
 * {@code Y} and there are two paths to {@code Y}, one of
 * which goes through a {@code dobj} and one of which goes
 * through a {@code mod}.
 * <p>
 * There is also a new operation, {@code uniq}, which allows for a query to reduce to only one match:
 *<br>
 * {@code {} >dobj ({} > {}=foo) >mod ({} > {}=foo) :: uniq}
 *<br>
 * This operation also takes a list of nodes, which if supplied, will use the values of those nodes
 * as keys for the uniq.  In the above example, this variation will match once per observed value of {@code foo}:
 *<br>
 * {@code {} >dobj ({} > {}=foo) >mod ({} > {}=foo) :: uniq foo}
 *
 * <h2>Naming relations</h2>
 *
 * It is also possible to name relations with {@code ~}.  For example, you can write the pattern
 * {@code {idx:1} >~reln {idx:2}}  The name of the relation will then
 * be stored in the matcher and can be extracted with {@code getRelnString("reln")}.
 * If the relation is later referenced a second time, the type of
 * relation must be the same, or the potential match will not be
 * accepted.
 * <p>
 * In the case of {@code <<} and {@code >>}, the name is given the relation
 * of the edge of the chain which touches B, the same edge whose type is
 * checked.
 *
 *
 * <h2>Naming edges</h2>
 *
 * It is also possible to name edges themselves, with {@code =}.  The following
 * pattern will iterate through the edges from the root:
 * {@code {$} >=edge {}}
 * The edge itself is now stored with the matcher and can
 * be extracted with {@code getEdge("edge")}, and its relation with
 * {@code getRelnString("edge")}, as an edge's name also names its
 * relation.  If the edge is
 * later referenced a second time, the exact edge must be the same, or
 * the potential match will not be accepted.
 * <br>
 * This is only legal on relations with only one link between the two endpoints.
 * Other relations (such as grandparent) will throw a parse exception.
 *
 * <h2>Variable Groups</h2>
 *
 * If you write a node description using a regular expression, you can
 * assign its matching groups to variable names. If more than one node
 * has a group assigned to the same variable name, then matching will
 * only occur when all such groups capture the same string. This is
 * useful for enforcing coindexation constraints. The syntax is
 *
 * {@code / <regex-stuff> /#<group-number>%<variable-name> }
 *
 * For example, a pattern which looks for the same word occurring twice in a row is
 *
 * {@code {word:__#1%w} . {word:__#1%w}}
 * <p>
 * The type of a relation can assign its groups the same way, from the
 * relation of the edge it matched.  The groups come right after the type,
 * before any {@code @graph} or {@code =edge}:
 *<br>
 * {@code {}=head >/aux:pass/ {} >/([cn]subj)/#1%etype=edge {}=subject}
 *<br>
 * sets {@code etype} to {@code nsubj} or {@code csubj}.  Group 0 is the
 * whole relation, so {@code >#0%rel} sets {@code rel} to the relation of
 * any edge.  Only a relation with a single edge between its two nodes,
 * the same relations which can name their edge, can assign groups.
 * <p>
 * A node, a variable group, and an edge may not share a name.
 *
 * <h2>Matching more than once at a node</h2>
 *
 * A pattern can match at one node in more than one way, and
 * {@link SemgrexMatcher#find} returns each of them, as Tregex does: with
 * three dependents, {@code {word:ate} > {}=x} matches at "ate" three
 * times, once with each dependent as {@code x}.  To move on to the next
 * node instead, use {@link SemgrexMatcher#findNextMatchingNode}, which
 * returns the first match at each node.  {@code :: uniq} keeps one match
 * for each value of the keys it is given.
 *
 * @author Chloe Kiddon
 */
public abstract class SemgrexPattern implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemgrexPattern.class);

  private static final long serialVersionUID = 1722052832350596732L;
  /** Whether this pattern is negated */
  private boolean neg; // = false;
  /** Whether this pattern is optional */
  private boolean opt; // = false;
  /** The string this pattern was compiled from; only set on the pattern {@link #compile} returns */
  private String patternString; // conceptually final, but can't do because of parsing

  /** The Env used to look up node attribute names */
  protected Env env; //always set with setEnv to make sure that it is also available to child patterns

  // package private constructor
  SemgrexPattern() {
  }

  // NodePattern will return its one child, CoordinationPattern will
  // return the list of children it conjuncts or disjuncts
  /**
   * The graphs whose nodes could be the start of a match.
   *<br>
   * A node can only start a match if the relations written directly off it
   * can be satisfied, and each of those names the graph it is looked for
   * in, or the one the search was started in.  So an ordinary pattern can
   * start at a node of the basic graph, and one which reaches across can
   * also start at a node which only the named graph has.
   *<br>
   * The walk stops at each relation rather than carrying on through the
   * whole pattern: what lies beyond a relation is matched from the node
   * that relation found, so it has no say in where a match may begin.
   *<br>
   * An empty result means nothing in the pattern constrains where it
   * starts, so any node of any graph will do.
   */
  Set<SemgrexGraphName> startingGraphs() {
    Set<SemgrexGraphName> names = EnumSet.noneOf(SemgrexGraphName.class);
    collectStartingGraphs(names);
    return names;
  }

  /**
   * Adds the graphs named by the relations at the top of this pattern.
   *<br>
   * Returns whether this pattern is itself reached by a relation, which is
   * how the walk knows to stop.
   */
  boolean collectStartingGraphs(Set<SemgrexGraphName> names) {
    boolean reached = false;
    for (SemgrexPattern child : getChildren()) {
      reached |= child.collectStartingGraphs(names);
    }
    return reached;
  }

  /**
   * Every graph any relation of this pattern names, wherever it appears.
   *<br>
   * A sentence without one of these cannot be matched as the pattern
   * intends, so the whole pattern is checked against the sentence once,
   * when the search is set up, rather than a relation at a time as the
   * match happens to reach it.
   *
   * @return the graphs the relations of this pattern name
   */
  public Set<SemgrexGraphName> requiredGraphs() {
    Set<SemgrexGraphName> names = EnumSet.noneOf(SemgrexGraphName.class);
    collectRequiredGraphs(names);
    return names;
  }

  void collectRequiredGraphs(Set<SemgrexGraphName> names) {
    for (SemgrexPattern child : getChildren()) {
      child.collectRequiredGraphs(names);
    }
  }

  abstract List<SemgrexPattern> getChildren();

  abstract String localString();


  void negate() {
    if (opt) {
      throw new RuntimeException("Node cannot be both negated and optional.");
    }
    neg = true;
  }

  void makeOptional() {
    if (neg) {
      throw new RuntimeException("Node cannot be both negated and optional.");
    }
    opt = true;
  }

  boolean isNegated() {
    return neg;
  }

  boolean isOptional() {
    return opt;
  }

  /**
   * Returns the node names used in the pattern.
   *
   * @return The node names
   * @throws UnsupportedOperationException unless this is the {@link RootPattern}
   * returned by {@link #compile}
   */
  public Set<String> getKnownVariables() {
    throw new UnsupportedOperationException("Only the RootPattern knows about the full set of known variables");
  }

  /**
   * Returns the variable group names used in the pattern.
   *
   * @return The variable group names
   * @throws UnsupportedOperationException unless this is the {@link RootPattern}
   * returned by {@link #compile}
   */
  public Set<String> getKnownVarGroups() {
    throw new UnsupportedOperationException("Only the RootPattern knows about the full set of known var groups");
  }

  /**
   * Returns the edge names used in the pattern.
   *
   * @return The edge names
   * @throws UnsupportedOperationException unless this is the {@link RootPattern}
   * returned by {@link #compile}
   */
  public Set<String> getKnownEdges() {
    throw new UnsupportedOperationException("Only the RootPattern knows about the full set of known edges");
  }

  // matcher methods
  // ------------------------------------------------------------

  // These get implemented in semgrex.CoordinationMatcher and NodeMatcher
  abstract SemgrexMatcher matcher(SemgrexGraphs graphs, SemgrexGraphName currentGraph,
                                  IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
                                  Map<String, MatchedEdge> namesToEdges,
                                  VariableStrings variableStrings, boolean ignoreCase);

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph.
   *
   * @param sg The SemanticGraph to match on
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph sg) {
    return matcher(new SemgrexGraphs(sg), SemgrexGraphName.BASIC, sg.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph.
   *
   * @param sg The SemanticGraph to match on
   * @param root The IndexedWord from which to start the search
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord root) {
    return matcher(new SemgrexGraphs(sg), SemgrexGraphName.BASIC, root, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph, with some
   * initial conditions on the variable assignments
   *
   * @param sg The SemanticGraph to match on
   * @param variables Nodes which the named nodes of the pattern must match.
   *   The matcher uses this map (not a copy) to store its named nodes.
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph sg, Map<String, IndexedWord> variables) {
    return matcher(new SemgrexGraphs(sg), SemgrexGraphName.BASIC, sg.getFirstRoot(), variables, new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph.
   *
   * @param sg The SemanticGraph to match on
   * @param ignoreCase Will ignore case for matching a pattern with a node; not
   *          implemented by Coordination Pattern
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph sg, boolean ignoreCase) {
    return matcher(new SemgrexGraphs(sg), SemgrexGraphName.BASIC, sg.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), ignoreCase);
  }

  /**
   * Matches against a sentence which has more than one graph of it.
   *<br>
   * The search starts in the basic graph; a relation written with a graph
   * name, such as {@code >nsubj@enhanced}, moves it to that one.
   *
   * @param graphs The graphs of the sentence to match on
   * @return a SemgrexMatcher
   * @throws IllegalStateException if the sentence has no basic graph, or
   *   lacks a graph the pattern names
   */
  public SemgrexMatcher matcher(SemgrexGraphs graphs) {
    SemanticGraph graph = graphs.getDefault();
    if (graph == null) {
      throw new IllegalStateException("Semgrex matching starts in the " + SemgrexGraphName.BASIC +
                                      " graph, but the sentence has not got one");
    }
    // the whole pattern is checked against the sentence here, rather than a
    // relation at a time as the match reaches it, so that a pattern which
    // cannot be matched as written says so instead of quietly finding less
    for (SemgrexGraphName name : requiredGraphs()) {
      if (graphs.get(name) == null) {
        throw new IllegalStateException("Semgrex pattern uses the " + name +
                                        " graph, which the sentence has not got: " + this);
      }
    }

    return matcher(graphs, SemgrexGraphName.BASIC, graph.getFirstRoot(),
                   new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                   new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern on two graphs joined by an
   * alignment.  The search starts at the first root of {@code hypGraph}.
   *
   * @param hypGraph The graph on the side the alignment maps from
   * @param alignment The alignment joining the two graphs
   * @param txtGraph The graph on the side the alignment maps to
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph) {
    return matcher(SemgrexGraphs.aligned(hypGraph, alignment, txtGraph), SemgrexGraphName.BASIC, hypGraph.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern on two graphs joined by an
   * alignment.  The search starts at the first root of {@code hypGraph}.
   *
   * @param hypGraph The graph on the side the alignment maps from
   * @param alignment The alignment joining the two graphs
   * @param txtGraph The graph on the side the alignment maps to
   * @param ignoreCase Will ignore case for matching a pattern with a node; not
   *          implemented by Coordination Pattern
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph, boolean ignoreCase) {
    return matcher(SemgrexGraphs.aligned(hypGraph, alignment, txtGraph), SemgrexGraphName.BASIC, hypGraph.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), ignoreCase);
  }

  // batch processing
  // -------------------------------------------------------------
  /**
   * Postprocess a set of results from the batch processing method
   *
   * TODO: make abstract
   * TODO: neither SortPattern nor UniqPattern operate recursively, seems like a bug
   *
   * @param matches The sentences and their matches
   * @param keepEmptyMatches Whether sentences with no matches are kept
   * @return The postprocessed matches; this implementation returns {@code matches} unchanged
   */
  public List<Pair<CoreMap, List<SemgrexMatch>>> postprocessMatches(List<Pair<CoreMap, List<SemgrexMatch>>> matches, boolean keepEmptyMatches) {
    return matches;
  }

  /**
   * Returns true if this pattern, or any of its children, have a sort operation in them.
   *<br>
   * Useful for sending responses to the python client, since older python clients
   * will expect the old layout and thus we use the old layout if the request isn't sorted.
   * Only newer python clients will support the ::sort operator
   */
  boolean isSorted() {
    for (SemgrexPattern child : getChildren()) {
      if (child.isSorted()) {
        return true;
      }
    }
    return false;
  }

  /**
   * A sentence as it would be named in a complaint about it.
   *<br>
   * The sent_id comment if the sentence came from a CoNLL-U file, or
   * its SentenceIDAnnotation, such as the sent_id a Stanza request
   * sends, since that is what someone would search the file for.
   * Otherwise the sentence's index, if it has one, and its text, which
   * is put together from its words if the sentence does not have its text.
   */
  static String describe(CoreMap sentence) {
    List<String> comments = sentence.get(CoreAnnotations.CommentsAnnotation.class);
    if (comments != null) {
      for (String comment : comments) {
        String trimmed = comment.trim();
        if (trimmed.startsWith("#") && trimmed.replace("#", "").trim().startsWith("sent_id")) {
          return "the sentence at |" + trimmed + "|";
        }
      }
    }
    String sentenceID = sentence.get(CoreAnnotations.SentenceIDAnnotation.class);
    if (sentenceID != null) {
      return "the sentence with sent_id |" + sentenceID + "|";
    }

    Integer index = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
    String text = sentence.get(CoreAnnotations.TextAnnotation.class);
    if (text == null) {
      text = wordsOf(sentence);
    }
    if (index != null && text != null) {
      return "the sentence at index " + index + ", |" + text + "|,";
    }
    if (index != null) {
      return "the sentence at index " + index;
    }
    if (text != null) {
      return "the sentence |" + text + "|";
    }
    return "a sentence";
  }

  /**
   * The words of a sentence's tokens, separated by spaces, or null if it has none.
   *<br>
   * Empty words are left out, as they are not part of the text.
   */
  private static String wordsOf(CoreMap sentence) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tokens == null) {
      return null;
    }
    StringBuilder words = new StringBuilder();
    for (CoreLabel token : tokens) {
      if (token.getEmptyIndex() != 0 || token.word() == null) {
        continue;
      }
      if (words.length() > 0) {
        words.append(' ');
      }
      words.append(token.word());
    }
    if (words.length() == 0) {
      return null;
    }
    return words.toString();
  }

  /**
   * Returns a list of sentences and each of the matches from those sentences.
   *<br>
   * A sentence with no matches is only in the list if keepEmptyMatches is set.
   *<br>
   * A sentence which is missing a graph the pattern needs is an
   * IllegalStateException, which names the sentence.
   *
   * @param sentences The sentences to match, each with a
   *   {@code BasicDependenciesAnnotation} and optionally an
   *   {@code EnhancedDependenciesAnnotation}
   * @param keepEmptyMatches Whether to include sentences with no matches
   * @return Each sentence paired with its matches, after postprocessing
   *   such as {@code uniq} or {@code sort}
   * @throws IllegalStateException if a sentence lacks a graph the pattern needs
   */
  public List<Pair<CoreMap, List<SemgrexMatch>>> matchSentences(List<CoreMap> sentences, boolean keepEmptyMatches) {
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = new ArrayList<>();
    for (CoreMap sentence : sentences) {
      SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
      SemgrexGraphs graphs = SemgrexGraphs.of(basic, enhanced);
      // the same checks the single sentence entry point makes, but naming
      // the sentence: one out of thousands is no use without saying which
      if (basic == null) {
        throw new IllegalStateException("Semgrex matching starts in the " + SemgrexGraphName.BASIC +
                                        " graph, and " + describe(sentence) + " has not got one");
      }
      for (SemgrexGraphName name : requiredGraphs()) {
        if (graphs.get(name) == null) {
          throw new IllegalStateException("Semgrex pattern uses the " + name + " graph, and " +
                                          describe(sentence) + " has not got one: " + this);
        }
      }
      SemgrexMatcher matcher = matcher(graphs);
      if (!matcher.find()) {
        if (keepEmptyMatches) {
          matches.add(new Pair<>(sentence, new ArrayList<>()));
        }
        continue;
      }
      matches.add(new Pair<>(sentence, new ArrayList<>()));
      boolean found = true;
      while (found) {
        matches.get(matches.size() - 1).second().add(new SemgrexMatch(this, matcher));
        found = matcher.find();
      }
    }

    for (SemgrexPattern child : getChildren()) {
      matches = child.postprocessMatches(matches, keepEmptyMatches);
    }
    matches = postprocessMatches(matches, keepEmptyMatches);

    return matches;
  }

  // compile method
  // -------------------------------------------------------------

  /**
   * Creates a pattern from the given string.
   *
   * @param semgrex The pattern string
   * @param env The Env used to look up node attribute names
   * @return A SemgrexPattern for the string.
   * @throws SemgrexParseException if the pattern cannot be parsed
   */
  public static SemgrexPattern compile(String semgrex, Env env) {
    try {
      SemgrexParser parser = new SemgrexParser(new StringReader(semgrex + '\n'));
      SemgrexPattern newPattern = parser.Root();
      newPattern.setEnv(env);
      newPattern.patternString = semgrex;
      return newPattern;
    } catch (ParseException | TokenMgrError ex) {
      throw new SemgrexParseException("Error parsing semgrex pattern " + semgrex, ex);
    }
  }

  /**
   * Creates a pattern from the given string, with an empty Env.
   *
   * @param semgrex The pattern string
   * @return A SemgrexPattern for the string.
   * @throws SemgrexParseException if the pattern cannot be parsed
   */
  public static SemgrexPattern compile(String semgrex) {
    return compile(semgrex, new Env());
  }

  /**
   * Returns the string this pattern was compiled from.
   *
   * @return The pattern string, or null if this pattern did not come
   *   directly from {@link #compile}, such as a child of a compiled pattern
   */
  public String pattern() {
    return patternString;
  }

  /**
   * Recursively sets the env variable to this pattern in this and in all its children
   *
   * @param env An Env
   */
  public void setEnv(Env env) {
    this.env = env;
    this.getChildren().forEach(p -> p.setEnv(env));
  }



  // printing methods
  // -----------------------------------------------------------

  /**
   * The goal is to return a string which will be compiled to the same pattern
   *
   * @return A single-line string representation of the pattern
   */
  @Override
  public abstract String toString();

  /**
   * Returns a string representation of the pattern.
   *
   * @param hasPrecedence indicates that this pattern has precedence in terms
   * of "order of operations", so there is no need to parenthesize the
   * expression
   * @return A single-line string representation of the pattern
   */
  public abstract String toString(boolean hasPrecedence);

  private void prettyPrint(PrintWriter pw, int indent) {
    for (int i = 0; i < indent; i++) {
      pw.print("   ");
    }
    pw.println(localString());
    for (SemgrexPattern child : getChildren()) {
      child.prettyPrint(pw, indent + 1);
    }
  }

  /**
   * Print a multi-line representation of the pattern illustrating its syntax.
   *
   * @param pw The writer to print to
   */
  public void prettyPrint(PrintWriter pw) {
    prettyPrint(pw, 0);
  }

  /**
   * Print a multi-line representation of the pattern illustrating its syntax.
   *
   * @param ps The stream to print to
   */
  public void prettyPrint(PrintStream ps) {
    prettyPrint(new PrintWriter(new OutputStreamWriter(ps), true));
  }

  /**
   * Print a multi-line representation of the pattern illustrating its syntax
   * to {@code System.out}.
   */
  public void prettyPrint() {
    prettyPrint(System.out);
  }

  @Override
  public boolean equals(Object o) {
    //noinspection SimplifiableIfStatement
    if (!(o instanceof SemgrexPattern)) return false;
    return o.toString().equals(this.toString());
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  /** How {@link #main} prints the matches it finds. */
  public enum OutputFormat {
    /** Log each matching graph as a list of dependencies, followed by the matched nodes, edges, and variables */
    LIST,
    /** Print the line number of each matching sentence and the file it came from */
    OFFSET,
    /**
     * Print each matching sentence in CoNLL-U, with the matches as
     * comments, and with only the rows around the matches if context is asked for
     */
    CONLLU,
    /**
     * Print each match on a line of its own: the sentence's id, a tab, and
     * its words, with the match and its named nodes marked as {@code [[word]]}
     */
    TEXT
  }

  private static final String PATTERN = "-pattern";
  private static final String TREE_FILE = "-treeFile";
  private static final String MODE = "-mode";
  private static final String DEFAULT_MODE = "BASIC";
  private static final String EXTRAS = "-extras";
  private static final String CONLLU_FILE = "-conlluFile";
  private static final String OUTPUT_FORMAT_OPTION = "-outputFormat";
  private static final String DEFAULT_OUTPUT_FORMAT = "LIST";
  private static final String CONTEXT = "-C";
  private static final String AFTER_CONTEXT = "-A";
  private static final String BEFORE_CONTEXT = "-B";



  /** Flags which take one argument; the rest take everything up to the next flag */
  private static final Set<String> SINGLE_VALUED =
    Collections.unmodifiableSet(new HashSet<>(Arrays.asList(PATTERN, MODE, EXTRAS, OUTPUT_FORMAT_OPTION,
                                                            CONTEXT, AFTER_CONTEXT, BEFORE_CONTEXT)));

  /** Sentence order for words: by index, then empty index or copy count, as in 8, 8.1, 8.2, 9 */
  private static final Comparator<IndexedWord> SENTENCE_ORDER = SemgrexUtils.SENTENCE_ORDER;

  /**
   * The ConlluEditor comments which highlight the matches of a sentence.
   *<br>
   * {@code # highlight tokens} lists the word each match starts at and
   * its named nodes.  {@code # highlight deprels} lists the dependent of
   * each named edge, as ConlluEditor marks a deprel by the word it points
   * to.  ConlluEditor shows that mark on every arc into the word, the
   * basic one and any in the enhanced graph, and accepts the ids of empty
   * words such as 8.1, which only have enhanced arcs.
   *<br>
   * ConlluEditor keeps only the last of each line in a sentence, so the
   * matches of a sentence share one of each.
   *
   * @param matches the matches of one sentence
   * @return the comments, each only if it has something to highlight
   */
  static List<String> highlightComments(List<SemgrexMatch> matches) {
    Set<IndexedWord> nodes = new TreeSet<>(SENTENCE_ORDER);
    Set<IndexedWord> deprels = new TreeSet<>(SENTENCE_ORDER);
    for (SemgrexMatch match : matches) {
      nodes.add(match.getMatch());
      for (String name : match.getNodeNames()) {
        nodes.add(match.getNode(name));
      }
      for (String name : match.getEdgeNames()) {
        deprels.add(match.getEdge(name).getTarget());
      }
    }

    List<String> comments = new ArrayList<>();
    if (!nodes.isEmpty()) {
      comments.add("# highlight tokens = " + nodes.stream().map(IndexedWord::toCopyOrEmptyIndex).collect(Collectors.joining(" ")));
    }
    if (!deprels.isEmpty()) {
      comments.add("# highlight deprels = " + deprels.stream().map(IndexedWord::toCopyOrEmptyIndex).collect(Collectors.joining(" ")));
    }
    return comments;
  }

  /**
   * The variables a match set, sorted by name, each with its string.
   * A variable whose group matched nothing is left out.
   *
   * @param match the match
   * @return each variable name with its string
   */
  static List<Pair<String, String>> matchVariables(SemgrexMatch match) {
    List<String> names = new ArrayList<>(match.getVariableNames());
    Collections.sort(names);
    List<Pair<String, String>> variables = new ArrayList<>();
    for (String name : names) {
      String value = match.getVariableString(name);
      if (value != null) {
        variables.add(new Pair<>(name, value));
      }
    }
    return variables;
  }

  /**
   * The named edges of a match, sorted by name, each written as
   * {@code reln(gov, dep)}, such as {@code aux(yendo-12, fue-11)}.  An edge
   * from a graph other than the basic graph has the graph after its
   * relation, such as {@code nsubj:xsubj@enhanced(ir-4, Juan-1)}.
   *
   * @param match the match
   * @return each edge name with its edge as written
   */
  static List<Pair<String, String>> matchEdges(SemgrexMatch match) {
    List<String> names = new ArrayList<>(match.getEdgeNames());
    Collections.sort(names);
    List<Pair<String, String>> edges = new ArrayList<>();
    for (String name : names) {
      SemanticGraphEdge edge = match.getEdge(name);
      SemgrexGraphName graph = match.getEdgeGraph(name);
      StringBuilder written = new StringBuilder(edge.getRelation().toString());
      if (graph != null && graph != SemgrexGraphName.BASIC) {
        written.append("@").append(graph.lowerName);
      }
      written.append("(").append(edge.getGovernor().toString(CoreLabel.OutputFormat.VALUE_INDEX));
      written.append(", ").append(edge.getDependent().toString(CoreLabel.OutputFormat.VALUE_INDEX)).append(")");
      edges.add(new Pair<>(name, written.toString()));
    }
    return edges;
  }

  /**
   * The comment {@link #main} writes for a match in CoNLL-U: the pattern,
   * the word it matched at, its named nodes as {@code name:word-index},
   * its named edges as {@code =name:reln(gov, dep)}, and the variables it
   * set as {@code %name:value}, such as {@code %cpos:AUX}.
   *
   * @param semgrexName the pattern, as written in the comment
   * @param match the match
   * @return the comment
   */
  static String matchComment(String semgrexName, SemgrexMatch match) {
    StringBuilder comment = new StringBuilder();
    comment.append("# semgrex pattern = |" + semgrexName + "| matched at " + match.getMatch().toString(CoreLabel.OutputFormat.VALUE_INDEX));

    List<String> nodeNames = new ArrayList<>(match.getNodeNames());
    Collections.sort(nodeNames);
    for (String name : nodeNames) {
      comment.append("  ");
      comment.append(name);
      comment.append(":");
      comment.append(match.getNode(name).toString(CoreLabel.OutputFormat.VALUE_INDEX));
    }
    for (Pair<String, String> edge : matchEdges(match)) {
      comment.append("  =");
      comment.append(edge.first());
      comment.append(":");
      comment.append(edge.second());
    }
    for (Pair<String, String> variable : matchVariables(match)) {
      comment.append("  %");
      comment.append(variable.first());
      comment.append(":");
      comment.append(variable.second());
    }
    return comment.toString();
  }

  /**
   * The line {@link #main} writes for a match as text: the sentence's
   * label, a tab, and its words, with the word the match is at and its
   * named nodes marked, as {@link SemgrexUtils#markedText} writes them.
   *
   * @param label what the sentence is called, such as its {@code sent_id}
   * @param sentence the sentence
   * @param match the match
   * @param before how many words to keep before each marked word, or a negative number for all of them
   * @param after how many words to keep after each marked word, or a negative number for all of them
   * @return the line, without a line separator
   */
  static String matchText(String label, CoreMap sentence, SemgrexMatch match, int before, int after) {
    Set<IndexedWord> marked = markedWords(Collections.singletonList(match));
    return label + "\t" + SemgrexUtils.markedText(sentence, marked, before, after);
  }

  /**
   * A sentence written in CoNLL-U, cut down to the rows of the words in
   * the ranges {@link SemgrexUtils#contextRanges} gives.  The comments are
   * all kept.  A range line is kept with its words, which the ranges never
   * split, and an empty word such as 8.1 is kept with the word it follows.
   * The result is a snippet rather than valid CoNLL-U: the heads of the
   * rows kept may point at rows which are not.
   *
   * @param conllu the sentence in CoNLL-U
   * @param ranges the ranges of word indices to keep
   * @return the rows kept, with the line separators they had
   */
  static String conlluContext(String conllu, List<int[]> ranges) {
    StringBuilder sb = new StringBuilder();
    // the separators are kept with their lines, so the blank line which
    // ends the sentence stays
    for (String line : conllu.split("(?<=\\n)")) {
      String content = line.trim();
      if (content.isEmpty() || content.startsWith("#")) {
        sb.append(line);
        continue;
      }
      String id = content.split("\t", 2)[0];
      int index;
      try {
        // the first word of a range such as 4-5, or the word an empty word such as 8.1 follows
        index = Integer.parseInt(id.split("[-.]", 2)[0]);
      } catch (NumberFormatException e) {
        sb.append(line);
        continue;
      }
      if (SemgrexUtils.inRanges(ranges, index)) {
        sb.append(line);
      }
    }
    return sb.toString();
  }

  /** The word each match is at and its named nodes, in sentence order */
  private static Set<IndexedWord> markedWords(List<SemgrexMatch> matches) {
    Set<IndexedWord> marked = new TreeSet<>(SENTENCE_ORDER);
    for (SemgrexMatch match : matches) {
      marked.add(match.getMatch());
      for (String name : match.getNodeNames()) {
        marked.add(match.getNode(name));
      }
    }
    return marked;
  }

  /**
   * What a sentence is called in a line of text: its {@code sent_id}, or
   * else the file it came from and the line it starts on, or else its
   * position among the sentences read.
   */
  private static String sentenceLabel(CoreMap sentence, String file, int position) {
    String id = SemgrexUtils.sentenceId(sentence);
    if (id != null) {
      return id;
    }
    SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    if (file != null && graph != null && !graph.isEmpty()) {
      Integer line = graph.vertexListSorted().get(0).get(CoreAnnotations.LineNumberAnnotation.class);
      if (line != null) {
        return file + ":" + line;
      }
    }
    return "sentence " + position;
  }

  /**
   * The number of words of context a flag asks for, from the flag itself
   * or else from {@code -C}, or -1 for the whole sentence if neither was given.
   *
   * @throws IllegalArgumentException if the number is not a whole number, zero or more
   */
  static int contextWords(Map<String, List<String>> argsMap, String flag) {
    String value = SemgrexUtils.firstArg(argsMap, flag, SemgrexUtils.firstArg(argsMap, CONTEXT, null));
    if (value == null) {
      return -1;
    }
    try {
      int words = Integer.parseInt(value.trim());
      if (words >= 0) {
        return words;
      }
    } catch (NumberFormatException e) {
      // reported below
    }
    String which = argsMap.containsKey(flag) ? flag : CONTEXT;
    throw new IllegalArgumentException(which + " takes a number of words, 0 or more, not " + value);
  }

  /**
   * Reads the patterns of a pattern file, one to a line.  Blank lines and
   * lines starting with {@code #}, which no pattern can, are skipped, so
   * the patterns can be spaced out and commented.
   *
   * @param text the text of the file
   * @return the patterns, in the order they appear
   * @throws SemgrexParseException naming the line, if a line is not a pattern, or there are no patterns
   */
  static List<SemgrexPattern> readPatterns(String text) {
    List<SemgrexPattern> patterns = new ArrayList<>();
    String[] lines = text.split("\\R");
    for (int i = 0; i < lines.length; ++i) {
      String line = lines[i].trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      try {
        patterns.add(SemgrexPattern.compile(line));
      } catch (SemgrexParseException e) {
        throw new SemgrexParseException("Could not read the pattern on line " + (i + 1) + ": " + line, e);
      }
    }
    if (patterns.isEmpty()) {
      throw new SemgrexParseException("No semgrex patterns found, only blank lines and comments");
    }
    return patterns;
  }

  /**
   * Matches each pattern against the sentences, and puts each sentence's
   * matches together, so that a sentence more than one pattern matches is
   * reported once.  The sentences are in corpus order, and a sentence's
   * matches are in the order of the patterns.
   *
   * @param patterns the patterns
   * @param sentences the sentences, each with its graphs
   * @return each sentence with at least one match, with its matches
   */
  static List<Pair<CoreMap, List<SemgrexMatch>>> matchAll(List<SemgrexPattern> patterns, List<CoreMap> sentences) {
    Map<CoreMap, List<SemgrexMatch>> bySentence = new IdentityHashMap<>();
    for (SemgrexPattern pattern : patterns) {
      for (Pair<CoreMap, List<SemgrexMatch>> sentenceMatches : pattern.matchSentences(sentences, false)) {
        bySentence.computeIfAbsent(sentenceMatches.first(), k -> new ArrayList<>()).addAll(sentenceMatches.second());
      }
    }
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = new ArrayList<>();
    for (CoreMap sentence : sentences) {
      List<SemgrexMatch> sentenceMatches = bySentence.get(sentence);
      if (sentenceMatches != null) {
        matches.add(new Pair<>(sentence, sentenceMatches));
      }
    }
    return matches;
  }

  /** Logs the command line arguments {@link #main} accepts. */
  public static void help() {
    log.info("Possible arguments for SemgrexPattern:");
    log.info(PATTERN + ": what pattern to use for matching, or a file of patterns, one to a line.");
    log.info("  Blank lines and lines starting with # are skipped.  A sentence matched by several patterns is printed once.");
    log.info(TREE_FILE + ": one or more files of trees to process");
    log.info(CONLLU_FILE + ": one or more CoNLL-U files of dependency trees to process.");
    log.info("  Each may be a file, a directory, or a pattern such as 'es_gsd-ud-*.conllu'.");
    log.info("  Files may also be named after the flags, with no flag of their own.");
    log.info(MODE + ": what mode for dependencies.  basic, collapsed, or ccprocessed.  To get 'noncollapsed', use basic with extras");
    log.info(EXTRAS + ": whether or not to use extras");
    log.info(OUTPUT_FORMAT_OPTION + ": output format of matches. list, offset, conllu, or text. 'list' prints the graph as a list of dependencies, "
                         + "'offset' prints the filename and the line offset in the ConLL-U file, "
                         + "'conllu' prints the sentence in CoNLL-U with each match as a comment, "
                         + "and 'text' prints each match on a line: the sent_id, a tab, and the words, with the match marked as [[word]].");
    log.info(CONTEXT + " N: with text or conllu, keep only N words before and after each word of a match, "
                         + "as grep -C does for lines.  A multi-word token is kept whole.  "
                         + "Implies text if no " + OUTPUT_FORMAT_OPTION + " is given");
    log.info(BEFORE_CONTEXT + " N, " + AFTER_CONTEXT + " N: as " + CONTEXT + ", for the words before or after the match.  "
                         + "Either takes precedence over " + CONTEXT + " on its side");
    log.info();
    log.info(PATTERN + " is required");
  }

  /**
   * Prints out all matches of one or more semgrex patterns on files of dependencies.
   *<br>
   * The {@code -pattern} is a pattern, or a file of patterns, one to a
   * line, as {@link #readPatterns} describes.  A sentence which more than
   * one of them matches is printed once, with the matches of each.
   * <p>
   * Usage:<br>
   * java edu.stanford.nlp.semgraph.semgrex.SemgrexPattern [args]
   * <br>
   * See the help() function for a list of possible arguments to provide.
   *
   * @param args The command line arguments
   * @throws IOException if a file cannot be read, or a file pattern matches nothing
   */
  public static void main(String[] args) throws IOException {
    Map<String, List<String>> argsMap = SemgrexUtils.parseArgs(args, SINGLE_VALUED);

    if (SemgrexUtils.firstArg(argsMap, PATTERN, null) == null) {
      help();
      System.exit(2);
    }
    String patternArg = SemgrexUtils.firstArg(argsMap, PATTERN, null);
    String patternText;
    try {
      patternText = IOUtils.slurpFile(patternArg);
    } catch(IOException e) {
      patternText = patternArg;
    }
    List<SemgrexPattern> patterns = readPatterns(patternText);

    // ROOT, since this is matched against the names of enum constants
    // rather than against text.  in a Turkish locale the default would
    // uppercase the i of "basic" to a dotted capital I, and the valueOf
    // below would then fail
    String modeString = SemgrexUtils.firstArg(argsMap, MODE, DEFAULT_MODE).toUpperCase(Locale.ROOT);
    SemanticGraphFactory.Mode mode = SemanticGraphFactory.Mode.valueOf(modeString);

    // asking for context is asking for text, as only text has any
    boolean contextGiven = argsMap.containsKey(CONTEXT) || argsMap.containsKey(BEFORE_CONTEXT) || argsMap.containsKey(AFTER_CONTEXT);
    String defaultFormat = contextGiven ? OutputFormat.TEXT.toString() : DEFAULT_OUTPUT_FORMAT;
    String outputFormatString = SemgrexUtils.firstArg(argsMap, OUTPUT_FORMAT_OPTION, defaultFormat).toUpperCase(Locale.ROOT);
    OutputFormat outputFormat = OutputFormat.valueOf(outputFormatString);
    int before;
    int after;
    try {
      if (contextGiven && outputFormat != OutputFormat.TEXT && outputFormat != OutputFormat.CONLLU) {
        throw new IllegalArgumentException(CONTEXT + ", " + BEFORE_CONTEXT + " and " + AFTER_CONTEXT + " only apply to " +
                                           OUTPUT_FORMAT_OPTION + " text or conllu, not " + outputFormatString.toLowerCase(Locale.ROOT));
      }
      before = contextWords(argsMap, BEFORE_CONTEXT);
      after = contextWords(argsMap, AFTER_CONTEXT);
    } catch (IllegalArgumentException e) {
      log.err(e.getMessage());
      System.exit(2);
      return;
    }

    boolean useExtras = Boolean.parseBoolean(SemgrexUtils.firstArg(argsMap, EXTRAS, "true"));

    List<CoreMap> sentences = new ArrayList<>();
    // which file each sentence was read from, so that a match can be
    // reported against the right one when several were given
    Map<CoreMap, String> sentenceFiles = new IdentityHashMap<>();

    List<String> treePaths = argsMap.getOrDefault(TREE_FILE, Collections.emptyList());
    for (File treeFile : SemgrexUtils.expandFiles(treePaths, ".txt")) {
      log.info("Loading file " + treeFile);
      List<CoreMap> read = SemgrexUtils.readTreeFile(treeFile.toString(), mode, useExtras);
      for (CoreMap sentence : read) {
        sentenceFiles.put(sentence, treeFile.toString());
      }
      sentences.addAll(read);
    }

    // files may be named after any of the flags, without a flag of their own
    List<String> conlluPaths = new ArrayList<>(argsMap.getOrDefault(CONLLU_FILE, Collections.emptyList()));
    conlluPaths.addAll(argsMap.getOrDefault(null, Collections.emptyList()));
    if (!conlluPaths.isEmpty()) {
      CoNLLUReader reader = new CoNLLUReader();
      for (File conlluFile : SemgrexUtils.expandFiles(conlluPaths, ".conllu")) {
        log.info("Loading file " + conlluFile);
        List<Annotation> docs = reader.readCoNLLUFile(conlluFile.toString());
        for (Annotation doc : docs) {
          List<CoreMap> read = doc.get(CoreAnnotations.SentencesAnnotation.class);
          for (CoreMap sentence : read) {
            sentenceFiles.put(sentence, conlluFile.toString());
          }
          sentences.addAll(read);
        }
      }
    }

    List<Pair<CoreMap, List<SemgrexMatch>>> matches = matchAll(patterns, sentences);
    // where each sentence is among those read, for a sentence with no other name
    Map<CoreMap, Integer> positions = new IdentityHashMap<>();
    if (outputFormat == OutputFormat.TEXT) {
      for (int idx = 0; idx < sentences.size(); ++idx) {
        positions.put(sentences.get(idx), idx + 1);
      }
    }

    // UTF-8 whatever the locale, as the treebanks are, rather than letting
    // a terminal in the C locale turn every accented letter into ?
    PrintStream stdout = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "utf-8");
    for (Pair<CoreMap, List<SemgrexMatch>> sentenceMatches : matches) {
      CoreMap sentence = sentenceMatches.first();
      SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
      if (outputFormat == OutputFormat.LIST) {
        log.info("Matched graph:" + System.lineSeparator() + graph.toString(SemanticGraph.OutputFormat.LIST));
        int i = 0;
        for (SemgrexMatch matcher : sentenceMatches.second()) {
          i++;
          log.info("Match " + i + " at: " + matcher.getMatch().toString(CoreLabel.OutputFormat.VALUE_INDEX));
          if (patterns.size() > 1) {
            log.info("  pattern: " + matcher.matchedPattern.toString().trim());
          }
          List<String> nodeNames = Generics.newArrayList();
          nodeNames.addAll(matcher.getNodeNames());
          Collections.sort(nodeNames);
          for (String name : nodeNames) {
            log.info("  " + name + ": " + matcher.getNode(name).toString(CoreLabel.OutputFormat.VALUE_INDEX));
          }
          for (Pair<String, String> edge : matchEdges(matcher)) {
            log.info("  =" + edge.first() + ": " + edge.second());
          }
          for (Pair<String, String> variable : matchVariables(matcher)) {
            log.info("  %" + variable.first() + ": " + variable.second());
          }
        }
      } else if (outputFormat == OutputFormat.OFFSET) {
        if (graph == null || graph.vertexListSorted().isEmpty()) {
          continue;
        }
        stdout.printf("+%d %s%n", graph.vertexListSorted().get(0).get(CoreAnnotations.LineNumberAnnotation.class),
            sentenceFiles.getOrDefault(sentence, ""));
      } else if (outputFormat == OutputFormat.CONLLU) {
        CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
        List<String> comments = new ArrayList<>(sentence.get(CoreAnnotations.CommentsAnnotation.class));
        // TODO: maybe stop putting comments on the graphs?
        if (comments.size() == 0) {
          comments.addAll(graph.getComments());
        }
        for (SemgrexMatch matcher : sentenceMatches.second()) {
          comments.add(matchComment(matcher.matchedPattern.toString().trim(), matcher));
        }
        comments.addAll(highlightComments(sentenceMatches.second()));
        String output = writer.printSemanticGraph(graph, enhanced, false, comments);
        if (contextGiven) {
          output = conlluContext(output, SemgrexUtils.contextRanges(markedWords(sentenceMatches.second()), graph, before, after));
        }
        stdout.print(output);
      } else if (outputFormat == OutputFormat.TEXT) {
        String label = sentenceLabel(sentence, sentenceFiles.get(sentence), positions.get(sentence));
        for (SemgrexMatch matcher : sentenceMatches.second()) {
          stdout.println(matchText(label, sentence, matcher, before, after));
        }
      }
    }
    stdout.flush();
  }

}
