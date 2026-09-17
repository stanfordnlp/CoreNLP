package edu.stanford.nlp.semgraph.semgrex;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;

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
import edu.stanford.nlp.util.StringUtils;
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
 * <h3>Nodes</h3>
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
 * <h3>Relations</h3>
 *
 * Relations are defined by a symbol representing the type of relationship and a
 * string or regular expression representing the value of the relationship. A
 * relationship string of {@code %} means any relationship.  It is
 * also OK simply to omit the relationship symbol altogether.
 * <p>
 * Currently supported node relations and their symbols:
 *
 * <table border = "1">
 * <tr><th>Symbol<th>Meaning
 * <tr><td>A &lt;reln B <td> A is the dependent of a relation reln with B
 * <tr><td>A &gt;reln B <td>A is the governor of a relation reln with B
 * <tr><td>A &lt;&lt;reln B <td>A is the dependent of a relation reln in a chain to B following {@code dep->gov} paths
 * <tr><td>A &gt;&gt;reln B <td>A is the governor of a relation reln in a chain to B following {@code gov->dep} paths
 * <tr><td>{@code A x,y<<reln B} <td>A is the dependent of a relation reln in a chain to B following {@code dep->gov} paths between distances of x and y
 * <tr><td>{@code A x,y>>reln B} <td>A is the governor of a relation reln in a chain to B following {@code gov->dep} paths between distances of x and y
 * <tr><td>A &lt;&gt;reln B <td> A is connected (either dependent or governor) via relation reln with B
 * <tr><td>A == B <td>A and B are the same nodes in the same graph
 * <tr><td>A . B <td>A immediately precedes B, i.e. A.index() == B.index() - 1
 * <tr><td>A - B <td>A immediately succeeds B, i.e. A.index() == B.index() + 1
 * <tr><td>A .. B <td>A precedes B, i.e. {@code A.index() < B.index()}
 * <tr><td>A -- B <td>A succeeds B, i.e. {@code A.index() > B.index()}
 * <tr><td>A $+ B <td>B is a right immediate sibling of A, i.e. A and B have the same parent and A.index() == B.index() - 1
 * <tr><td>A $- B <td>B is a left immediate sibling of A, i.e. A and B have the same parent and A.index() == B.index() + 1
 * <tr><td>A $++ B <td>B is a right sibling of A, i.e. A and B have the same parent and {@code A.index() < B.index()}
 * <tr><td>A $-- B <td>B is a left sibling of A, i.e. A and B have the same parent and {@code A.index() > B.index()}
 * <tr><td>A &lt;++ B <td>B is a right governor of A
 * <tr><td>A &lt;-- B <td>B is a left governor of A
 * <tr><td>A &lt;++ B <td>B is a right dependent of A
 * <tr><td>A &lt;-- B <td>B is a left dependent of A
 * <tr><td>A @ B <td>A is aligned to B (this is only used when you have two dependency graphs which are aligned)
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
 * If a relation type is specified for the {@code <<} relation, the
 * relation type is only used for the first relation in the sequence.
 * Therefore, if B depends on A with the relation type foo, the
 * pattern {@code {} <<foo {}} will then match B and
 * everything that depends on B.
 * <p>
 * Similarly, if a relation type is specified for the {@code >>}
 * relation, the relation type is only used for the last relation in
 * the sequence.  Therefore, if A governs B with the relation type
 * foo, the pattern {@code {} >>foo {}} will then match A
 * and all of the nodes which have a sequence leading to A.
 *
 *
 * <h3>Searching more than one graph</h3>
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
 * <h3>Boolean relational operators</h3>
 *
 * Relations can be combined using the '&amp;' and '|' operators, negated with
 * the '!' operator, and made optional with the '?' operator.
 * <p>
 * Relations can be grouped using brackets '[' and ']'.  So the
 * expression
 *
 * <blockquote>
 *{@code {} [<subj {} | <agent {}] & @ {} }
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
 * <h3>Naming nodes</h3>
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
 * Trying to do so will cause a {@link ParseException} to be thrown. Named nodes
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
 *</p><p>
 * There is also a new operation, {@code uniq}, which allows for a query to reduce to only one match:
 *<br>
 * {@code {} >dobj ({} > {}=foo) >mod ({} > {}=foo) :: uniq}
 *<br>
 * This operation also takes a list of nodes, which if supplied, will use the values of those nodes
 * as keys for the uniq.  In the above example, this variation will match once per observed value of {@code foo}:
 *<br>
 * {@code {} >dobj ({} > {}=foo) >mod ({} > {}=foo) :: uniq foo}
 *
 * <h3>Naming relations</h3>
 *
 * It is also possible to name relations.  For example, you can write the pattern
 * {@code {idx:1} >=reln {idx:2}}  The name of the relation will then
 * be stored in the matcher and can be extracted with {@code getRelnName("reln")}.
 * If the relation is later referenced a second time, the type of
 * relation must be the same, or the potential match will not be
 * accepted.
 * <p>
 * In the case of ancestor and descendant relations, the <b>last</b>
 * relation in the sequence of relations is the name used.
 * <p>
 *
 * <h3>Naming edges</h3>
 *
 * It is also possible to name edges themselves.  The following
 * pattern will iterate through the edges from the root:
 * {@code {$} >~edge {}}
 * The edge itself is now stored with the matcher and can
 * be extracted with {@code getEdgeName("edge")}.  If the edge is
 * later referenced a second time, the exact edge must be the same, or
 * the potential match will not be accepted.
 * <br>
 * This is only legal on relations with only one link between the two endpoints.
 * Other relations (such as grandparent) will throw a parse exception.
 *
 * <h3>Variable Groups</h3>
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
 *
 * <h3>TODO</h3>
 * At present a Semgrex pattern will match only once at a root node, even if there is more than one way of satisfying
 * it under the root node. Probably its semantics should be changed, or at least the option should be given, to return
 * all matches, as is the case for Tregex.  (Is this still true?  It seems to match multiple times from root.)
 *
 * @author Chloe Kiddon
 */
