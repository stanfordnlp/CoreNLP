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
  protected String UID;
  protected String notes = "";
  protected Language language = Language.English;
  protected List<SsurgeonEdit> editScript;
  protected SemgrexPattern semgrexPattern;
  protected SemanticGraph semgrexGraph = null; // Source graph semgrex pattern was derived from (used for pattern learning)
  protected SsurgPred predicateTest = null; // Predicate tests to apply, if non-null, must return true to execute.

  // NodeMap is used to maintain a list of named nodes outside of the set in the SemgrexMatcher.
  // Primarily for newly inserted nodes.
  private Map<String, IndexedWord> nodeMap = null;
  
  public SsurgeonPattern(String UID, SemgrexPattern pattern, List<SsurgeonEdit> editScript) {
    semgrexPattern = pattern;
    this.UID = UID;
    this.editScript = editScript;
  }

  public SsurgeonPattern(String UID, SemgrexPattern pattern) {
    this.UID = UID;
    this.semgrexPattern = pattern;
    this.editScript = new ArrayList<>();
  }

  public SsurgeonPattern(String UID, SemgrexPattern pattern, SemanticGraph patternGraph) {
    this(UID, pattern);
    this.semgrexGraph = patternGraph;
  }

  public SsurgeonPattern(SemgrexPattern pattern, List<SsurgeonEdit> editScript) {
    this(pattern.toString(), pattern, editScript);
  }

  public SsurgeonPattern(SemgrexPattern pattern) {
    this(pattern.toString(), pattern);
  }

  public SsurgeonPattern(SemgrexPattern pattern, SemanticGraph patternGraph) {
    this(pattern);
    this.semgrexGraph = patternGraph;
  }

  public void setPredicate(SsurgPred predicateTest) {
    this.predicateTest = predicateTest;
  }

  public void addEdit(SsurgeonEdit newEdit) {
    newEdit.setOwningPattern(this);
    editScript.add(newEdit);
  }

  /**
   * Adds the node to the set of named nodes registered, using the given name.
   */
  public void addNamedNode(IndexedWord node, String name) {
    nodeMap.put(name, node);
  }
  
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
   *  NOTE: because the graph could be destructively modified, the matcher may be invalid, and
   *  thus the pattern will only be executed against the first match.  Repeat this routine on the returned
   *  SemanticGraph to reapply on other matches.
   *  
   *  TODO: create variant that returns set of expansions while matcher.find() returns true
   * @param sg SemanticGraph to operate over (NOT destroyed/modified).
   * @return List of the generated matches
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
   * 
   * NOTE: Predicate tests are still active here, and any named nodes required for evaluation must be
   * present.
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


  public SemgrexPattern getSemgrexPattern() {
    return semgrexPattern;
  }

  /* ------
   * XML output and input
   * ------ */
  public static final String ELT_LIST_TAG = "ssurgeon-pattern-list";
  public static final String UID_ELEM_TAG = "uid";
  public static final String LANGUAGE_TAG = "language";
  public static final String RESOURCE_TAG = "resource";
  public static final String SSURGEON_ELEM_TAG = "ssurgeon-pattern";
  public static final String SEMGREX_ELEM_TAG = "semgrex";
  public static final String SEMGREX_GRAPH_ELEM_TAG = "semgrex-graph";
  public static final String PREDICATE_TAG = "predicate";
  public static final String PREDICATE_AND_TAG = "and";
  public static final String PREDICATE_OR_TAG = "or";
  public static final String PRED_WORDLIST_TEST_TAG = "wordlist-test";
  public static final String PRED_ID_ATTR = "id";
  public static final String NOTES_ELEM_TAG = "notes";
  public static final String EDIT_LIST_ELEM_TAG = "edit-list";
  public static final String EDIT_ELEM_TAG = "edit";
  public static final String ORDINAL_ATTR = "ordinal";

  public List<SsurgeonEdit> getEditScript() {
    return editScript;
  }

  public SemanticGraph getSemgrexGraph() {
    return semgrexGraph;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getUID() {
    return UID;
  }

  public void setUID(String uid) {
    UID = uid;
  }

  public Language getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    // might be null if the language doesn't exist
    this.language = Language.valueOfSafe(language);
  }

  /**
   * Simply reads the given Ssurgeon pattern from file (args[0]), parses it, and prints it out.
   * Use this for debugging the class and patterns. 
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
