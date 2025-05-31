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
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.ud.CoNLLUDocumentWriter;
import edu.stanford.nlp.util.ArrayCoreMap;
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
                                  Map<String, String> namesToRelations, Map<String, SemanticGraphEdge> namesToEdges,
                                  VariableStrings variableStrings, boolean ignoreCase);

  abstract SemgrexMatcher matcher(SemanticGraph sg, Alignment alignment, SemanticGraph sg_align, boolean hypToText,
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
    return matcher(sg, sg.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph.
   *
   * @param sg The SemanticGraph to match on
   * @param root The IndexedWord from which to start the search
   * @return a SemgrexMatcher
   */
  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord root) {
    return matcher(sg, root, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

  /**
   * Get a {@link SemgrexMatcher} for this pattern in this graph, with some
   * initial conditions on the variable assignments
   */
  public SemgrexMatcher matcher(SemanticGraph sg, Map<String, IndexedWord> variables) {
    return matcher(sg, sg.getFirstRoot(), variables, new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
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
    return matcher(sg, sg.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), ignoreCase);
  }

  public SemgrexMatcher matcher(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph) {
    return matcher(hypGraph, alignment, txtGraph, true, hypGraph.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), false);
  }

  public SemgrexMatcher matcher(SemanticGraph hypGraph, Alignment alignment, SemanticGraph txtGraph, boolean ignoreCase) {
    return matcher(hypGraph, alignment, txtGraph, true, hypGraph.getFirstRoot(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new VariableStrings(), ignoreCase);
  }

  // batch processing
  // -------------------------------------------------------------
  /**
   * Postprocess a set of results from the batch processing method
   *
   * TODO: make abstract
   */
  public List<Pair<CoreMap, List<SemgrexMatch>>> postprocessMatches(List<Pair<CoreMap, List<SemgrexMatch>>> matches, boolean keepEmptyMatches) {
    return matches;
  }

  /**
   * Returns a list of matching sentences and each of the matches from those sentences.
   *<br>
   * Non-matching sentences are currently not returned (may change in the future to return an empty list).
   */
  public List<Pair<CoreMap, List<SemgrexMatch>>> matchSentences(List<CoreMap> sentences, boolean keepEmptyMatches) {
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = new ArrayList<>();
    for (CoreMap sentence : sentences) {
      SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
      SemgrexMatcher matcher = matcher(graph);
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
   * <p>
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
    // args = argsMap.get(null);

    if (!(argsMap.containsKey(PATTERN)) || argsMap.get(PATTERN).length == 0) {
      help();
      System.exit(2);
    }
    SemgrexPattern semgrex;
    try {
      String pattern = IOUtils.slurpFile(argsMap.get(PATTERN)[0]);
      semgrex = SemgrexPattern.compile(pattern);
    } catch(IOException e) {
      semgrex = SemgrexPattern.compile(argsMap.get(PATTERN)[0]);
    }

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
      useExtras = Boolean.parseBoolean(argsMap.get(EXTRAS)[0]);
    }

    List<CoreMap> sentences = new ArrayList<>();
    if (argsMap.containsKey(TREE_FILE) && argsMap.get(TREE_FILE).length > 0) {
      for (String treeFile : argsMap.get(TREE_FILE)) {
        log.info("Loading file " + treeFile);
        MemoryTreebank treebank = new MemoryTreebank(new TreeNormalizer());
        treebank.loadPath(treeFile);
        for (Tree tree : treebank) {
          // TODO: allow other languages... this defaults to English
          SemanticGraph graph = SemanticGraphFactory.makeFromTree(tree, mode, useExtras ?
                  GrammaticalStructure.Extras.MAXIMAL : GrammaticalStructure.Extras.NONE);
          CoreMap sentence = new ArrayCoreMap();
          sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
          List<CoreLabel> tokens = graph.vertexListSorted().stream().map(x -> x.backingLabel()).collect(Collectors.toList());
          sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
          sentences.add(sentence);
        }
      }
    }

    if (argsMap.containsKey(CONLLU_FILE) && argsMap.get(CONLLU_FILE).length > 0) {
      try {
        CoNLLUReader reader = new CoNLLUReader();
        for (String conlluPath : argsMap.get(CONLLU_FILE)) {
          File file = new File(conlluPath);
          List<File> filenames;
          if (file.isFile()) {
            filenames = Collections.singletonList(file);
          } else {
            filenames = Arrays.asList(file.listFiles());
          }
          for (File conlluFile : filenames) {
            log.info("Loading file " + conlluFile);
            List<Annotation> docs = reader.readCoNLLUFile(conlluFile.toString());
            for (Annotation doc : docs) {
              sentences.addAll(doc.get(CoreAnnotations.SentencesAnnotation.class));
            }
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
        if (graph.vertexListSorted().isEmpty()) {
          continue;
        }
        System.out.printf("+%d %s%n", graph.vertexListSorted().get(0).get(CoreAnnotations.LineNumberAnnotation.class),
            argsMap.get(CONLLU_FILE)[0]);
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
