package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred.SsurgPred;
import edu.stanford.nlp.semgraph.semgrex.*;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * This represents a source pattern and a subsequent edit script, or a sequence
 * of successive in-place edits to perform on a SemanticGraph.  
 *
 * Though the SemgrexMatcher resulting from the Semgrex match over the 
 * SemanticGraph is available to the edit, currently the nodes and edges to be affected 
 * should be named, in order for the edits to identify nodes easily.  See the constructor
 * for each edit type for appropriate syntax.
 * 
 * NOTE: the edits are currently destructive.  If you wish to preserve your graph, make a copy. 
 * @author yeh1
 *
 */
public class SsurgeonPattern {
  /** An identifier for this pattern */
  protected String UID;
  /** Free text notes describing this pattern */
  protected String notes = "";
  /** The language used when parsing relation names in the edits */
  protected Language language = Language.English;
  /** The edits to apply, in order, to each match */
  protected List<SsurgeonEdit> editScript;
  /** The pattern which finds the places to edit */
  protected SemgrexPattern semgrexPattern;
  /** The graph the semgrex pattern was derived from, if any */
  protected SemanticGraph semgrexGraph = null; // Source graph semgrex pattern was derived from (used for pattern learning)
  /** A test which a match must pass for the edits to be applied, or null for no test */
  protected SsurgPred predicateTest = null; // Predicate tests to apply, if non-null, must return true to execute.

  // NodeMap is used to maintain a list of named nodes outside of the set in the SemgrexMatcher.
  // Primarily for newly inserted nodes.
  private Map<String, IndexedWord> nodeMap = null;
  
  /**
   * Creates a pattern with the given edit script.  The list is used
   * directly, and the edits' owning pattern is not set.
   *
   * @param UID an identifier for this pattern
   * @param pattern the semgrex pattern which finds the places to edit
   * @param editScript the edits to apply to each match
   */
  public SsurgeonPattern(String UID, SemgrexPattern pattern, List<SsurgeonEdit> editScript) {
    semgrexPattern = pattern;
    this.UID = UID;
    this.editScript = editScript;
  }

  /**
   * Creates a pattern with an empty edit script.
   *
   * @param UID an identifier for this pattern
   * @param pattern the semgrex pattern which finds the places to edit
   */
  public SsurgeonPattern(String UID, SemgrexPattern pattern) {
    this.UID = UID;
    this.semgrexPattern = pattern;
    this.editScript = new ArrayList<>();
  }

  /**
   * Creates a pattern with an empty edit script, recording the graph the pattern came from.
   *
   * @param UID an identifier for this pattern
   * @param pattern the semgrex pattern which finds the places to edit
   * @param patternGraph the graph the semgrex pattern was derived from
   */
  public SsurgeonPattern(String UID, SemgrexPattern pattern, SemanticGraph patternGraph) {
    this(UID, pattern);
    this.semgrexGraph = patternGraph;
  }

  /**
   * Creates a pattern with the given edit script, using the semgrex pattern's text as the UID.
   *
   * @param pattern the semgrex pattern which finds the places to edit
   * @param editScript the edits to apply to each match; used directly
   */
  public SsurgeonPattern(SemgrexPattern pattern, List<SsurgeonEdit> editScript) {
    this(pattern.toString(), pattern, editScript);
  }

  /**
   * Creates a pattern with an empty edit script, using the semgrex pattern's text as the UID.
   *
   * @param pattern the semgrex pattern which finds the places to edit
   */
  public SsurgeonPattern(SemgrexPattern pattern) {
    this(pattern.toString(), pattern);
  }

  /**
   * Creates a pattern with an empty edit script, using the semgrex
   * pattern's text as the UID and recording the graph the pattern came from.
   *
   * @param pattern the semgrex pattern which finds the places to edit
   * @param patternGraph the graph the semgrex pattern was derived from
   */
  public SsurgeonPattern(SemgrexPattern pattern, SemanticGraph patternGraph) {
    this(pattern);
    this.semgrexGraph = patternGraph;
  }