public abstract class SemgrexPattern implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemgrexPattern.class);

  private static final long serialVersionUID = 1722052832350596732L;
  private boolean neg; // = false;
  private boolean opt; // = false;
  private String patternString; // conceptually final, but can't do because of parsing

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
   */
  Set<SemgrexGraphName> requiredGraphs() {
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

  public Set<String> getKnownVariables() {
    throw new UnsupportedOperationException("Only the RootPattern knows about the full set of known variables");
  }

  public Set<String> getKnownVarGroups() {
    throw new UnsupportedOperationException("Only the RootPattern knows about the full set of known var groups");
  }

  public Set<String> getKnownEdges() {
    throw new UnsupportedOperationException("Only the RootPattern knows about the full set of known edges");
  }

  // matcher methods
  // ------------------------------------------------------------

  // These get implemented in semgrex.CoordinationMatcher and NodeMatcher
  abstract SemgrexMatcher matcher(SemgrexGraphs graphs, SemgrexGraphName currentGraph,
                                  IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
                                  Map<String, SemanticGraphEdge> namesToEdges,
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

  public SemgrexMatcher matcher(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph) {
    return matcher(SemgrexGraphs.aligned(hypGraph, alignment, txtGraph), SemgrexGraphName.BASIC, hypGraph.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

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
   * Returns a list of matching sentences and each of the matches from those sentences.
   *<br>
   * Non-matching sentences are currently not returned (may change in the future to return an empty list).
   */
  /**
   * A sentence as it would be named in a complaint about it.
   *<br>
   * The sent_id comment if the sentence came from a CoNLL-U file, since
   * that is what someone would search the file for, and the text of the
   * sentence otherwise.
   */
  private static String describe(CoreMap sentence) {
    List<String> comments = sentence.get(CoreAnnotations.CommentsAnnotation.class);
    if (comments != null) {
      for (String comment : comments) {
        String trimmed = comment.trim();
        if (trimmed.startsWith("#") && trimmed.replace("#", "").trim().startsWith("sent_id")) {
          return "the sentence at |" + trimmed + "|";
        }
      }
    }
    String text = sentence.get(CoreAnnotations.TextAnnotation.class);
    if (text != null) {
      return "the sentence |" + text + "|";
    }
    return "a sentence";
  }

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
   * @return A SemgrexPattern for the string.
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

  public static SemgrexPattern compile(String semgrex) {
    return compile(semgrex, new Env());
  }

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
   * @param hasPrecedence indicates that this pattern has precedence in terms
   * of "order of operations", so there is no need to parenthesize the
   * expression
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
   */
  public void prettyPrint(PrintWriter pw) {
    prettyPrint(pw, 0);
  }

  /**
   * Print a multi-line representation of the pattern illustrating its syntax.
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

  public enum OutputFormat {
    LIST,
    OFFSET,
    CONLLU
  }

  private static final String PATTERN = "-pattern";
  private static final String TREE_FILE = "-treeFile";
  private static final String MODE = "-mode";
  private static final String DEFAULT_MODE = "BASIC";
  private static final String EXTRAS = "-extras";
  private static final String CONLLU_FILE = "-conlluFile";
  private static final String OUTPUT_FORMAT_OPTION = "-outputFormat";
  private static final String DEFAULT_OUTPUT_FORMAT = "LIST";



  /** Flags which take one argument; the rest take everything up to the next flag */
  private static final Set<String> SINGLE_VALUED =
    Collections.unmodifiableSet(new HashSet<>(Arrays.asList(PATTERN, MODE, EXTRAS, OUTPUT_FORMAT_OPTION)));

  /**
   * Splits a command line into the arguments given for each flag.
   *<br>
   * A flag which names files takes every argument up to the next flag, so
   * that a list of them can be given at once.  The others take one, which
   * leaves the files after them free to be files.  Anything not claimed by
   * a flag is collected under a null key.
   */
  static Map<String, List<String>> parseArgs(String[] args) {
    Map<String, List<String>> parsed = new LinkedHashMap<>();
    parsed.put(null, new ArrayList<>());
    String flag = null;
    for (String arg : args) {
      if (!arg.isEmpty() && arg.charAt(0) == '-' && !isFilename(arg)) {
        flag = arg;
        parsed.computeIfAbsent(flag, key -> new ArrayList<>());
      } else {
        parsed.computeIfAbsent(flag, key -> new ArrayList<>()).add(arg);
        if (SINGLE_VALUED.contains(flag)) {
          flag = null;
        }
      }
    }
    return parsed;
  }

  /** A leading "-" is a flag unless it names something on disk, such as -oddly-named.conllu */
  private static boolean isFilename(String arg) {
    return new File(arg).exists();
  }

  private static String firstArg(Map<String, List<String>> args, String flag, String fallback) {
    List<String> values = args.get(flag);
    return (values == null || values.isEmpty()) ? fallback : values.get(0);
  }

  /**
   * Every file named by the given paths, expanding directories and globs.
   *<br>
   * A path may be a file, a directory, or a pattern such as
   * es_gsd-ud-*.conllu.  A shell normally expands a pattern before the
   * program sees it, but not when it is quoted.  A file named outright is
   * read whatever it is called; a directory gives up only the files with
   * the given extension.
   */
  static List<File> expandFiles(List<String> paths, String extension) throws IOException {
    List<File> files = new ArrayList<>();
    for (String path : paths) {
      File file = new File(path);
      if (file.isFile()) {
        files.add(file);
      } else if (file.isDirectory()) {
        // a directory is a request for the files of the kind being read,
        // not for a README which happens to sit beside them
        File[] listed = file.listFiles();
        if (listed != null) {
          List<File> sorted = new ArrayList<>(Arrays.asList(listed));
          Collections.sort(sorted);
          for (File child : sorted) {
            if (child.isFile() && child.getName().endsWith(extension)) {
              files.add(child);
            }
          }
          if (files.isEmpty()) {
            log.info("No files ending in " + extension + " in " + file);
          }
        }
      } else {
        List<File> matches = glob(path);
        if (matches.isEmpty()) {
          throw new FileNotFoundException("Could not find any files matching " + path);
        }
        files.addAll(matches);
      }
    }
    return files;
  }

  /** The files matching a path whose last piece is a pattern */
  private static List<File> glob(String path) {
    File asFile = new File(path);
    File parent = asFile.getParentFile() == null ? new File(".") : asFile.getParentFile();
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + asFile.getName());
    File[] listed = parent.listFiles();
    List<File> matches = new ArrayList<>();
    if (listed != null) {
      for (File child : listed) {
        if (child.isFile() && matcher.matches(Paths.get(child.getName()))) {
          matches.add(child);
        }
      }
    }
    Collections.sort(matches);
    return matches;
  }

  public static void help() {
    log.info("Possible arguments for SemgrexPattern:");
    log.info(PATTERN + ": what pattern to use for matching");
    log.info(TREE_FILE + ": one or more files of trees to process");
    log.info(CONLLU_FILE + ": one or more CoNLL-U files of dependency trees to process.");
    log.info("  Each may be a file, a directory, or a pattern such as 'es_gsd-ud-*.conllu'.");
    log.info("  Files may also be named after the flags, with no flag of their own.");
    log.info(MODE + ": what mode for dependencies.  basic, collapsed, or ccprocessed.  To get 'noncollapsed', use basic with extras");
    log.info(EXTRAS + ": whether or not to use extras");
    log.info(OUTPUT_FORMAT_OPTION + ": output format of matches. list or offset. 'list' prints the graph as a list of dependencies, "
                         + "'offset' prints the filename and the line offset in the ConLL-U file.");
    log.info();
    log.info(PATTERN + " is required");
  }

  /**
   * Prints out all matches of a semgrex pattern on a file of dependencies.
   * <p>
   * Usage:<br>
   * java edu.stanford.nlp.semgraph.semgrex.SemgrexPattern [args]
   * <br>
   * See the help() function for a list of possible arguments to provide.
   */
  public static void main(String[] args) throws IOException {
    Map<String, List<String>> argsMap = parseArgs(args);

    if (firstArg(argsMap, PATTERN, null) == null) {
      help();
      System.exit(2);
    }
    SemgrexPattern semgrex;
    String patternArg = firstArg(argsMap, PATTERN, null);
    try {
      semgrex = SemgrexPattern.compile(IOUtils.slurpFile(patternArg));
    } catch(IOException e) {
      semgrex = SemgrexPattern.compile(patternArg);
    }

    // ROOT, since this is matched against the names of enum constants
    // rather than against text.  in a Turkish locale the default would
    // uppercase the i of "basic" to a dotted capital I, and the valueOf
    // below would then fail
    String modeString = firstArg(argsMap, MODE, DEFAULT_MODE).toUpperCase(Locale.ROOT);
    SemanticGraphFactory.Mode mode = SemanticGraphFactory.Mode.valueOf(modeString);

    String outputFormatString = firstArg(argsMap, OUTPUT_FORMAT_OPTION, DEFAULT_OUTPUT_FORMAT).toUpperCase(Locale.ROOT);
    OutputFormat outputFormat = OutputFormat.valueOf(outputFormatString);

    boolean useExtras = Boolean.parseBoolean(firstArg(argsMap, EXTRAS, "true"));

    List<CoreMap> sentences = new ArrayList<>();
    // which file each sentence was read from, so that a match can be
    // reported against the right one when several were given
    Map<CoreMap, String> sentenceFiles = new IdentityHashMap<>();

    List<String> treePaths = argsMap.getOrDefault(TREE_FILE, Collections.emptyList());
    for (File treeFile : expandFiles(treePaths, ".txt")) {
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
      try {
        CoNLLUReader reader = new CoNLLUReader();
        for (File conlluFile : expandFiles(conlluPaths, ".conllu")) {
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
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    List<Pair<CoreMap, List<SemgrexMatch>>> matches = semgrex.matchSentences(sentences, false);

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
          List<String> nodeNames = Generics.newArrayList();
          nodeNames.addAll(matcher.getNodeNames());
          Collections.sort(nodeNames);
          for (String name : nodeNames) {
            log.info("  " + name + ": " + matcher.getNode(name).toString(CoreLabel.OutputFormat.VALUE_INDEX));
          }
        }
      } else if (outputFormat == OutputFormat.OFFSET) {
        if (graph == null || graph.vertexListSorted().isEmpty()) {
          continue;
        }
        System.out.printf("+%d %s%n", graph.vertexListSorted().get(0).get(CoreAnnotations.LineNumberAnnotation.class),
            sentenceFiles.getOrDefault(sentence, ""));
      } else if (outputFormat == OutputFormat.CONLLU) {
        CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
        String semgrexName = semgrex.toString().trim();
        List<String> comments = new ArrayList<>(sentence.get(CoreAnnotations.CommentsAnnotation.class));
        // TODO: maybe stop putting comments on the graphs?
        if (comments.size() == 0) {
          comments.addAll(graph.getComments());
        }
        for (SemgrexMatch matcher : sentenceMatches.second()) {
          StringBuilder comment = new StringBuilder();
          comment.append("# semgrex pattern |" + semgrexName + "| matched at " + matcher.getMatch().toString(CoreLabel.OutputFormat.VALUE_INDEX));

          List<String> nodeNames = new ArrayList<>();
          nodeNames.addAll(matcher.getNodeNames());
          Collections.sort(nodeNames);
          for (String name : nodeNames) {
            comment.append("  ");
            comment.append(name);
            comment.append(":");
            comment.append(matcher.getNode(name).toString(CoreLabel.OutputFormat.VALUE_INDEX));
          }
          comments.add(comment.toString());
        }
        String output = writer.printSemanticGraph(graph, enhanced, false, comments);
        System.out.print(output);
      }
    }
  }

}
