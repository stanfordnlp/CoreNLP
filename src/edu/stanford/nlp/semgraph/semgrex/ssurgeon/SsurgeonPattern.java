package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred.SsurgPred;
import edu.stanford.nlp.semgraph.semgrex.*;
import edu.stanford.nlp.trees.ud.CoNLLUDocumentWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

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
  private static final Redwood.RedwoodChannels log = Redwood.channels(SsurgeonPattern.class);

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
   * Creates a pattern with the given edit script, and sets this as the
   * owning pattern of each edit, as {@link #addEdit} does.  The list is
   * used directly.
   *
   * @param UID an identifier for this pattern
   * @param pattern the semgrex pattern which finds the places to edit
   * @param editScript the edits to apply to each match
   */
  public SsurgeonPattern(String UID, SemgrexPattern pattern, List<SsurgeonEdit> editScript) {
    semgrexPattern = pattern;
    this.UID = UID;
    this.editScript = editScript;
    for (SsurgeonEdit edit : editScript) {
      edit.setOwningPattern(this);
    }
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
      SemgrexGraphs tgtGraphs = new SemgrexGraphs(tgt);
      for (SsurgeonEdit edit : editScript) {      
        edit.evaluate(tgtGraphs, matcher);
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
   * @throws IllegalStateException if the pattern names a graph other than the basic graph
   */
  public Pair<SemanticGraph, Boolean> iterate(SemanticGraph sg) {
    return iterate(sg, null);
  }

  /**
   * Repeatedly matches and edits the basic graph of a sentence, as
   * {@link #iterate(SemanticGraph)} does, with the enhanced graph of the
   * same sentence available to the semgrex pattern.
   *<br>
   * The pattern can search the enhanced graph with relations such as
   * {@code >nsubj@enhanced}.  Only the basic graph is edited.  The two
   * graphs normally share their words, as when read from CoNLL-U, so an
   * edit which changes a word, such as EditNode, changes it in both, and
   * that includes a word matched only in the enhanced graph.
   *<br>
   * Once the edits change which words the basic graph has, or their
   * indices, the enhanced graph no longer describes the same words.
   * Matching then continues on the basic graph alone, or stops if the
   * pattern needs the enhanced graph.
   *
   * @param sg the basic graph to edit; it is copied, but word nodes are shared
   * @param enhanced the enhanced graph of the same sentence, which is not edited, or null if there is none
   * @return the edited copy of the basic graph, and whether any edit changed it
   * @throws IllegalStateException if the pattern names a graph the sentence has not got
   */
  public Pair<SemanticGraph, Boolean> iterate(SemanticGraph sg, SemanticGraph enhanced) {
    SemanticGraph copied = new SemanticGraph(sg);
    List<IndexedWord> words = copied.vertexListSorted();
    List<String> indices = wordIndices(words);

    // the edits work on the same graphs the matcher searches
    SemgrexGraphs graphs = SemgrexGraphs.of(copied, enhanced);
    SemgrexMatcher matcher = semgrexPattern.matcher(graphs);
    boolean anyChanges = false;
    while (matcher.find()) {
      // We reset the named node map with each edit set, since these edits
      // should exist in a separate graph for each unique Semgrex match.
      nodeMap = Generics.newHashMap();
      boolean edited = false;
      for (SsurgeonEdit edit : editScript) {
        if (edit.evaluate(graphs, matcher)) {
          edited = true;
          anyChanges = true;
        }
      }
      if (edited) {
        if (enhanced != null && !sameWords(words, indices, copied)) {
          enhanced = null;
          if (searchesBeyondBasic()) {
            break;
          }
        }
        graphs = SemgrexGraphs.of(copied, enhanced);
        matcher = semgrexPattern.matcher(graphs);
      }
    }
    return new Pair<>(copied, anyChanges);
  }

  /**
   * Whether the semgrex pattern searches any graph other than the basic graph.
   *
   * @return true if a relation of the pattern names another graph, such as the enhanced graph
   */
  public boolean searchesBeyondBasic() {
    for (SemgrexGraphName name : semgrexPattern.requiredGraphs()) {
      if (name != SemgrexGraphName.BASIC) {
        return true;
      }
    }
    return false;
  }

  /** The index of each word, as {@link IndexedWord#toCopyOrEmptyIndex} gives it */
  private static List<String> wordIndices(List<IndexedWord> words) {
    List<String> indices = new ArrayList<>();
    for (IndexedWord word : words) {
      indices.add(word.toCopyOrEmptyIndex());
    }
    return indices;
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
      SemgrexGraphs tgtGraphs = new SemgrexGraphs(tgt);
      for (SsurgeonEdit edit : editScript) {      
        edit.evaluate(tgtGraphs, matcher);
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

  private static final String EDIT_FILE = "-editFile";
  private static final String SEMGREX = "-semgrex";
  private static final String EDIT = "-edit";
  private static final String CONLLU_FILE = "-conlluFile";
  private static final String OUTPUT = "-output";
  private static final String STDOUT = "-stdout";
  private static final String LANGUAGE_OPTION = "-language";
  private static final String DEFAULT_LANGUAGE = "UniversalEnglish";

  /** Flags which take one argument; the rest take everything up to the next flag */
  private static final Set<String> SINGLE_VALUED =
    Collections.unmodifiableSet(new HashSet<>(Arrays.asList(EDIT_FILE, SEMGREX, EDIT, OUTPUT, LANGUAGE_OPTION)));

  /** Flags which take no arguments */
  private static final Set<String> NO_VALUE = Collections.singleton(STDOUT);

  /** Logs the command line arguments {@link #main} accepts. */
  public static void help() {
    log.info("Possible arguments for SsurgeonPattern:");
    log.info(EDIT_FILE + ": a file of Ssurgeon patterns.  May be given more than once; the files are applied in the order given.");
    log.info("  Blocks of lines are separated by blank lines.  The first line of each block is a semgrex pattern,");
    log.info("  and each following line is an edit to apply to its matches.  Lines starting with # are comments.");
    log.info("  This is the same format as the edit files of Stanza's ssurgeon.py.");
    log.info("  A file starting with < is read as Ssurgeon XML instead.");
    log.info(SEMGREX + ": a semgrex pattern to use instead of, or after, the edit files");
    log.info(EDIT + ": an edit to apply to the matches of " + SEMGREX + ".  May be given more than once; the edits are applied in order.");
    log.info(CONLLU_FILE + ": one or more CoNLL-U files to edit.");
    log.info("  Each may be a file, a directory, or a pattern such as 'es_gsd-ud-*.conllu'.");
    log.info("  Files may also be named after the flags, with no flag of their own.");
    log.info(OUTPUT + ": where to write the edited files.  With one input file, this is the output file,");
    log.info("  unless it is an existing directory.  Otherwise, it is a directory, and each file is written under its own name.");
    log.info(STDOUT + ": print the edited files to stdout");
    log.info("  Without " + OUTPUT + " or " + STDOUT + ", each edited file is written back over its input.");
    log.info("  A file is only rewritten if the edits changed at least one of its sentences.");
    log.info(LANGUAGE_OPTION + ": the language used for relation names in the edits.  Default " + DEFAULT_LANGUAGE);
    log.info();
    log.info("The semgrex patterns can search the enhanced dependencies as well as the basic, as in >nsubj@enhanced,");
    log.info("but only the basic dependencies are edited.  The enhanced dependencies are written unchanged,");
    log.info("unless the edits change the words of a sentence, in which case that sentence's enhanced dependencies are dropped.");
    log.info();
    log.info("For example:");
    log.info("  java edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonPattern -editFile update_que_cconj.txt UD_Spanish-GSD");
    log.info();
    log.info("Given only an XML pattern file and compact semantic graphs, with no flags, the patterns are printed and");
    log.info("then applied to each graph, printing all of the results.");
  }

  /**
   * Reads the patterns named on the command line: the edit files in the
   * order given, then the pattern from {@code -semgrex} and {@code -edit}.
   */
  private static List<SsurgeonPattern> readPatterns(Map<String, List<String>> argsMap) throws IOException {
    String language = SemgrexUtils.firstArg(argsMap, LANGUAGE_OPTION, DEFAULT_LANGUAGE);
    if (Language.valueOfSafe(language) == null) {
      throw new IllegalArgumentException("Unknown language " + language);
    }

    List<SsurgeonPattern> patterns = new ArrayList<>();
    for (String editFile : argsMap.getOrDefault(EDIT_FILE, Collections.emptyList())) {
      String text = IOUtils.slurpFile(editFile, "utf-8");
      if (text.trim().startsWith("<")) {
        patterns.addAll(Ssurgeon.inst().readFromString(text));
      } else {
        patterns.addAll(Ssurgeon.readFromEditText(text, language));
      }
    }

    List<String> semgrex = argsMap.getOrDefault(SEMGREX, Collections.emptyList());
    List<String> edits = argsMap.getOrDefault(EDIT, Collections.emptyList());
    if (semgrex.size() > 1) {
      throw new IllegalArgumentException("Only one " + SEMGREX + " pattern may be given.  Use " + EDIT_FILE + " for more");
    }
    if (semgrex.isEmpty() && !edits.isEmpty()) {
      throw new IllegalArgumentException(EDIT + " was given without a " + SEMGREX + " pattern for it to edit");
    }
    if (!semgrex.isEmpty()) {
      // one block of the edit file format: the semgrex, then each edit on its own line
      List<String> block = new ArrayList<>();
      block.add(semgrex.get(0));
      block.addAll(edits);
      for (SsurgeonPattern pattern : Ssurgeon.readFromEditText(String.join("\n", block), language)) {
        pattern.setUID(SEMGREX);
        patterns.add(pattern);
      }
    }
    return patterns;
  }

  /** Whether the comment is the {@code # text} comment of a CoNLL-U sentence */
  private static boolean isTextComment(String comment) {
    return (comment.startsWith("# text ") || comment.startsWith("#text ") ||
            comment.startsWith("# text=") || comment.startsWith("#text="));
  }

  /**
   * The text of a sentence, rebuilt from the words of its graph.
   *<br>
   * A multiword token contributes its own text rather than that of its
   * words.  Each token is followed by its spaces after, except the last.
   * Empty nodes and copy nodes are skipped.
   *
   * @param graph the graph to read the words from
   * @return the text, as for a {@code # text} comment
   */
  static String sentenceText(SemanticGraph graph) {
    List<IndexedWord> words = new ArrayList<>();
    for (IndexedWord word : graph.vertexListSorted()) {
      if (word.getEmptyIndex() == 0 && word.copyCount() == 0) {
        words.add(word);
      }
    }

    StringBuilder text = new StringBuilder();
    int i = 0;
    while (i < words.size()) {
      IndexedWord word = words.get(i);
      // the last word of the token, which holds the spaces after it
      int last = i;
      String tokenText;
      IntPair span = word.get(CoreAnnotations.CoNLLUTokenSpanAnnotation.class);
      Boolean isFirstOfMWT = word.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class);
      if (span != null) {
        tokenText = word.originalText();
        while (last + 1 < words.size() && words.get(last + 1).index() <= span.getTarget()) {
          ++last;
        }
      } else if (isFirstOfMWT != null && isFirstOfMWT && word.get(CoreAnnotations.MWTTokenTextAnnotation.class) != null) {
        tokenText = word.get(CoreAnnotations.MWTTokenTextAnnotation.class);
        while (last + 1 < words.size() && CoNLLUDocumentWriter.isMWTbutNotStart(words.get(last + 1))) {
          ++last;
        }
      } else {
        tokenText = word.word();
      }
      text.append(tokenText);
      if (last + 1 < words.size()) {
        // no SpaceAfter information at all is written as a plain space
        String after = words.get(last).get(CoreAnnotations.AfterAnnotation.class);
        text.append(after == null ? " " : after);
      }
      i = last + 1;
    }
    return text.toString();
  }

  /**
   * Whether two lists of words are the same objects with the same indices.
   * The enhanced graph shares its words with the basic graph, so this tells
   * whether the enhanced graph still describes the words of the edited basic graph.
   */
  private static boolean sameWords(List<IndexedWord> words, List<String> indices, SemanticGraph graph) {
    List<IndexedWord> current = graph.vertexListSorted();
    if (current.size() != words.size()) {
      return false;
    }
    for (int i = 0; i < words.size(); ++i) {
      if (current.get(i) != words.get(i) || !current.get(i).toCopyOrEmptyIndex().equals(indices.get(i))) {
        return false;
      }
    }
    return true;
  }

  /** The sent_id of a sentence, or else where it starts in its file */
  private static String describeSentence(List<String> comments, SemanticGraph graph, File file) {
    for (String comment : comments) {
      if (comment.startsWith("# sent_id")) {
        return comment.substring(1).trim() + " of " + file;
      }
    }
    List<IndexedWord> words = graph.vertexListSorted();
    if (!words.isEmpty() && words.get(0).get(CoreAnnotations.LineNumberAnnotation.class) != null) {
      return "the sentence at line " + words.get(0).get(CoreAnnotations.LineNumberAnnotation.class) + " of " + file;
    }
    return "a sentence of " + file;
  }

  /**
   * Applies each pattern in turn to the basic dependencies of one sentence,
   * using {@link #iterate(SemanticGraph, SemanticGraph)} so that the
   * patterns can also search the enhanced dependencies, then writes the
   * sentence as CoNLL-U.
   *<br>
   * If the sentence changed, its {@code # text} comment is rebuilt from the
   * edited words.  The enhanced dependencies are written as they are,
   * unless the words of the sentence changed, in which case they no
   * longer fit and are left out.
   *
   * @return the CoNLL-U for the sentence, and whether any pattern changed it
   */
  private static Pair<String, Boolean> processSentence(List<SsurgeonPattern> patterns, CoreMap sentence, File file) {
    SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    List<String> comments = new ArrayList<>();
    if (sentence.get(CoreAnnotations.CommentsAnnotation.class) != null) {
      comments.addAll(sentence.get(CoreAnnotations.CommentsAnnotation.class));
    }

    List<IndexedWord> words = graph.vertexListSorted();
    List<String> indices = wordIndices(words);

    boolean changed = false;
    for (SsurgeonPattern pattern : patterns) {
      // the enhanced graph describes the words as they were read, so it
      // is only searched while the edits have left those words alone
      SemanticGraph searchEnhanced = (enhanced != null && sameWords(words, indices, graph)) ? enhanced : null;
      if (enhanced != null && searchEnhanced == null && pattern.searchesBeyondBasic()) {
        log.warn("Skipping Ssurgeon pattern " + pattern.getUID() + " on " + describeSentence(comments, graph, file) +
                 ": it searches the enhanced graph, which no longer fits now that earlier edits changed the words");
        continue;
      }
      try {
        Pair<SemanticGraph, Boolean> result = pattern.iterate(graph, searchEnhanced);
        graph = result.first();
        changed = changed || result.second();
      } catch (RuntimeException e) {
        throw new RuntimeException("Ssurgeon pattern " + pattern.getUID() + " failed on " + describeSentence(comments, graph, file), e);
      }
    }

    if (changed) {
      if (enhanced != null && !sameWords(words, indices, graph)) {
        log.warn("The edits changed the words of " + describeSentence(comments, graph, file) +
                 ".  Its enhanced dependencies, including any empty nodes, no longer fit and are not written");
        enhanced = null;
      }
      String text = sentenceText(graph);
      for (int i = 0; i < comments.size(); ++i) {
        if (isTextComment(comments.get(i))) {
          comments.set(i, "# text = " + text);
        }
      }
    }

    CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
    return new Pair<>(writer.printSemanticGraph(graph, enhanced, false, comments), changed);
  }

  /**
   * Where each input file is written: null for {@code -stdout}, the
   * {@code -output} file or directory, or else the input file itself.
   */
  private static List<File> outputFiles(List<File> inputs, String output, boolean toStdout) {
    List<File> outputs = new ArrayList<>();
    if (toStdout) {
      for (int i = 0; i < inputs.size(); ++i) {
        outputs.add(null);
      }
    } else if (output == null) {
      outputs.addAll(inputs);
    } else if (inputs.size() == 1 && !new File(output).isDirectory()) {
      outputs.add(new File(output));
    } else {
      File dir = new File(output);
      if (!dir.isDirectory() && !dir.mkdirs()) {
        throw new IllegalArgumentException("Could not make the output directory " + dir);
      }
      Map<String, File> seen = new HashMap<>();
      for (File input : inputs) {
        File previous = seen.put(input.getName(), input);
        if (previous != null) {
          throw new IllegalArgumentException("Both " + previous + " and " + input + " would be written to the same file in " + dir);
        }
        outputs.add(new File(dir, input.getName()));
      }
    }
    return outputs;
  }

  /**
   * Edits CoNLL-U files with Ssurgeon patterns.
   *<br>
   * The patterns come from edit files, in the format described in
   * {@link Ssurgeon#readFromEditText}, or from a single semgrex pattern
   * and its edits on the command line.  Each pattern is applied in turn
   * to each sentence, repeating until it no longer matches anything it
   * changes, as in {@link #iterate(SemanticGraph, SemanticGraph)}.  The
   * patterns can search the enhanced dependencies of a sentence as well
   * as the basic, but only the basic dependencies are edited.  The edited files are written back
   * over the originals unless {@code -output} or {@code -stdout} is given.
   * For example:
   *<pre>{@code
   * java edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonPattern -editFile update_que_cconj.txt UD_Spanish-GSD
   * java edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonPattern -semgrex "{}=source >nsubj {} >csubj=bad {}" -edit "relabelNamedEdge -edge bad -reln advcl" -stdout -conlluFile en_pronouns-ud-test.conllu
   * }</pre>
   * See {@link #help} for all of the arguments.
   *<br>
   * Given no flags at all, the first argument is instead a file of
   * Ssurgeon XML, and any further arguments are compact semantic graphs.
   * The patterns are printed, then applied to each graph with
   * {@link Ssurgeon#exhaustFromPatterns}, printing every graph produced.
   *
   * @param args the command line arguments
   * @throws IOException if a file cannot be read or written
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      help();
      System.exit(2);
    }
    boolean anyFlags = false;
    for (String arg : args) {
      if (arg.startsWith("-")) {
        anyFlags = true;
        break;
      }
    }
    if (!anyFlags) {
      printAndExhaust(args);
      return;
    }

    Map<String, List<String>> argsMap = SemgrexUtils.parseArgs(args, SINGLE_VALUED, NO_VALUE);
    for (String flag : argsMap.keySet()) {
      if (flag != null && !flag.equals(CONLLU_FILE) && !SINGLE_VALUED.contains(flag) && !NO_VALUE.contains(flag)) {
        help();
        throw new IllegalArgumentException("Unknown flag " + flag);
      }
    }

    List<SsurgeonPattern> patterns = readPatterns(argsMap);
    if (patterns.isEmpty()) {
      help();
      throw new IllegalArgumentException("No Ssurgeon patterns given.  Use " + EDIT_FILE + " or " + SEMGREX);
    }

    List<String> conlluPaths = new ArrayList<>(argsMap.getOrDefault(CONLLU_FILE, Collections.emptyList()));
    conlluPaths.addAll(argsMap.getOrDefault(null, Collections.emptyList()));
    List<File> inputs = SemgrexUtils.expandFiles(conlluPaths, ".conllu");
    if (inputs.isEmpty()) {
      throw new IllegalArgumentException("No CoNLL-U files to edit.  Use " + CONLLU_FILE);
    }

    String output = SemgrexUtils.firstArg(argsMap, OUTPUT, null);
    boolean toStdout = argsMap.containsKey(STDOUT);
    if (toStdout && output != null) {
      throw new IllegalArgumentException("Use only one of " + OUTPUT + " and " + STDOUT);
    }
    List<File> outputs = outputFiles(inputs, output, toStdout);

    PrintStream stdout = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "utf-8");
    CoNLLUReader reader = new CoNLLUReader();
    for (int i = 0; i < inputs.size(); ++i) {
      File input = inputs.get(i);
      File outputFile = outputs.get(i);
      log.info("Processing " + input + (outputFile == null ? "" : " to " + outputFile));

      StringBuilder result = new StringBuilder();
      int sentences = 0;
      int changed = 0;
      for (Annotation doc : reader.readCoNLLUFile(input.getPath())) {
        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
          Pair<String, Boolean> processed = processSentence(patterns, sentence, input);
          result.append(processed.first());
          ++sentences;
          if (processed.second()) {
            ++changed;
          }
        }
      }
      log.info("  Changed " + changed + " of " + sentences + " sentences");

      if (outputFile == null) {
        stdout.print(result);
        stdout.flush();
      } else if (changed == 0 && outputFile.equals(input)) {
        // nothing to write back, and leaving the file alone means
        // nothing can change in it which the edits did not ask for
        log.info("  Leaving " + input + " as it is");
      } else {
        Files.write(outputFile.toPath(), result.toString().getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  /**
   * Reads the Ssurgeon XML file in {@code args[0]} and prints its patterns,
   * then applies them to each compact semantic graph in the rest of the
   * arguments, printing every graph produced.
   */
  private static void printAndExhaust(String[] args) {
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