  /**
   * Sets a test which each match must pass for the edits to be applied.
   *
   * @param predicateTest the test, or null for no test
   */
  public void setPredicate(SsurgPred predicateTest) {
    this.predicateTest = predicateTest;
  }

  /**
   * Appends an edit to the edit script and sets this as the edit's owning pattern.
   *
   * @param newEdit the edit to add
   */
  public void addEdit(SsurgeonEdit newEdit) {
    newEdit.setOwningPattern(this);
    editScript.add(newEdit);
  }

  /**
   * Adds the node to the set of named nodes registered, using the given name.
   * The set is created fresh for each match by the execute and iterate
   * methods, so calling this before either of them throws a NullPointerException.
   *
   * @param node the node to register
   * @param name the name to register it under
   */
  public void addNamedNode(IndexedWord node, String name) {
    nodeMap.put(name, node);
  }
  
  /**
   * Returns a node registered with {@link #addNamedNode}.
   * As with that method, this throws a NullPointerException before the pattern has been executed.
   *
   * @param name the name the node was registered under
   * @return the node, or null if no node has that name
   */
  public IndexedWord getNamedNode(String name) {
    return nodeMap.get(name);
  }
  
  @Override
  public String toString() {
    StringWriter buf = new StringWriter();
    buf.append("Semgrex Pattern: UID=");
    buf.write(getUID());
    buf.write("\nNotes: ");
    buf.write(getNotes());
    buf.write("\n");
    buf.append(semgrexPattern.toString());
    if (predicateTest != null) {
      buf.write("\nPredicate: ");
      buf.write(predicateTest.toString());
    }
    buf.append("\nEdit script:\n");
    for (SsurgeonEdit edit : editScript) {
      buf.append("\t");
      buf.append(edit.toString());
      buf.append("\n");
    }
    return buf.toString();
  }

  /** 
   * Executes the given sequence of edits against the SemanticGraph. 
   * 
   *  Each match which passes the predicate test is applied to its own copy of the graph,
   *  so the edits for one match do not see those for another.  The copies share
   *  the original's nodes, so edits which change a node (such as its word) also
   *  change {@code sg}.  Matching stops entirely at the first match which binds
   *  two names to the same node.
   *  
   *  TODO: create variant that returns set of expansions while matcher.find() returns true
   * @param sg SemanticGraph to operate over; its edges are not modified
   * @return one edited graph for each match
   */
  public Collection<SemanticGraph> execute(SemanticGraph sg) {
    Collection<SemanticGraph> generated = new ArrayList<>();
    SemgrexMatcher matcher = semgrexPattern.matcher(sg);
    nextMatch:
    while (matcher.find()) {
      // NOTE: Semgrex can match two named nodes to the same node.  In this case, we simply,
      // check the named nodes, and if there are any collisions, we throw out this match.
      Set<String> nodeNames = matcher.getNodeNames();
      Set<IndexedWord> seen = Generics.newHashSet();
      for (String name : nodeNames) {
        IndexedWord curr = matcher.getNode(name);
        if (seen.contains(curr))
          break nextMatch;
        seen.add(curr);
//        System.out.println("REDUNDANT NODES FOUDN IN SEMGREX MATCH");
      }
      
      // if we do have to test, assemble the tests and arguments based off of the current
      // match and test.  If false, continue, else execute as normal.
      if (predicateTest != null) {        
        if (!predicateTest.test(matcher))
          continue;
      }
//      SemanticGraph tgt = new SemanticGraph(sg);
      // Generate a new graph, since we don't want to mutilate the original graph.
      // We use the same nodes, since the matcher operates off of those.
      SemanticGraph tgt = SemanticGraphFactory.duplicateKeepNodes(sg);
      nodeMap = Generics.newHashMap();
      for (SsurgeonEdit edit : editScript) {      
        edit.evaluate(tgt, matcher);
      }
      generated.add(tgt);
    }
    return generated;
  }

