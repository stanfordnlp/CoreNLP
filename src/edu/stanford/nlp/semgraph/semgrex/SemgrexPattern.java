package edu.stanford.nlp.semgraph.semgrex;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.ud.CoNLLUDocumentReader;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * A SemgrexPattern is a pattern for matching node and edge configurations a dependency graph.
 * Patterns are written in a similar style to {@code tgrep} or {@code Tregex} and operate over
 * {@code SemanticGraph} objects, which contain {@code IndexedWord nodes}.  Unlike
 * {@code tgrep} but like Unix {@code grep}, there is no pre-indexing
 * of the data to be searched.  Rather there is a linear scan through the graph
 * where matches are sought. <p/>
 *
 * A node is represented by a set of attributes and their values contained by
 * curly braces: {attr1:value1;attr2:value2;...}.  Therefore, {} represents any
 * node in the graph.  Attributes must be plain strings; values can be strings
 * or regular expressions blocked off by "/".  Regular expressions must
 * match the whole attribute value, so that /NN/ matches "NN" only, while /NN.* /
 * matches "NN", "NNS", "NNP", etc. <p/>
 *
 * For example, {@code {lemma:slice;tag:/VB.* /}} represents any verb nodes
 * with "slice" as their lemma.  Attributes are extracted using
 * {@code edu.stanford.nlp.ling.AnnotationLookup}. <p/>
 *
 * The root of the graph can be marked by the $ sign, that is {@code {$}}
 * represents the root node. <p/>
 *
 * Relations are defined by a symbol representing the type of relationship and a
 * string or regular expression representing the value of the relationship. A
 * relationship string of {@code %} means any relationship.  It is
 * also OK simply to omit the relationship symbol altogether.
 * <p/>
 *
 * Currently supported node relations and their symbols: <p/>
 *
 * <table border = "1">
 * <tr><th>Symbol<th>Meaning
 * <tr><td>A &lt;reln B <td> A is the dependent of a relation reln with B
 * <tr><td>A &gt;reln B <td>A is the governor of a relation reln with B
 * <tr><td>A &lt;&lt;reln B <td>A is the dependent of a relation reln in a chain to B following dep-&gt;gov paths
 * <tr><td>A &gt;&gt;reln B <td>A is the governor of a relation reln in a chain to B following gov-&gt;dep paths
 * <tr><td>A x,y&lt;&lt;reln B <td>A is the dependent of a relation reln in a chain to B following dep-&gt;gov paths between distances of x and y
 * <tr><td>A x,y&gt;&gt;reln B <td>A is the governor of a relation reln in a chain to B following gov-&gt;dep paths between distances of x and y
 * <tr><td>A == B <td>A and B are the same nodes in the same graph
 * <tr><td>A . B <td>A immediately precedes B, i.e. A.index() == B.index() - 1
 * <tr><td>A $+ B <td>B is a right immediate sibling of A, i.e. A and B have the same parent and A.index() == B.index() - 1
 * <tr><td>A $- B <td>B is a left immediate sibling of A, i.e. A and B have the same parent and A.index() == B.index() + 1
 * <tr><td>A $++ B <td>B is a right sibling of A, i.e. A and B have the same parent and A.index() < B.index()
 * <tr><td>A $-- B <td>B is a left sibling of A, i.e. A and B have the same parent and A.index() > B.index()
 * <tr><td>A @ B <td>A is aligned to B (this is only used when you have two dependency graphs which are aligned)
 * </table>
 * <p/>
 *
 * In a chain of relations, all relations are relative to the first
 * node in the chain. For example, "{@code {} &gt;nsubj {} &gt;dobj
 * {}}" means "any node that is the governor of both a nsubj and
 * a dobj relation".  If instead what you want is a node that is the
 * governor of a nsubj relation with a node that is itself the
 * governor of dobj relation, you should use parentheses and write: "{@code {} &gt;nsubj
 * ({} &gt;dobj {})}". <p/>
 *
 * If a relation type is specified for the &lt;&lt; relation, the
 * relation type is only used for the first relation in the sequence.
 * Therefore, if B depends on A with the relation type foo, the
 * pattern {@code {} &lt;&lt;foo {}} will then match B and
 * everything that depends on B. <p/>
 *
 * Similarly, if a relation type is specified for the &gt;&gt;
 * relation, the relation type is only used for the last relation in
 * the sequence.  Therefore, if A governs B with the relation type
 * foo, the pattern {@code {} &gt;&gt;foo {}} will then match A
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
 *{@code {} [&lt;subj {} | &lt;agent {}] &amp; @ {} }
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
 * {@code {}=a &gt;&gt; {word:foo} : {}=a &gt;&gt; {word:bar} }
 * </blockquote>
 *
 * This pattern could have been written
 *
 * <blockquote>
 * {@code {}=a &gt;&gt; {word:foo} &gt;&gt; {word:bar} }
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
 * example {@code ({tag:NN}=noun) } will match a singular noun node and
 * after a match is found, the map can be queried with the name to retrieved the
 * matched node using {@link SemgrexMatcher#getNode(String o)} with (String)
 * argument "noun" (<it>not</it> "=noun").  Note that you are not allowed to
 * name a node that is under the scope of a negation operator (the semantics
 * would be unclear, since you can't store a node that never gets matched to).
 * Trying to do so will cause a {@link ParseException} to be thrown. Named nodes
 * <it>can be put within the scope of an optionality operator</it>. <p/>
 *
 * Named nodes that refer back to previously named nodes need not have a node
 * description -- this is known as "backreferencing".  In this case, the
 * expression will match only when all instances of the same name get matched to
 * the same node.  For example: the pattern
 * {@code {} &gt;dobj ({} &gt; {}=foo) &gt;mod ({} &gt; {}=foo) }
 * will match a graph in which there are two nodes, {@code X} and
 * {@code Y}, for which {@code X} is the grandparent of
 * {@code Y} and there are two paths to {@code Y}, one of
 * which goes through a {@code dobj} and one of which goes
 * through a {@code mod}. <p/>
 *
 * <p><h3>Naming relations</h3>
 *
 * It is also possible to name relations.  For example, you can write the pattern
 * {@code {idx:1} &gt;=reln {idx:2}}  The name of the relation will then
 * be stored in the matcher and can be extracted with {@code getRelnName("reln")}
 * At present, though, there is no backreferencing capability such as with the
 * named nodes; this is only useful when using the API to extract the name of the
 * relation used when making the match.
 * <p/>
 * In the case of ancestor and descendant relations, the <b>last</b>
 * relation in the sequence of relations is the name used.
 * <p/>
 *
 * @author Chloe Kiddon
 */
public abstract class SemgrexPattern implements Serializable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SemgrexPattern.class);

  private static final long serialVersionUID = 1722052832350596732L;
  private boolean neg = false;
  private boolean opt = false;
  private String patternString; // conceptually final, but can't do because of parsing

  protected Env env; //always set with setEnv to make sure that it is also availble to child patterns

  // package private constructor
  SemgrexPattern() {
  }

  // NodePattern will return its one child, CoordinationPattern will
  // return the list of children it conjuncts or disjuncts
  abstract List<SemgrexPattern> getChildren();

  abstract String localString();

  abstract void setChild(SemgrexPattern child);

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
  public static SemgrexPattern compile(String semgrex, Env env) {
    try {
      SemgrexParser parser = new SemgrexParser(new StringReader(semgrex + "\n"));
      SemgrexPattern newPattern = parser.Root();
      newPattern.setEnv(env);
      newPattern.patternString = semgrex;
      return newPattern;
    } catch (ParseException ex) {
      throw new SemgrexParseException("Error parsing semgrex pattern " + semgrex, ex);
    } catch (TokenMgrError er) {
      throw new SemgrexParseException("Error parsing semgrex pattern " + semgrex, er);
    }
  }

  public static SemgrexPattern compile(String semgrex) {
    return compile(semgrex, new Env());
  }

  public String pattern() {
    return patternString;
  }

  /**
   * Recursively sets the env variable to this pattern and all its children
   *
   * @param env
   */
  public void setEnv(Env env) {
    this.env = env;
    this.getChildren().stream().forEach(p -> p.setEnv(env));
  }



  // printing methods
  // -----------------------------------------------------------

  /**
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
    if (!(o instanceof SemgrexPattern)) return false;
    return o.toString().equals(this.toString());
  }

  @Override
  public int hashCode() {
    // if (this == null) return 0;
    return this.toString().hashCode();
  }

  public enum OutputFormat {
    LIST,
    OFFSET
  };


  static final String PATTERN = "-pattern";
  static final String TREE_FILE = "-treeFile";
  static final String MODE = "-mode";
  static final String DEFAULT_MODE = "BASIC";
  static final String EXTRAS = "-extras";
  static final String CONLLU_FILE = "-conlluFile";
  static final String OUTPUT_FORMAT_OPTION = "-outputFormat";
  static final String DEFAULT_OUTPUT_FORMAT = "LIST";



  public static void help() {
    log.info("Possible arguments for SemgrexPattern:");
    log.info(PATTERN + ": what pattern to use for matching");
    log.info(TREE_FILE + ": a file of trees to process");
    log.info(CONLLU_FILE + ": a CoNLL-U file of dependency trees to process");
    log.info(MODE + ": what mode for dependencies.  basic, collapsed, or ccprocessed.  To get 'noncollapsed', use basic with extras");
    log.info(EXTRAS + ": whether or not to use extras");
    log.info(OUTPUT_FORMAT_OPTION + ": output format of matches. list or offset. 'list' prints the graph as a list of dependencies, "
                         + "'offset' prints the filename and the line offset in the ConLL-U file.");
    log.info();
    log.info(PATTERN + " is required");
  }

  /**
   * Prints out all matches of a semgrex pattern on a file of dependencies.
   * <br>
   * Usage:<br>
   * java edu.stanford.nlp.semgraph.semgrex.SemgrexPattern [args]
   * <br>
   * See the help() function for a list of possible arguments to provide.
   */
  public static void main(String[] args) throws IOException {
    Map<String,Integer> flagMap = Generics.newHashMap();

    flagMap.put(PATTERN, 1);
    flagMap.put(TREE_FILE, 1);
    flagMap.put(MODE, 1);
    flagMap.put(EXTRAS, 1);
    flagMap.put(CONLLU_FILE, 1);
    flagMap.put(OUTPUT_FORMAT_OPTION, 1);


    Map<String, String[]> argsMap = StringUtils.argsToMap(args, flagMap);
    args = argsMap.get(null);

    // TODO: allow patterns to be extracted from a file
    if (!(argsMap.containsKey(PATTERN)) || argsMap.get(PATTERN).length == 0) {
      help();
      System.exit(2);
    }
    SemgrexPattern semgrex = SemgrexPattern.compile(argsMap.get(PATTERN)[0]);

    String modeString = DEFAULT_MODE;
    if (argsMap.containsKey(MODE) && argsMap.get(MODE).length > 0) {
      modeString = argsMap.get(MODE)[0].toUpperCase();
    }
    SemanticGraphFactory.Mode mode = SemanticGraphFactory.Mode.valueOf(modeString);

    String outputFormatString = DEFAULT_OUTPUT_FORMAT;
    if (argsMap.containsKey(OUTPUT_FORMAT_OPTION) && argsMap.get(OUTPUT_FORMAT_OPTION).length > 0) {
      outputFormatString = argsMap.get(OUTPUT_FORMAT_OPTION)[0].toUpperCase();
    }
    OutputFormat outputFormat = OutputFormat.valueOf(outputFormatString);

    boolean useExtras = true;
    if (argsMap.containsKey(EXTRAS) && argsMap.get(EXTRAS).length > 0) {
      useExtras = Boolean.valueOf(argsMap.get(EXTRAS)[0]);
    }

    List<SemanticGraph> graphs = Generics.newArrayList();
    // TODO: allow other sources of graphs, such as dependency files
    if (argsMap.containsKey(TREE_FILE) && argsMap.get(TREE_FILE).length > 0) {
      for (String treeFile : argsMap.get(TREE_FILE)) {
        log.info("Loading file " + treeFile);
        MemoryTreebank treebank = new MemoryTreebank(new TreeNormalizer());
        treebank.loadPath(treeFile);
        for (Tree tree : treebank) {
          // TODO: allow other languages... this defaults to English
          SemanticGraph graph = SemanticGraphFactory.makeFromTree(tree, mode, useExtras ? GrammaticalStructure.Extras.MAXIMAL : GrammaticalStructure.Extras.NONE);
          graphs.add(graph);
        }
      }
    }

    if (argsMap.containsKey(CONLLU_FILE) && argsMap.get(CONLLU_FILE).length > 0) {
      CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
      for (String conlluFile : argsMap.get(CONLLU_FILE)) {
        log.info("Loading file " + conlluFile);
        Iterator<SemanticGraph> it = reader.getIterator(IOUtils.readerFromString(conlluFile));

        while (it.hasNext()) {
          SemanticGraph graph = it.next();
          graphs.add(graph);
        }
      }
    }

    for (SemanticGraph graph : graphs) {
      SemgrexMatcher matcher = semgrex.matcher(graph);
      if (!(matcher.find())) {
        continue;
      }

      if (outputFormat == OutputFormat.LIST) {
        log.info("Matched graph:");
        log.info(graph.toString(SemanticGraph.OutputFormat.LIST));
        boolean found = true;
        while (found) {
          log.info("Matches at: " + matcher.getMatch().value() + "-" + matcher.getMatch().index());
          List<String> nodeNames = Generics.newArrayList();
          nodeNames.addAll(matcher.getNodeNames());
          Collections.sort(nodeNames);
          for (String name : nodeNames) {
            log.info("  " + name + ": " + matcher.getNode(name).value() + "-" + matcher.getNode(name).index());
          }
          log.info();
          found = matcher.find();
        }
      } else if (outputFormat == OutputFormat.OFFSET) {
        if (graph.vertexListSorted().isEmpty()) {
          continue;
        }
        System.out.printf("+%d %s%n", graph.vertexListSorted().get(0).get(CoreAnnotations.LineNumberAnnotation.class),
            argsMap.get(CONLLU_FILE)[0]);
      }
    }
  }
}