  /**
   * This alternative processing style repeatedly matches the graph
   * and executes patterns until all of the matches are exhausted and
   * there are no more edits performed.
   *<br>
   * Note that this means "bomb" patterns will go infinite.  In
   * particular, adding a node without a check that the node already
   * exists is a problem.  Most other patterns are self-limiting, in
   * that the change will not be repeated more than once.
   *<br>
   * The graph is always copied, although operations which change the
   * text or otherwise edit a word node will affect the original graph.
   *<br>
   * It's not clear what to do with a multiple edit pattern.
   * Currently we iterate through multiple patterns.  If any of them fire,
   * we rerun the semgrex and restart.
   * There are a couple issues to doing this:
   * <ul>
   * <li> what do we do when an edit doesn't fire?  keep going or break?
   *   Currently we continue and give the other edits an opportunity to fire
   * <li> what node names do the later edits get?  rearranging nodes
   *   may change the indices, affecting the match.  currently we reindex
   *   and update the SemgrexMatcher when inserting new nodes.
   * </ul>
   *
   * @param sg the graph to edit; it is copied, but word nodes are shared as described above
   * @return the edited copy, and whether any edit changed it
   */
  public Pair<SemanticGraph, Boolean> iterate(SemanticGraph sg) {
    SemanticGraph copied = new SemanticGraph(sg);

    SemgrexMatcher matcher = semgrexPattern.matcher(copied);
    boolean anyChanges = false;
    while (matcher.find()) {
      // We reset the named node map with each edit set, since these edits
      // should exist in a separate graph for each unique Semgrex match.
      nodeMap = Generics.newHashMap();
      boolean edited = false;
      for (SsurgeonEdit edit : editScript) {
        if (edit.evaluate(copied, matcher)) {
          edited = true;
          anyChanges = true;
        }
      }
      if (edited) {
        matcher = semgrexPattern.matcher(copied);
      }
    }
    return new Pair<>(copied, anyChanges);
  }

  /**
   * Executes the Ssurgeon edit, but with the given Semgrex Pattern, instead of the one attached to this
   * pattern.
   * Each match which passes the predicate test is applied to its own copy of {@code sg}.
   * 
   * NOTE: Predicate tests are still active here, and any named nodes required for evaluation must be
   * present.
   *
   * @param sg the graph to match against
   * @param overridePattern the pattern to use in place of this pattern's semgrex pattern
   * @return one edited graph for each match
   * @throws Exception declared, but no checked exception is thrown by this method
   */
  public Collection<SemanticGraph> execute(SemanticGraph sg, SemgrexPattern overridePattern) throws Exception {
    SemgrexMatcher matcher = overridePattern.matcher(sg);
    Collection<SemanticGraph> generated = new ArrayList<>();
    while (matcher.find()) {
      if (predicateTest != null) {        
        if (!predicateTest.test(matcher))
          continue;
      }
      // We reset the named node map with each edit set, since these edits
      // should exist in a separate graph for each unique Semgrex match.
      nodeMap = Generics.newHashMap();
      SemanticGraph tgt = new SemanticGraph(sg);
      for (SsurgeonEdit edit : editScript) {      
        edit.evaluate(tgt, matcher);
      }
      generated.add(tgt);
    }
    return generated;
  }


  /**
   * Returns the pattern which finds the places to edit.
   *
   * @return the semgrex pattern
   */
  public SemgrexPattern getSemgrexPattern() {
    return semgrexPattern;
  }

  /* ------
   * XML output and input
   * ------ */
  /** XML element containing a list of Ssurgeon patterns */
  public static final String ELT_LIST_TAG = "ssurgeon-pattern-list";
  /** XML element for a pattern's UID */
  public static final String UID_ELEM_TAG = "uid";
  /** XML element for a pattern's language */
  public static final String LANGUAGE_TAG = "language";
  /** XML element for a resource, such as a word list, used by predicate tests */
  public static final String RESOURCE_TAG = "resource";
  /** XML element for a single Ssurgeon pattern */
  public static final String SSURGEON_ELEM_TAG = "ssurgeon-pattern";
  /** XML element for the semgrex pattern of a Ssurgeon pattern */
  public static final String SEMGREX_ELEM_TAG = "semgrex";
  /** XML element for the graph a semgrex pattern was derived from */
  public static final String SEMGREX_GRAPH_ELEM_TAG = "semgrex-graph";
  /** XML element for a pattern's predicate test */
  public static final String PREDICATE_TAG = "predicate";
  /** XML element for a predicate which requires all of its children to pass */
  public static final String PREDICATE_AND_TAG = "and";
  /** XML element for a predicate which requires any of its children to pass */
  public static final String PREDICATE_OR_TAG = "or";
  /** XML element for a predicate which tests a node against a word list */
  public static final String PRED_WORDLIST_TEST_TAG = "wordlist-test";
  /** XML attribute giving the id of a word list test */
  public static final String PRED_ID_ATTR = "id";
  /** XML element for a pattern's notes */
  public static final String NOTES_ELEM_TAG = "notes";
  /** XML element for one edit when reading patterns; the writer instead puts all of the edits in one such element */
  public static final String EDIT_LIST_ELEM_TAG = "edit-list";
  /** XML element for one edit inside the edit list, used only when writing patterns */
  public static final String EDIT_ELEM_TAG = "edit";
  /** XML attribute giving the position of a pattern or edit when writing */
  public static final String ORDINAL_ATTR = "ordinal";

  /**
   * Returns the edits of this pattern.
   *
   * @return the edit script itself, not a copy
   */
  public List<SsurgeonEdit> getEditScript() {
    return editScript;
  }

  /**
   * Returns the graph the semgrex pattern was derived from.
   *
   * @return the source graph, or null if none was given
   */
  public SemanticGraph getSemgrexGraph() {
    return semgrexGraph;
  }

  /**
   * Returns the notes describing this pattern.
   *
   * @return the notes
   */
  public String getNotes() {
    return notes;
  }

  /**
   * Sets the notes describing this pattern.
   *
   * @param notes the notes
   */
  public void setNotes(String notes) {
    this.notes = notes;
  }

  /**
   * Returns the identifier of this pattern.
   *
   * @return the UID
   */
  public String getUID() {
    return UID;
  }

  /**
   * Sets the identifier of this pattern.
   *
   * @param uid the UID
   */
  public void setUID(String uid) {
    UID = uid;
  }

  /**
   * Returns the language used when parsing relation names in the edits.
   *
   * @return the language, which may be null if set to an unknown name
   */
  public Language getLanguage() {
    return language;
  }

  /**
   * Sets the language, looked up case insensitively by name.
   * An unknown name sets the language to null.
   *
   * @param language the name of the language
   */
  public void setLanguage(String language) {
    // might be null if the language doesn't exist
    this.language = Language.valueOfSafe(language);
  }

  /**
   * Simply reads the given Ssurgeon pattern from file (args[0]), parses it, and prints it out.
   * Use this for debugging the class and patterns. 
   * Any further arguments are compact semantic graphs to run the patterns on.
   *
   * @param args the pattern file, followed by optional graphs
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: SsurgeonPattern FILEPATH [\"COMPACT_SEMANTIC_GRAPH\"], FILEPATH=path to ssurgeon pattern to parse and print., SENTENCE=test sentence (in quotes)");
      System.exit(-1);
    }

    File tgtFile = new File(args[0]);
    try {
      Ssurgeon.inst().initLog(new File("./ssurgeon.log"));
      Ssurgeon.inst().setLogPrefix("SsurgeonPattern test");
      List<SsurgeonPattern> patterns = Ssurgeon.inst().readFromFile(tgtFile);
      for (SsurgeonPattern pattern : patterns) {
        System.out.println("- - - - -");
        System.out.println(pattern);
      }
      if (args.length > 1) {
        for (int i=1; i<args.length;i++) {
          String text = args[i];
          SemanticGraph sg = SemanticGraph.valueOf(text);
          Collection<SemanticGraph> generated = Ssurgeon.inst().exhaustFromPatterns(patterns, sg);
          System.out.println("\n= = = = = = = = = =\nSrc text = "+text);
          System.out.println(sg.toCompactString());
          System.out.println("# generated  = "+generated.size());
          for (SemanticGraph genSg : generated) {
            System.out.println(genSg);
            System.out.println(". . . . .");
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
