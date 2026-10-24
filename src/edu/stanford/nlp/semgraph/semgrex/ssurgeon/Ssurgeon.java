package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DateFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.SAXException;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred.*;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.XMLUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * This is the primary class for loading and saving out Ssurgeon patterns.
 * This is also the class that maintains the current list of resources loaded into Ssurgeon: any pattern
 * loaded can reference these resources.
 *<br>
 * An Ssurgeon can be built from an XML pattern or by assembling the pieces by hand.
 *<br>
 * The XML format is as follows:
<pre>
{@code
<ssurgeon-pattern-list>
  <ssurgeon-pattern>
    <uid>...</uid>
    <notes>...</notes>
    <semgrex>...</semgrex>
    <language>...</language>
    <edit-list>...</edit-list>
  </ssurgeon-pattern>
  <table name="...">
    <entry key="..." value="..."/>
  </table>
</ssurgeon-pattern-list>
}
</pre>
 * The {@code uid} is the id of the Ssurgeon operation. <br>
 * The {@code notes} are comments on the Ssurgeon. <br>
 * The {@code semgrex} is a Semgrex pattern to use when matching for this operation. <br>
 * Each {@code edit-list} is one Ssurgeon operation to execute, in order. <br>
 * The {@code language} is an optional field to determine what
 * language formalism to use when making new dependencies.  By default
 * it will be English for SD when using the Java API, although most
 * people probably want UniversalEnglish for UD (including non-English
 * UD datasets) <br>
 * Each {@code table} is a table for {@code remap}, which any pattern of
 * the list can use, as {@link SsurgeonTable} describes. <br>
 *<br>
 * Below, edge means an edge in the Semgrex results, and node refers to a matched word.
 *<br>
 *
 * Available operations and their arguments include:
 * <ul>
 * <li> {@code addEdge -gov node1 -dep node2 -reln depType -weight 0.5 -graph enhanced}
 * <li> {@code relabelNamedEdge -edge edgename -reln depType}
 * <li> {@code removeEdge -gov node1 -dep node2 -reln depType -graph enhanced}
 * <li> {@code removeNamedEdge -edge edgename}
 * <li> {@code reattachNamedEdge -edge edgename -gov gov -dep dep}
 * <li> {@code addDep -gov node1 -reln depType -position where -name name -graph enhanced ...attributes...}
 * <li> {@code addDep -position where -name name ...attributes...}
 * <li> {@code editNode -node node ...attributes...}
 * <li> {@code lemmatize -node node}
 * <li> {@code combineMWT -node node -word word}
 * <li> {@code splitWord -node node -headIndex idx -reln depType -regex w1 -regex w2 ...}
 * <li> {@code setPhraseHead -node n1 -node n2 ... -headIndex idx -reln depType -weight w -graph enhanced}
 * <li> {@code setRoots n1 (n2 n3 ...) -graph enhanced}
 * <li> {@code mergeNodes -node n1 -node n2 ...}
 * <li> {@code killAllIncomingEdges -node node -graph enhanced}
 * <li> {@code deleteLeaf -node node -graph enhanced}
 * <li> {@code killNonRooted -graph basic}
 * <li> {@code reindexGraph}
 * <li> {@code remap -var variable -table table -to variable}
 * </ul>
 *
 *<p>
 * {@code addEdge} adds a new edge between two existing nodes. <br>
 * {@code -gov} and {@code -dep} will be nodes matched by the Semgrex pattern. <br>
 * {@code -reln} is the name of the dependency type to add. <br>
 * {@code -graph} is the graph to add it to, {@code basic} or {@code enhanced}.  The default is {@code basic}. <br>
 * A word which the graph does not have, such as an empty word when adding to the basic graph,
 * or a word an earlier edit deleted, is not added, and the graph is left alone.
 *</p><p>
 * {@code relabelNamedEdge} changes the dependency type of a named edge. <br>
 * {@code edge} is the name of the edge in the Semgrex pattern. <br>
 * {@code -reln} is the name of the dependency type to use.
 *</p><p>
 * {@code removeEdge} deletes an edge based on its description. <br>
 * {@code -gov} is the governor to delete, a named node from the Semgrex pattern. <br>
 * {@code -dep} is the dependent to delete, a named node from the Semgrex pattern. <br>
 * {@code -reln} is the name of the dependency to delete. <br>
 * If {@code -gov} or {@code -dep} are left empty, then all (matching) edges to or from the
 * remaining argument will be deleted. <br>
 * {@code -graph} is the graph to delete it from, {@code basic} or {@code enhanced}.  The default is {@code basic}.
 *</p><p>
 * {@code removeNamedEdge} deletes an edge based on its name. <br>
 * {@code edge} is the name of the edge in the Semgrex pattern.
 *</p><p>
 * {@code reattachNamedEdge} changes an edge's gov and/or dep based on its name. <br>
 * {@code edge} is the name of the edge in the Semgrex pattern. <br>
 * {@code -gov} is the governor to attach to, a named node from the Semgrex pattern.  If left blank, no edit. <br>
 * {@code -dep} is the dependent to attach to, a named node from the Semgrex pattern.  If left blank, no edit. <br>
 * At least one of {@code -gov} or {@code -dep} must be set.
 *</p><p>
 * {@code addDep} adds a word and a dependency arc to the dependency graph. <br>
 * {@code -gov} is the governor to attach to, a named node from the Semgrex pattern. <br>
 * {@code -reln} is the name of the dependency type to use. <br>
 * {@code -position} is where in the sentence the word should go.  {@code -} will be the first word of the sentence,
 *   {@code +} will be the last word of the sentence, and {@code -node} or {@code +node} will be before or after the
 *   named node.  An empty word stays after the word it follows, so a word put after {@code node} goes after any
 *   empty words which follow {@code node}, and a word put before {@code node} goes after any empty words which
 *   follow the word before it.  A word cannot be put directly before an empty word. <br>
 * The word goes into every graph of the sentence.  Its edge goes into each graph which has the governor, or with
 *   {@code -graph}, into that graph alone, {@code basic} or {@code enhanced}. <br>
 * {@code -name} adds the new word to the match under that name, so later edits for the same match can use it.
 *   Each match adds its own word, and the name refers to the word added for that match.
 *   If the match already has a node by that name, nothing is added, and later edits use that node.
 *   With an optional node in the Semgrex pattern, this fills in a word only where one is missing:
 *   {@code {word:antennae}=antennae ?>dep {word:blue}=blue} with
 *   {@code addDep -gov antennae -reln dep -word blue -name blue} adds {@code blue} to antennae which have none,
 *   and stops once it has. <br>
 * Without {@code -gov}, the word is added with no edges, so it needs {@code -name} for later edits to attach it,
 *   such as {@code addEdge}, or to make it a root with {@code setRoots}.
 *   {@code -reln}, {@code -weight} and {@code -graph} are then not allowed. <br>
 * {@code ...attributes...} means any attributes which can be set from a string or numerical value
 *   eg {@code -text ...} sets the text of the word
 *   {@code -pos ...} sets the xpos of the word, {@code -cpos ...} sets the upos of the word, etc.
 *   You cannot set the index of a word this way; an exception will be thrown. <br>
 *   To put whitespace in an attribute, you can quote it. <br>
 *   So, for example, a Vietnamese word can be set as {@code -word "xin chào"}
 *</p><p>
 * {@code editNode} will edit the attributes of a word. <br>
 * {@code -node} is the node to edit. <br>
 * {@code ...attributes...} are the attributes to change, same as with {@code addDep} <br>
 *   {@code -morphofeatures ...} will set the features to be exactly as written. <br>
 *   {@code -updateMorphoFeatures ...} will edit or add the features without overwriting existing features. <br>
 *   {@code -removeMorphoFeatures ...} will remove this one morpho feature. <br>
 *   {@code -remove ...} will remove the attribute entirely, such as doing {@code -remove lemma} to remove the lemma.
 *</p><p>
 * The attribute values of {@code addDep} and {@code editNode}, the features of {@code -updateMorphoFeatures},
 *   and the {@code -reln} of {@code addDep}, {@code addEdge} and {@code relabelNamedEdge}
 *   can refer to variables of the match as {@code ${name}}.  A variable is set by a regex group the Semgrex
 *   pattern captures, written {@code #group%name} after the regex, or by {@code remap}.  For example, to fix
 *   verb lemmas which end in -ir rather than -ar: <br>
 *   {@code {cpos:VERB;lemma:/(.*)ir/#1%stem}=verb} with {@code editNode -node verb -lemma ${stem}ar} <br>
 *   A relation's type can capture groups the same way, which with {@code -reln} makes the subjects of
 *   passive verbs passive: <br>
 *   {@code {}=head >/aux:pass/ {} >/([cn]subj)/#1%etype=edge {}} with
 *   {@code relabelNamedEdge -edge edge -reln ${etype}:pass} <br>
 *   If a variable is not set, the edit does nothing.  {@code $$} is a literal {@code $}, so {@code $${name}}
 *   is the text {@code ${name}}, and any other {@code $}, such as in {@code US$}, is literal as well.
 *</p><p>
 * {@code lemmatize} will put a lemma on a word. <br>
 * {@code -node} is the node to edit. <br>
 *   This only works on English text.
 *</p><p>
 * {@code combineMWT} will add MWT attributes to a sequence of two or more words. <br>
 * {@code -node} (repeated) is the nodes to edit. <br>
 * {@code -word} is the optional text to use for the new MWT.  If not set, the words will be concatenated.
 *</p><p>
 * {@code setPhraseHead} will set a new head for a sequence of nodes.<br>
 * {@code -node} for each node to include in the phrase. <br>
 * {@code -headIndex} is the index (counting from 0) of the node to make the head. <br>
 * {@code -reln} is the name of the dependency type to use to connect the other words in the phrase to the new head <br>
 * {@code -weight} is the weight to give the new edges (probably not particularly important) <br>
 * {@code -graph} is the graph whose edges to rearrange, {@code basic} or {@code enhanced}.  The default is {@code basic}. <br>
 * The words must already be in a phrase for this to work.  This is
 * detected by making sure each node has its parent within the phrase,
 * except for the head word, which can either be the root or have the
 * one edge that goes out from the phrase. <br>
 * This operation reconnects the head of the phrase to the same node that was previously the parent of the phrase. <br>
 * All edges that previously went to a different word in the phrase are now pointed to the new head of the phrase. <br>
 * Some of these behaviors are optional.  If you happen to need a different behavior, please file an issue on github.
 *</p><p>
 * {@code splitWord} will split a single word into multiple pieces from the text of the current word <br>
 * {@code -node} is the node to split. <br>
 * The pieces are counted from 0, in the order they appear, for {@code -headIndex}, {@code -name}, {@code -edge}, and {@code -reln}. <br>
 * {@code -headIndex} is the index (counting from 0) of the word piece to make the head. <br>
 * {@code -reln} is the name of the dependency type to use.  pieces other than the head will connect using this relation. <br>
 *   Or, written as {@code 1=det,2=obj}, the relation for each piece.  Each piece other than the head needs one.
 *   One for the head replaces the relation of its incoming edges, which are otherwise left as they are. <br>
 * {@code -regex} regex must match the matched node.  all matching groups will be concatenated to form a new word.  need at least 2 to split a word <br>
 * {@code -exact} instead of specifying matching regex, can split to exact pieces.  need at least 2 to split a word <br>
 * {@code -siblings true} attaches the new words to the governors of the split node rather than to the split node itself <br>
 * {@code -name} will give names to the newly created nodes.  The format is #=name, comma separated <br>
 * {@code -edge} will give a name to the newly created edges.  Later operations can update those edges.  Index the edges from the dep (since the gov is always the same).
 *   The name refers to the edge in the basic graph. <br>
 * The new words go into every graph of the sentence.  In each graph, they are attached to the head piece, or with
 *   {@code -siblings true}, to each governor the split word has in that graph.  An empty word after the split word
 *   goes after the last piece.
 *</p><p>
 * {@code setRoots} sets the roots of the sentence to a new root. <br>
 * {@code n1, n2, ...} are the names of the nodes from the Semgrex to use as the root(s). <br>
 * {@code -graph} is the graph whose roots to set, {@code basic} or {@code enhanced}.  The default is {@code basic}. <br>
 * This is best done in conjunction with other operations which actually manipulate the structure
 * of the graph, or the new root will weirdly have dependents and the graph will be incorrect.
 *</p><p>
 * {@code mergeNodes} will merge n1 and n2, assuming they are mergeable. <br>
 * The nodes can be merged if one of the nodes is the head of a phrase
 * and the other node depends on the head.
 * Another node with exactly the same parents, with the same relations, can merge into the head as well,
 * and its children outside the phrase move to the head.  The other nodes cannot have edges to words outside the phrase. <br>
 * The nodes are merged in every graph of the sentence which has them, and each of those graphs has to allow
 *   the merge.  The head is the first node which every one of those graphs allows as the head.  The merged word takes the place of the first of the words, and the empty
 *   words which followed any of them follow it.  A name for any of the nodes refers to the merged word afterwards.
 *</p><p>
 * {@code killAllIncomingEdges} deletes all edges to a node. <br>
 * {@code -node} is the node to edit. <br>
 * {@code -graph} is the graph to delete them from, {@code basic} or {@code enhanced}.  The default is {@code basic}. <br>
 * Note that this is the same as {@code removeEdge} with only the dependent set.
 *</p><p>
 * {@code deleteLeaf} deletes a node as long as it is a leaf. <br>
 * {@code -node} is the node to delete. <br>
 * If the node is not a leaf (no outgoing edges), it will not be deleted. <br>
 * The node is deleted from every graph of the sentence which has it, and has to be a leaf in each of them.
 *   With {@code -graph}, only that graph decides whether it is a leaf, {@code basic} or {@code enhanced},
 *   and its edges in the other graphs, outgoing as well as incoming, are deleted along with it. <br>
 * The words after it move down by one.  An empty word stays after the word it follows, so deleting word 5
 *   with 4.1 and 5.1 present makes 5.1 into 4.2, and deleting empty word 2.1 makes 2.2 into 2.1.
 *   Deleting a copy of a word moves nothing, as the word still holds its place. <br>
 * A graph's last root is never deleted: if the node is the only root of a graph which has it, nothing happens.
 *</p><p>
 * {@code killNonRooted} searches the graph and deletes all nodes which have no path to a root. <br>
 * After cutting a piece of the graph off with {@code removeEdge}, this deletes the piece without the root.
 *   To keep that piece instead, give it the root with {@code setRoots} first. <br>
 * A node is deleted from every graph of the sentence, if it has no path to a root in any graph which has it.
 *   With {@code -graph}, only that graph decides, {@code basic} or {@code enhanced}, so after a cut made in
 *   the basic graph alone, {@code killNonRooted -graph basic} deletes the piece from every graph.
 *   A graph's last root is never deleted, and the words left keep their indices.
 *</p><p>
 * {@code reindexGraph} reindexes the graph from 1 in case there are gaps or the node indices start later than 1.  (Warning: does not work for first index less than 1)
 *</p><p>
 * {@code remap} looks up a variable of the match in a table, for the edits after it. <br>
 * {@code -var} is the variable to look up, such as one the Semgrex pattern captured with {@code #0%lemma}. <br>
 * {@code -table} is the table, defined in the same edit file or XML pattern list. <br>
 * {@code -to} is the variable to set to the result.  The default is the variable looked up. <br>
 *   If the table has no entry for the variable, the result is unset, so an edit which uses it does nothing.
 *   This changes nothing in the graphs, so it does not count as a change by itself.  For example, to fix
 *   the verb lemmas a table lists: <br>
 *   {@code {cpos:VERB;lemma:__#0%lemma}=verb}, {@code remap -var lemma -table lemma_mapping},
 *   {@code editNode -node verb -lemma ${lemma}}
 *</p>
 *<p>
 * A practical example comes from the {@code UD_English-Pronouns}
 * dataset, where some words had both {@code nsubj} and {@code csubj}
 * dependencies:
 *<pre>
1	Hers	hers	PRON	PRP	Gender=Fem|Number=Sing|Person=3|Poss=Yes|PronType=Prs	3	nsubj	_	_
2	is	be	AUX	VBZ	Mood=Ind|Number=Sing|Person=3|Tense=Pres|VerbForm=Fin	3	cop	_	_
3	easy	easy	ADJ	JJ	Degree=Pos	0	root	_	_
4	to	to	PART	TO	_	5	mark	_	_
5	clean	clean	VERB	VB	VerbForm=Inf	3	csubj	_	SpaceAfter=No
6	.	.	PUNCT	.	_	5	punct	_	_
</pre>
 *<p>
 * We can update this with the following Semgrex/Ssurgeon pair:
 *<pre>
{@code
{}=source >nsubj {} >csubj=bad {}
relabelNamedEdge -edge bad -reln advcl
}
</pre>
 *<p>
 * The result will be the {@code csubj} updated to {@code advcl}
 *</p><p>
 * For the most part, each of these operations is already bomb-proof,
 * eg the pattern will execute once and not repeat on the same part of
 * the same dependency graph.
 * However, in the case of {@code addDep}, it is not possible to automatically bomb-proof the command,
 * as certain sentences may legitimately have multiple words with the same attributes as dependents
 * of the same governor.  In this case, it is necessary to make the Semgrex pattern itself bomb-proof.
 *</p><p>

 * As an example, if the intent is to change "Jennifer has lovely
 * antennae" to "Jennifer has lovely blue antennae", the following
 * command would "bomb":                                         
<pre>
{@code
  {word:antennae}=antennae
  addDep -gov antennae -reln dep -word blue
}
</pre>
 *<p>
 * The following would not:
<pre>
{@code
  {word:antennae}=antennae !> {word:blue}
  addDep -gov antennae -reln dep -word blue
}
</pre>
 * Some patterns which leave the node in the same format will bomb because of the way the dirty bit works.  For example:
<pre>
{@code
{word:/pattern/;cpos:VERB;morphofeatures:{VerbForm:Inf}}=word
EditNode -node word -remove morphofeatures
EditNode -node word -updatemorphofeatures Aspect=Imp -updatemorphofeatures VerbForm=Inf
}
</pre>
 * Here, the end result will be the same after at most one iteration through the loop,
 * but {@code -remove morphofeatures} sets the dirty bit and does not go away
 * when {@code -updatemorphofeatures} puts back the deleted features.
 * TODO: this one at least can be fixed
 *
 * @author Eric Yeh
 */
public class Ssurgeon  {

  private static final boolean VERBOSE = false;

  // singleton, to ensure all use the same resources
  private static Ssurgeon instance = null;

  private Ssurgeon() {}

  // TODO: get rid of this if possible
  /**
   * Returns the singleton instance, creating it on first use.
   *
   * @return The shared Ssurgeon, which holds the loaded word list resources
   */
  public static Ssurgeon inst() {
    synchronized(Ssurgeon.class) {
      if (instance == null)
        instance = new Ssurgeon();
    }
    return instance;
  }

  // Logging to file facilities.
  // The prefix is used to append stuff in front of the logging messages
  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Ssurgeon.class);

  private String logPrefix = null;
  /**
   * Reconfigures Redwood logging, globally, to write to stderr and to the given file.
   *
   * @param logFilePath The file to log to
   * @throws IOException declared, but no checked exception is thrown by this method
   */
  public void initLog(File logFilePath) throws IOException {
    RedwoodConfiguration.empty()
      .handlers(RedwoodConfiguration.Handlers.chain(
        RedwoodConfiguration.Handlers.showAllChannels(), RedwoodConfiguration.Handlers.stderr),
        RedwoodConfiguration.Handlers.file(logFilePath.toString())
      ).apply();
    // fh.setFormatter(new NewlineLogFormatter());

    System.out.println("Starting Ssurgeon log, at "+logFilePath.getAbsolutePath()+" date=" + DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
    log.info("Starting Ssurgeon log, date=" + DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
  }

  /**
   * Sets a line logged before the results of each pattern, or null for none.
   *
   * @param logPrefix The prefix to log
   */
  public void setLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
  }



  /**
   * Given a list of SsurgeonPattern edit scripts, and a SemanticGraph
   * to operate over, returns a list of expansions of that graph, with
   * the result of each edit applied against a copy of the graph.
   * Each generated graph is also printed to stdout.
   *
   * @param patternList The patterns to apply
   * @param sg The graph to apply them to; not modified
   * @return All of the graphs generated by all of the patterns
   * @throws Exception declared, but no checked exception is thrown by this method
   */
  public  List<SemanticGraph> expandFromPatterns(List<SsurgeonPattern> patternList, SemanticGraph sg) throws Exception {
    List<SemanticGraph> retList = new ArrayList<>();
    for (SsurgeonPattern pattern :patternList) {
      Collection<SemanticGraph> generated = pattern.execute(sg);
      for (SemanticGraph orderedGraph : generated) {
        //orderedGraph.vertexList(true);
        //orderedGraph.edgeList(true);
        retList.add(orderedGraph);
        System.out.println("\ncompact = "+orderedGraph.toCompactString());
        System.out.println("regular=" + orderedGraph);
      }

      if (generated.size() > 0) {
        if (log != null) {
          log.info("* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *");
          log.info("Pre remove duplicates, num="+generated.size());
        }
        SemanticGraphUtils.removeDuplicates(generated, sg);
        if (log != null) {
          log.info("Expand from patterns");
          if (logPrefix != null) log.info(logPrefix);
          log.info("Pattern = '"+pattern.getUID()+"' generated "+generated.size()+" matches");
          log.info("= = = = = = = = = =\nSrc graph:\n" + sg + "\n= = = = = = = = = =\n");
          int index=1;
          for (SemanticGraph genSg : generated) {
            log.info("REWRITE "+(index++));
            log.info(genSg.toString());
            log.info(". . . . .\n");
          }
        }
      }
    }
    return retList;
  }

  /**
   * Similar to the expandFromPatterns, but performs an exhaustive
   * search, performing simplifications on the graphs until exhausted.
   *
   * TODO: ensure cycles do not occur
   * NOTE: put in an arbitrary depth limit of 3, to prevent churning way too much (heuristic)
   *
   * @param patternList The patterns to apply
   * @param sg The graph to apply them to; not modified
   * @return The generated graphs, with duplicates and copies of {@code sg} removed
   *   if more than one graph was generated
   * @throws Exception declared, but no checked exception is thrown by this method
   */
  public  Collection<SemanticGraph> exhaustFromPatterns(List<SsurgeonPattern> patternList, SemanticGraph sg) throws Exception {
    Collection<SemanticGraph> generated = exhaustFromPatterns(patternList, sg, 1);
    if (generated.size() > 1) {
      if (log != null)
        log.info("Before remove dupe, size="+generated.size());
      generated = SemanticGraphUtils.removeDuplicates(generated, sg);
      if (log != null)
        log.info("AFTER remove dupe, size="+generated.size());
    }
    return generated;
  }
  private  List<SemanticGraph> exhaustFromPatterns(List<SsurgeonPattern> patternList, SemanticGraph sg, int depth) throws Exception {
    List<SemanticGraph> retList = new ArrayList<>();
    for (SsurgeonPattern pattern : patternList) {
      Collection<SemanticGraph> generated = pattern.execute(sg);
      for (SemanticGraph modGraph : generated) {
        //modGraph = SemanticGraphUtils.resetVerticeOrdering(modGraph);
        //modGraph.vertexList(true);
        //modGraph.edgeList(true);
        retList.add(modGraph);
      }

      if (log != null && generated.size() > 0) {
        log.info("* * * * * * * * * ** * * * * * * * * *");
        log.info("Exhaust from patterns, depth="+depth);
        if (logPrefix != null) log.info(logPrefix);
        log.info("Pattern = '"+pattern.getUID()+"' generated "+generated.size()+" matches");
        log.info("= = = = = = = = = =\nSrc graph:\n"+sg.toString()+"\n= = = = = = = = = =\n");
        int index=1;
        for (SemanticGraph genSg : generated) {
          log.info("REWRITE "+(index++));
          log.info(genSg.toString());
          log.info(". . . . .\n");
        }
      }
    }

    if (retList.size() > 0) {
      List<SemanticGraph> referenceList = new ArrayList<>(retList);
      for (SemanticGraph childGraph : referenceList) {
        if (depth < 3)
          retList.addAll(exhaustFromPatterns(patternList, childGraph, depth + 1));
      }
    }
    return retList;
  }


  /**
   * Given a path to a file, converts it into a SsurgeonPattern
   * TODO: finish implementing this stub.
   *
   * @param path The file to read; currently ignored
   * @return Always null
   */
  public static SsurgeonPattern getOperationFromFile(String path) {
    return null;
  }

  //
  // Resource management
  //
  private Map<String, SsurgeonWordlist> wordListResources = Generics.newHashMap();

  /**
   * Places the given word list resource under the given ID.
   * Note: can overwrite existing one in place.
   */
  private void addResource(SsurgeonWordlist resource) {
    wordListResources.put(resource.getID(), resource);
  }

  /**
   * Returns the given resource with the id.
   *
   * @param id The ID of the word list
   * @return The word list, or null if there is no resource with that id
   */
  public SsurgeonWordlist getResource(String id) {
    return wordListResources.get(id);
  }

  /**
   * Returns the word list resources loaded so far.
   *
   * @return A live view of the loaded resources
   */
  public Collection<SsurgeonWordlist> getResources() {
    return wordListResources.values();
  }


  /** Flag for the name of the governor node */
  public static final String GOV_NODENAME_ARG = "-gov";
  /** Flag for the name of the dependent node */
  public static final String DEP_NODENAME_ARG = "-dep";
  /** Flag for the name of an edge */
  public static final String EDGE_NAME_ARG = "-edge";
  /** Flag for the name of a node; may be repeated */
  public static final String NODENAME_ARG = "-node";
  /** Flag for a regex describing one piece of a split word; may be repeated */
  public static final String REGEX_ARG = "-regex";
  /** Flag for the exact text of one piece of a split word; may be repeated */
  public static final String EXACT_ARG = "-exact";
  /** Flag for whether split pieces attach to the governors of the split word */
  public static final String SIBLINGS_ARG = "-siblings";
  /** Flag for the name of a dependency relation */
  public static final String RELN_ARG = "-reln";
  /** Flag for the weight of a new edge */
  public static final String WEIGHT_ARG = "-weight";
  /** Flag for the index, counting from 0, of the head of a phrase or split */
  public static final String HEAD_INDEX_ARG = "-headIndex";
  /** Lowercase spelling of {@link #HEAD_INDEX_ARG} */
  public static final String HEAD_INDEX_LOWER_ARG = "-headindex";
  /** Flag for the name(s) to give new nodes */
  public static final String NAME_ARG = "-name";
  /** Flag for where to put a new word */
  public static final String POSITION_ARG = "-position";
  /** Arg which says which graph of the sentence an edit works on, such as {@code enhanced} */
  public static final String GRAPH_ARG = "-graph";
  /** Arg for the variable remap looks up */
  public static final String VAR_ARG = "-var";
  /** Arg for the table remap looks a variable up in */
  public static final String TABLE_ARG = "-table";
  /** Arg for the variable remap sets */
  public static final String TO_ARG = "-to";
  /** Flag for morpho features to add or change without removing the others */
  public static final String UPDATE_MORPHO_FEATURES = "-updateMorphoFeatures";
  /** Lowercase spelling of {@link #UPDATE_MORPHO_FEATURES} */
  public static final String UPDATE_MORPHO_FEATURES_LOWER = "-updatemorphofeatures";
  /** Flag for an attribute to remove from a word; may be repeated */
  public static final String REMOVE = "-remove";
  /** Flag for a morpho feature to remove from a word; may be repeated */
  public static final String REMOVE_MORPHO_FEATURES = "-removeMorphoFeatures";
  /** Lowercase spelling of {@link #REMOVE_MORPHO_FEATURES} */
  public static final String REMOVE_MORPHO_FEATURES_LOWER = "-removemorphofeatures";


  /**
   * Args for Ssurgeon edits, allowing us to not
   * worry about arg order (and to make things appear less confusing)
   */
  protected static class SsurgeonArgs {
    /** Creates an empty set of args, with every value at its default. */
    protected SsurgeonArgs() { }

    // Below are values keyed by Semgrex name
    /** The {@code -gov} node name */
    public String govNodeName = null;

    /** The {@code -dep} node name */
    public String dep = null;

    /** The {@code -edge} name */
    public String edge = null;

    /** The {@code -reln} relation name */
    public String reln = null;

    /** Each {@code -node} name, in order */
    public List<String> nodes = new ArrayList<>();

    /** Each {@code -regex}, in order */
    public List<String> regex = new ArrayList<>();

    /** Each {@code -exact} piece, in order */
    public List<String> exact = new ArrayList<>();
    /** The {@code -siblings} setting */
    public boolean siblings = false;

    // below are string representations of the intended values

    /** The {@code -weight}, or 0 if not given */
    public double weight = 0.0;

    /** The {@code -name} value */
    public String name = null;

    /** The {@code -position} value */
    public String position = null;

    /** The {@code -updateMorphoFeatures} value; if repeated, the last one */
    public String updateMorphoFeatures = null;

    /** The {@code -headIndex}, or null if not given */
    public Integer headIndex = null;

    /** Any other flag which names a CoreAnnotation, keyed by the flag without its dash */
    public Map<String, String> annotations = new TreeMap<>();

    /** Each {@code -remove} attribute, in order */
    public List<String> remove = new ArrayList<>();

    /** Each {@code -removeMorphoFeatures} feature, in order */
    public List<String> removeMorphoFeatures = new ArrayList<>();

    /** The {@code -graph} the edit works on, or null if not given */
    public SemgrexGraphName graph = null;

    /** The {@code -var} value */
    public String variable = null;

    /** The {@code -table} value */
    public String table = null;

    /** The {@code -to} value */
    public String to = null;
  }

  /**
   * This is a specialized args parser, as we want to split on
   * whitespace, but retain everything inside quotes, so we can pass
   * in hashmaps in String form.
   */
  private static List<Pair<String, String>> parseArgs(String argsString) {
    List<String> retList = new ArrayList<>();
    String patternString = "(?:[^\\s\\\"]++|\\\"[^\\\"]*+\\\"|(\\\"))++";
    Pattern pattern = Pattern.compile(patternString);
    Matcher matcher = pattern.matcher(argsString);
    while (matcher.find()) {
      if (matcher.group(1) == null) {
        String matched = matcher.group();
        if (matched.charAt(0) == '"' &&
            matched.charAt(matched.length()-1) == '"')
          retList.add(matched.substring(1, matched.length()-1));
        else
          retList.add(matched);
      }  else
        throw new SsurgeonParseException("Unmatched quote in string to parse");
    }

    List<Pair<String, String>> parsedArgs = new ArrayList<>();
    for (int i = 0; i < retList.size() - 1; i += 2) {
      parsedArgs.add(new Pair<>(retList.get(i), retList.get(i + 1)));
    }
    return parsedArgs;
  }

  /**
   * The graph named by a {@code -graph} argument.
   *
   * @param name the name, such as {@code basic} or {@code enhanced}, in any case
   * @return the graph
   * @throws SsurgeonParseException if there is no graph by that name
   */
  static SemgrexGraphName parseGraphName(String name) {
    SemgrexGraphName graph = SemgrexGraphName.fromName(name);
    if (graph == null) {
      throw new SsurgeonParseException("Unknown graph " + name + " for " + GRAPH_ARG + ": expected one of " + Arrays.toString(SemgrexGraphName.values()));
    }
    return graph;
  }

  /** The edits which take a {@code -graph} argument, in lower case */
  private static final Set<String> GRAPH_EDITS =
    new HashSet<>(Arrays.asList(AddDep.LABEL.toLowerCase(Locale.ROOT),
                                AddEdge.LABEL.toLowerCase(Locale.ROOT),
                                DeleteLeaf.LABEL.toLowerCase(Locale.ROOT),
                                RemoveEdge.LABEL.toLowerCase(Locale.ROOT),
                                KillAllIncomingEdges.LABEL.toLowerCase(Locale.ROOT),
                                SetPhraseHead.LABEL.toLowerCase(Locale.ROOT)));

  private static SsurgeonArgs parseArgsBox(String args, Map<String, String> additionalArgs) {
    SsurgeonArgs argsBox = new SsurgeonArgs();
    List<Pair<String, String>> argsArray = parseArgs(args);
    for (String additional : additionalArgs.keySet()) {
      argsArray.add(new Pair<>("-" + additional, additionalArgs.get(additional)));
    }

    for (Pair<String, String> arg : argsArray) {
      String argsKey = arg.first;
      String argsValue = arg.second;
      switch (argsKey) {
        case GOV_NODENAME_ARG:
          argsBox.govNodeName = argsValue;
          break;
        case DEP_NODENAME_ARG:
          argsBox.dep = argsValue;
          break;
        case EDGE_NAME_ARG:
          argsBox.edge = argsValue;
          break;
        case RELN_ARG:
          argsBox.reln = argsValue;
          break;
        case NODENAME_ARG:
          argsBox.nodes.add(argsValue);
          break;
        case REGEX_ARG:
          argsBox.regex.add(argsValue);
          break;
        case EXACT_ARG:
          argsBox.exact.add(argsValue);
          break;
        case SIBLINGS_ARG:
          argsBox.siblings = Boolean.parseBoolean(argsValue);
          break;
        case WEIGHT_ARG:
          argsBox.weight = Double.valueOf(argsValue);
          break;
        case HEAD_INDEX_ARG:
        case HEAD_INDEX_LOWER_ARG:
          argsBox.headIndex = Integer.valueOf(argsValue);
          break;
        case NAME_ARG:
          argsBox.name = argsValue;
          break;
        case POSITION_ARG:
          argsBox.position = argsValue;
          break;
        case GRAPH_ARG:
          argsBox.graph = parseGraphName(argsValue);
          break;
        case VAR_ARG:
          argsBox.variable = argsValue;
          break;
        case TABLE_ARG:
          argsBox.table = argsValue;
          break;
        case TO_ARG:
          argsBox.to = argsValue;
          break;
        case UPDATE_MORPHO_FEATURES:
        case UPDATE_MORPHO_FEATURES_LOWER:
          argsBox.updateMorphoFeatures = argsValue;
          break;
        case REMOVE:
          argsBox.remove.add(argsValue);
          break;
        case REMOVE_MORPHO_FEATURES:
        case REMOVE_MORPHO_FEATURES_LOWER:
          argsBox.removeMorphoFeatures.add(argsValue);
          break;
        default:
          String key = argsKey.substring(1);
          Class<? extends CoreAnnotation<?>> annotation = AnnotationLookup.toCoreKey(key);
          if (annotation == null) {
            throw new SsurgeonParseException("Parsing Ssurgeon args: unknown flag " + argsKey);
          }
          argsBox.annotations.put(key, argsValue);
      }
    }
    return argsBox;
  }

  /**
   * Given a string entry, converts it into a SsurgeonEdit object.
   *
   * @param editLine The edit, a command word followed by its arguments
   * @param attributeArgs Extra arguments, such as XML attributes of the edit element,
   *   treated as if each key were a flag with a dash in front of it
   * @param language The language used for new relations
   * @param knownEdges The edge names defined so far; edges named by this edit are added to it
   * @return The new edit
   * @throws SsurgeonParseException if the line cannot be parsed
   */
  public static SsurgeonEdit parseEditLine(String editLine, Map<String, String> attributeArgs, Language language, Set<String> knownEdges) {
    return parseEditLine(editLine, attributeArgs, language, knownEdges, Collections.emptyMap());
  }

  /**
   * Given a string entry, converts it into a SsurgeonEdit object, with
   * the tables a {@code remap} can use.
   *
   * @param editLine The edit, a command word followed by its arguments
   * @param attributeArgs Extra arguments, such as XML attributes of the edit element,
   *   treated as if each key were a flag with a dash in front of it
   * @param language The language used for new relations
   * @param knownEdges The edge names defined so far; edges named by this edit are added to it
   * @param tables The tables a {@code remap} can name, by name
   * @return The new edit
   * @throws SsurgeonParseException if the line cannot be parsed, or names a table which is not given
   */
  public static SsurgeonEdit parseEditLine(String editLine, Map<String, String> attributeArgs, Language language, Set<String> knownEdges,
                                           Map<String, SsurgeonTable> tables) {
    try {
      // Extract the operation name first
      final String[] tuples1 = editLine.split("\\s+", 2);
      if (tuples1.length < 1) {
        throw new SsurgeonParseException("Error in SsurgeonEdit.parseEditLine: invalid number of arguments");
      }
      final String command = tuples1[0];

      if (command.equalsIgnoreCase(SetRoots.LABEL)) {
        // the root names are not flags, but -graph can be given among them
        String[] names = tuples1[1].split("\\s+");
        List<String> newRoots = new ArrayList<>();
        SemgrexGraphName graph = attributeArgs.containsKey("graph") ? parseGraphName(attributeArgs.get("graph")) : SemgrexGraphName.BASIC;
        for (int i = 0; i < names.length; ++i) {
          if (names[i].equals(GRAPH_ARG)) {
            if (i + 1 == names.length) {
              throw new SsurgeonParseException(GRAPH_ARG + " needs the name of a graph");
            }
            graph = parseGraphName(names[i + 1]);
            ++i;
          } else {
            newRoots.add(names[i]);
          }
        }
        return new SetRoots(newRoots, graph);
      } else if (command.equalsIgnoreCase(KillNonRootedNodes.LABEL)) {
        // older versions wrote the label a second time, which is skipped
        String[] words = tuples1.length == 1 ? new String[0] : tuples1[1].trim().split("\\s+");
        SemgrexGraphName graph = attributeArgs.containsKey("graph") ? parseGraphName(attributeArgs.get("graph")) : null;
        for (int i = 0; i < words.length; ++i) {
          if (words[i].isEmpty() || words[i].equalsIgnoreCase(KillNonRootedNodes.LABEL)) {
            continue;
          } else if (words[i].equals(GRAPH_ARG)) {
            if (i + 1 == words.length) {
              throw new SsurgeonParseException(GRAPH_ARG + " needs the name of a graph");
            }
            graph = parseGraphName(words[i + 1]);
            ++i;
          } else {
            throw new SsurgeonParseException(KillNonRootedNodes.LABEL + " does not take the argument " + words[i]);
          }
        }
        return new KillNonRootedNodes(graph);
      }

      // Parse the arguments based upon the type of command to execute.
      final SsurgeonArgs argsBox = parseArgsBox(tuples1.length == 1 ? "" : tuples1[1], attributeArgs);
      if (argsBox.graph != null && !GRAPH_EDITS.contains(command.toLowerCase(Locale.ROOT))) {
        throw new SsurgeonParseException(command + " does not take a " + GRAPH_ARG + " argument");
      }
      final SemgrexGraphName graph = argsBox.graph == null ? SemgrexGraphName.BASIC : argsBox.graph;

      if (command.equalsIgnoreCase(AddDep.LABEL)) {
        if (argsBox.govNodeName != null && argsBox.reln == null) {
          throw new SsurgeonParseException("Relation not specified for AddDep");
        }
        SsurgeonRelation reln = SsurgeonRelation.parse(argsBox.reln, language);
        return AddDep.withRelation(argsBox.govNodeName, reln, argsBox.annotations, argsBox.position, argsBox.weight, argsBox.name, argsBox.graph);
      } else if (command.equalsIgnoreCase(AddEdge.LABEL)) {
        if (argsBox.reln == null) {
          throw new SsurgeonParseException("Relation not specified for AddEdge");
        }
        SsurgeonRelation reln = SsurgeonRelation.parse(argsBox.reln, language);
        if (argsBox.edge != null) {
          knownEdges.add(argsBox.edge);
        }
        return AddEdge.withRelation(argsBox.govNodeName, argsBox.dep, reln, argsBox.weight, argsBox.edge, graph);
      } else if (command.equalsIgnoreCase(ReattachNamedEdge.LABEL)) {
        if (argsBox.edge == null) {
          throw new SsurgeonParseException("Edge not specified for ReattachNamedEdge");
        }
        if (!knownEdges.contains(argsBox.edge)) {
          throw new SsurgeonParseException("Edge requested for ReattachNamedEdge is not known to the SemgrexPattern");
        }
        return new ReattachNamedEdge(argsBox.edge, argsBox.govNodeName, argsBox.dep);
      } else if (command.equalsIgnoreCase(DeleteLeaf.LABEL)) {
        if (argsBox.nodes.size() != 1) {
          throw new SsurgeonParseException("Cannot make a DeleteLeaf out of " + argsBox.nodes.size() + " nodes");
        }
        return new DeleteLeaf(argsBox.nodes.get(0), argsBox.graph);
      } else if (command.equalsIgnoreCase(EditNode.LABEL)) {
        if (argsBox.nodes.size() != 1) {
          throw new SsurgeonParseException("Cannot make an EditNode out of " + argsBox.nodes.size() + " nodes.  Please use exactly one -node");
        }
        return new EditNode(argsBox.nodes.get(0), argsBox.annotations, argsBox.updateMorphoFeatures, argsBox.remove, argsBox.removeMorphoFeatures);
      } else if (command.equalsIgnoreCase(Remap.LABEL)) {
        if (argsBox.table == null) {
          throw new SsurgeonParseException("Remap needs a table, given with " + TABLE_ARG);
        }
        SsurgeonTable table = tables.get(argsBox.table);
        if (table == null) {
          throw new SsurgeonParseException("Remap names the table " + argsBox.table + ", but the tables are " + new TreeSet<>(tables.keySet()));
        }
        return new Remap(argsBox.variable, table, argsBox.to);
      } else if (command.equalsIgnoreCase(Lemmatize.LABEL)) {
        if (argsBox.nodes.size() != 1) {
          throw new SsurgeonParseException("Cannot make a Lemmatize out of " + argsBox.nodes.size() + " nodes.  Please use exactly one -node");
        }
        return new Lemmatize(argsBox.nodes.get(0), language);
      } else if (command.equalsIgnoreCase(MergeNodes.LABEL)) {
        if (argsBox.nodes.size() < 2) {
          throw new SsurgeonParseException("Cannot make a MergeNodes out of fewer than 2 nodes (got " + argsBox.nodes.size() + ")");
        }
        return new MergeNodes(argsBox.nodes, argsBox.annotations);
      } else if (command.equalsIgnoreCase(RelabelNamedEdge.LABEL)) {
        if (argsBox.edge == null) {
          throw new SsurgeonParseException("Edge not specified for RelabelNamedEdge");
        }
        if (!knownEdges.contains(argsBox.edge)) {
          throw new SsurgeonParseException("Edge requested for RelabelNamedEdge is not known to the SemgrexPattern");
        }
        if (argsBox.reln == null) {
          throw new SsurgeonParseException("Relation not specified for RelabelNamedEdge");
        }
        return RelabelNamedEdge.withRelation(argsBox.edge, SsurgeonRelation.parse(argsBox.reln, language));
      } else if (command.equalsIgnoreCase(RemoveEdge.LABEL)) {
        GrammaticalRelation reln = null;
        if (argsBox.reln != null) {
          reln = GrammaticalRelation.valueOf(argsBox.reln);
        }
        return new RemoveEdge(reln, argsBox.govNodeName, argsBox.dep, graph);
      } else if (command.equalsIgnoreCase(RemoveNamedEdge.LABEL)) {
        return new RemoveNamedEdge(argsBox.edge);
      } else if (command.equalsIgnoreCase(KillAllIncomingEdges.LABEL)) {
        if (argsBox.nodes.size() != 1) {
          throw new SsurgeonParseException("Cannot make a KillAllIncomingEdges out of " + argsBox.nodes.size() + " nodes");
        }
        return new KillAllIncomingEdges(argsBox.nodes.get(0), graph);
      } else if (command.equalsIgnoreCase(CombineMWT.LABEL)) {
        return new CombineMWT(argsBox.nodes, argsBox.annotations.get("word"));
      } else if (command.equalsIgnoreCase(SetPhraseHead.LABEL)) {
        if (argsBox.reln == null) {
          throw new SsurgeonParseException(command + " requires a -reln argument");
        }
        GrammaticalRelation reln = GrammaticalRelation.valueOf(language, argsBox.reln);
        return new SetPhraseHead(argsBox.nodes, argsBox.headIndex, reln, argsBox.weight, graph);
      } else if (command.equalsIgnoreCase(SplitWord.LABEL)) {
        if (argsBox.reln == null) {
          throw new SsurgeonParseException(command + " requires a -reln argument");
        }
        if (argsBox.regex.size() > 0 && argsBox.exact.size() > 0) {
          throw new SsurgeonParseException("Found both regex and exact in the splits for splitWord");
        }
        if (argsBox.nodes.size() == 0) {
          throw new SsurgeonParseException("splitWord not given any -node parameter for the node to split");
        }
        boolean exact = argsBox.regex.isEmpty();
        List<String> pieces = exact ? argsBox.exact : argsBox.regex;
        // a relation name has no =, so one with = is a relation for each piece
        GrammaticalRelation reln = null;
        Map<Integer, GrammaticalRelation> pieceRelns = null;
        if (argsBox.reln.contains("=")) {
          pieceRelns = new HashMap<>();
          for (Map.Entry<Integer, String> entry : SplitWord.splitNames(argsBox.reln, "reln", pieces.size()).entrySet()) {
            pieceRelns.put(entry.getKey(), GrammaticalRelation.valueOf(language, entry.getValue()));
          }
        } else {
          reln = GrammaticalRelation.valueOf(language, argsBox.reln);
        }
        final SplitWord split = new SplitWord(argsBox.nodes.get(0), pieces, argsBox.headIndex, reln, pieceRelns, argsBox.name, argsBox.edge, exact, argsBox.siblings);
        for (String edgeName : split.edgeNames.values()) {
          knownEdges.add(edgeName);
        }
        return split;
      } else if (command.equalsIgnoreCase(ReindexGraph.LABEL)) {
        return new ReindexGraph();
      }
      throw new SsurgeonParseException("Error in SsurgeonEdit.parseEditLine: command '"+command+"' is not supported");
    } catch (SsurgeonParseException e) {
      throw new SsurgeonParseException("Unable to process Ssurgeon edit line: " + editLine, e);
    }
  }

  //public static SsurgeonPattern fromXML(String xmlString) throws Exception {
  //SAXBuilder builder = new SAXBuilder();
  //Document jdomDoc = builder.build(xmlString);
  //jdomDoc.getRootElement().getChildren(SsurgeonPattern.SSURGEON_ELEM_TAG);
  //}

  /**
   * Given a target filepath and a list of Ssurgeon patterns, writes them out as XML forms,
   * along with the tables their {@code remap} edits use.
   * Errors, such as two different tables with the same name, are logged rather than thrown.
   *
   * @param tgtFile The file to write
   * @param patterns The patterns to write
   */
  public static void writeToFile(File tgtFile, List<SsurgeonPattern> patterns) {
    try {
      Document domDoc = createPatternXMLDoc(patterns);
      if (domDoc != null) {
        Transformer tformer = TransformerFactory.newInstance().newTransformer();
        tformer.setOutputProperty(OutputKeys.INDENT, "yes");
        tformer.transform(new DOMSource(domDoc), new StreamResult(tgtFile));
      } else {
        log.warning("Was not able to create XML document for pattern list, file not written.");
      }
    } catch (Exception e) {
      log.error(Ssurgeon.class.getName(), "writeToFile");
      log.error(e);
    }
  }

  /**
   * Converts one pattern to an XML string.
   *
   * @param pattern The pattern to convert
   * @return The XML, or null if converting failed with an exception, or
   *   the empty string if the XML document could not be built
   */
  public static String writeToString(SsurgeonPattern pattern) {
    try {
      List<SsurgeonPattern> patterns = new LinkedList<>();
      patterns.add(pattern);
      Document domDoc = createPatternXMLDoc(patterns);
      if (domDoc != null) {
        Transformer tformer = TransformerFactory.newInstance().newTransformer();
        tformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter sw = new StringWriter();
        tformer.transform(new DOMSource(domDoc), new StreamResult(sw));
        return sw.toString();
      } else {
        log.warning("Was not able to create XML document for pattern list.");
      }
    } catch (Exception e) {
      log.info("Error in writeToString, could not process pattern="+pattern);
      log.info(e);
      return null;
    }
    return "";
  }


  /**
   * The tables the {@code remap} edits of the patterns use, by name, in the order they are first used.
   *
   * @param patterns the patterns
   * @return the tables
   * @throws IllegalArgumentException if two different tables have the same name, as
   *   one pattern list cannot hold them both
   */
  static Map<String, SsurgeonTable> tablesUsed(List<SsurgeonPattern> patterns) {
    Map<String, SsurgeonTable> tables = new LinkedHashMap<>();
    for (SsurgeonPattern pattern : patterns) {
      for (SsurgeonEdit edit : pattern.getEditScript()) {
        if (edit instanceof Remap) {
          SsurgeonTable table = ((Remap) edit).table;
          SsurgeonTable previous = tables.putIfAbsent(table.getName(), table);
          if (previous != null && !previous.equals(table)) {
            throw new IllegalArgumentException("The patterns use two different tables named " + table.getName());
          }
        }
      }
    }
    return tables;
  }

  private static Document createPatternXMLDoc(List<SsurgeonPattern> patterns) {
    try {
      DocumentBuilderFactory dbf = XMLUtils.safeDocumentBuilderFactory();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document domDoc = db.newDocument();
      Element rootElt = domDoc.createElement(SsurgeonPattern.ELT_LIST_TAG);
      domDoc.appendChild(rootElt);
      // the tables the patterns use go in the list, for the reader to give back to them
      for (SsurgeonTable table : tablesUsed(patterns).values()) {
        Element tableElt = domDoc.createElement(SsurgeonPattern.TABLE_TAG);
        tableElt.setAttribute(SsurgeonPattern.TABLE_NAME_ATTR, table.getName());
        for (Map.Entry<String, String> entry : table.entries().entrySet()) {
          Element entryElt = domDoc.createElement(SsurgeonPattern.TABLE_ENTRY_TAG);
          entryElt.setAttribute(SsurgeonPattern.TABLE_KEY_ATTR, entry.getKey());
          entryElt.setAttribute(SsurgeonPattern.TABLE_VALUE_ATTR, entry.getValue());
          tableElt.appendChild(entryElt);
        }
        rootElt.appendChild(tableElt);
      }
      int ordinal = 1;
      for (SsurgeonPattern pattern : patterns) {
        Element patElt = domDoc.createElement(SsurgeonPattern.SSURGEON_ELEM_TAG);
        patElt.setAttribute(SsurgeonPattern.ORDINAL_ATTR, String.valueOf(ordinal));
        Element semgrexElt = domDoc.createElement(SsurgeonPattern.SEMGREX_ELEM_TAG);
        semgrexElt.appendChild(domDoc.createTextNode(pattern.getSemgrexPattern().pattern()));
        patElt.appendChild(semgrexElt);
        Element uidElem = domDoc.createElement(SsurgeonPattern.UID_ELEM_TAG);
        uidElem.appendChild(domDoc.createTextNode(pattern.getUID()));
        patElt.appendChild(uidElem);
        Element notesElem = domDoc.createElement(SsurgeonPattern.NOTES_ELEM_TAG);
        notesElem.appendChild(domDoc.createTextNode(pattern.getNotes()));
        patElt.appendChild(notesElem);
        // the relations in the edits are read in this language
        Element languageElem = domDoc.createElement(SsurgeonPattern.LANGUAGE_TAG);
        languageElem.appendChild(domDoc.createTextNode(pattern.getLanguage().toString()));
        patElt.appendChild(languageElem);

        SemanticGraph semgrexGraph = pattern.getSemgrexGraph();
        if (semgrexGraph != null) {
          Element patNode = domDoc.createElement(SsurgeonPattern.SEMGREX_GRAPH_ELEM_TAG);
          patNode.appendChild(domDoc.createTextNode(semgrexGraph.toCompactString()));
        }
        // one edit-list element per edit, as the reader expects
        for (SsurgeonEdit edit : pattern.getEditScript()) {
          Element editElem = domDoc.createElement(SsurgeonPattern.EDIT_LIST_ELEM_TAG);
          editElem.appendChild(domDoc.createTextNode(edit.toEditString()));
          patElt.appendChild(editElem);
        }
        rootElt.appendChild(patElt);
        ordinal++;
      }
      return domDoc;
    } catch (Exception e) {
      log.error(Ssurgeon.class.getName(), "createPatternXML");
      log.error(e);
      return null;
    }
  }

  /**
   * Reads the SsurgeonPatterns in an XML string.  Any word list resources
   * are added to this Ssurgeon.
   *
   * @param text The XML text
   * @return The patterns, in the order they appear
   * @throws SsurgeonParseException if the XML cannot be parsed
   */
  public List<SsurgeonPattern> readFromString(String text) {
    try {
      Document doc = XMLUtils.readDocumentFromString(text);
      return readFromDocument(doc);
    } catch (ParserConfigurationException | SAXException e) {
      throw new SsurgeonParseException("XML failure while reading string", e);
    }
  }

  /**
   * Given a path to a file containing a list of SsurgeonPatterns, returns
   * the patterns in that file.
   *
   * TODO: deal with resources
   *
   * @param file The XML file to read
   * @return The patterns, in the order they appear
   * @throws SsurgeonParseException if the XML cannot be parsed
   */
  public List<SsurgeonPattern> readFromFile(File file) {
    try {
      Document doc = XMLUtils.readDocumentFromFile(file.getPath());

      if (VERBOSE)
        System.out.println("Reading ssurgeon file="+file.getAbsolutePath());

      return readFromDocument(doc);
    } catch (ParserConfigurationException | SAXException e) {
      throw new SsurgeonParseException("XML failure while reading " + file, e);
    }
  }

  /**
   * Reads Ssurgeon patterns written as plain text blocks, the same format
   * the edit files of Stanza's {@code stanza/server/ssurgeon.py} use.
   *<br>
   * Blocks are separated by one or more blank lines.  In each block,
   * lines starting with {@code #} are comments, which are joined to make
   * the pattern's notes.  The first other line is the semgrex pattern, and
   * each line after it is one edit, applied in order.  For example:
   *<pre>{@code
   * # que as a subordinating conjunction
   * {word:/(?i:que)/;cpos:CCONJ}=que <mark {}
   * EditNode -node que -cpos SCONJ
   *
   * {word:/(?i:que)/;cpos:CCONJ}=que <obj {}
   * EditNode -node que -cpos PRON -morphofeatures PronType=Rel
   * }</pre>
   * A block of nothing but comments is skipped.  Each pattern's UID is the
   * position of its block, counting from 1 and including skipped blocks.
   *<br>
   * A block whose first line is {@code table name} is a table for
   * {@code remap} rather than a pattern, with one {@code key<TAB>value}
   * line per entry, as {@link SsurgeonTable} describes.  A table can be
   * used by any pattern in the same text, before or after it.
   *<pre>{@code
   * table lemma_mapping
   * estudir	estudiar
   * var	variar
   *
   * {cpos:VERB;lemma:__#0%lemma}=verb
   * remap -var lemma -table lemma_mapping
   * editNode -node verb -lemma ${lemma}
   * }</pre>
   *
   * @param text The blocks to read
   * @param language The name of the language used when parsing relation names in the edits,
   *   such as {@code UniversalEnglish}
   * @return The patterns, in the order their blocks appear
   * @throws SsurgeonParseException if the language is unknown, a
   *   semgrex pattern, an edit, or a table cannot be parsed, or two
   *   tables have the same name
   */
  public static List<SsurgeonPattern> readFromEditText(String text, String language) {
    if (Language.valueOfSafe(language) == null) {
      throw new SsurgeonParseException("Unknown language " + language);
    }
    List<SsurgeonPattern> patterns = new ArrayList<>();
    List<List<String>> blocks = new ArrayList<>();
    List<String> block = new ArrayList<>();
    for (String line : text.split("\\R")) {
      if (line.trim().isEmpty()) {
        if (!block.isEmpty()) {
          blocks.add(block);
          block = new ArrayList<>();
        }
      } else {
        block.add(line.trim());
      }
    }
    if (!block.isEmpty()) {
      blocks.add(block);
    }

    // the tables are read first, so a pattern can use one defined after it
    Map<String, SsurgeonTable> tables = new LinkedHashMap<>();
    for (List<String> tableBlock : blocks) {
      List<String> lines = new ArrayList<>();
      for (String line : tableBlock) {
        if (!line.startsWith("#")) {
          lines.add(line);
        }
      }
      String name = lines.isEmpty() ? null : tableName(lines.get(0));
      if (name != null) {
        if (tables.containsKey(name)) {
          throw new SsurgeonParseException("There are two tables named " + name);
        }
        tables.put(name, SsurgeonTable.fromLines(name, lines.subList(1, lines.size())));
      }
    }

    for (int blockIdx = 0; blockIdx < blocks.size(); ++blockIdx) {
      List<String> comments = new ArrayList<>();
      List<String> lines = new ArrayList<>();
      for (String line : blocks.get(blockIdx)) {
        if (line.startsWith("#")) {
          comments.add(line.substring(1).trim());
        } else {
          lines.add(line);
        }
      }
      if (lines.isEmpty() || tableName(lines.get(0)) != null) {
        continue;
      }

      String uid = Integer.toString(blockIdx + 1);
      SemgrexPattern semgrex;
      try {
        semgrex = SemgrexPattern.compile(lines.get(0));
      } catch (RuntimeException e) {
        throw new SsurgeonParseException("Unable to compile the semgrex pattern of block " + uid + ": " + lines.get(0), e);
      }
      SsurgeonPattern pattern = new SsurgeonPattern(uid, semgrex);
      pattern.setNotes(String.join(" ", comments));
      pattern.setLanguage(language);

      Set<String> knownEdges = new HashSet<>(semgrex.getKnownEdges());
      for (String edit : lines.subList(1, lines.size())) {
        pattern.addEdit(parseEditLine(edit, Collections.emptyMap(), pattern.getLanguage(), knownEdges, tables));
      }
      patterns.add(pattern);
    }
    return patterns;
  }

  /**
   * The name of the table a block starts, if its first line is {@code table name}.
   * A semgrex pattern cannot start with a bare word, so such a line is never one.
   *
   * @param line the first line of a block, other than its comments
   * @return the table's name, or null if the line does not start a table
   * @throws SsurgeonParseException if the line starts a table but does not give one name
   */
  private static String tableName(String line) {
    String[] pieces = line.trim().split("\\s+");
    if (!pieces[0].equalsIgnoreCase(SsurgeonTable.LABEL)) {
      return null;
    }
    if (pieces.length != 2) {
      throw new SsurgeonParseException("A table block starts with " + SsurgeonTable.LABEL + " and the table's name, not |" + line + "|");
    }
    return pieces[1];
  }

  /**
   * Reads Ssurgeon patterns from a file of plain text blocks, as described in {@link #readFromEditText}.
   *
   * @param file The file to read, in UTF-8
   * @param language The name of the language used when parsing relation names in the edits
   * @return The patterns, in the order their blocks appear
   * @throws IOException if the file cannot be read
   * @throws SsurgeonParseException if a pattern cannot be parsed
   */
  public static List<SsurgeonPattern> readFromEditFile(File file, String language) throws IOException {
    String text = IOUtils.slurpFile(file.getPath(), "utf-8");
    return readFromEditText(text, language);
  }

  /**
   * Reads the SsurgeonPatterns in an XML document.  Any word list resources
   * are added to this Ssurgeon.  The tables are read first, so a pattern
   * can use any table in the document, before or after it.
   *
   * @param doc The XML document
   * @return The patterns, in the order they appear
   * @throws SsurgeonParseException if a pattern or a table cannot be read,
   *   or two tables have the same name
   */
  public List<SsurgeonPattern> readFromDocument(Document doc) {
    List<SsurgeonPattern> retList = new ArrayList<>();
    Map<String, SsurgeonTable> tables = readTables(doc);

    NodeList patternNodes = doc.getElementsByTagName(SsurgeonPattern.SSURGEON_ELEM_TAG);
    for (int i=0; i<patternNodes.getLength(); i++) {
      Node node = patternNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element elt = (Element) node;
        SsurgeonPattern pattern = ssurgeonPatternFromXML(elt, tables);
        retList.add(pattern);
      }
    }

    NodeList resourceNodes = doc.getElementsByTagName(SsurgeonPattern.RESOURCE_TAG);
    for (int i=0; i < resourceNodes.getLength(); i++) {
      Node node = resourceNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element resourceElt = (Element) node;
        SsurgeonWordlist wlRsrc = new SsurgeonWordlist(resourceElt);
        addResource(wlRsrc);
      }
    }

    return retList;
  }

  /**
   * Reads the tables of an XML document.
   *
   * @param doc The XML document
   * @return The tables, by name
   * @throws SsurgeonParseException if a table has no name, an entry has no
   *   key or value, a key has two values, the entries form a cycle, or two
   *   tables have the same name
   */
  private static Map<String, SsurgeonTable> readTables(Document doc) {
    Map<String, SsurgeonTable> tables = new LinkedHashMap<>();
    NodeList tableNodes = doc.getElementsByTagName(SsurgeonPattern.TABLE_TAG);
    for (int i = 0; i < tableNodes.getLength(); ++i) {
      Element tableElt = (Element) tableNodes.item(i);
      String name = tableElt.getAttribute(SsurgeonPattern.TABLE_NAME_ATTR);
      if (name.isEmpty()) {
        throw new SsurgeonParseException("A table needs a " + SsurgeonPattern.TABLE_NAME_ATTR);
      }
      Map<String, String> entries = new LinkedHashMap<>();
      NodeList entryNodes = tableElt.getElementsByTagName(SsurgeonPattern.TABLE_ENTRY_TAG);
      for (int j = 0; j < entryNodes.getLength(); ++j) {
        Element entryElt = (Element) entryNodes.item(j);
        if (!entryElt.hasAttribute(SsurgeonPattern.TABLE_KEY_ATTR) || entryElt.getAttribute(SsurgeonPattern.TABLE_KEY_ATTR).isEmpty() ||
            !entryElt.hasAttribute(SsurgeonPattern.TABLE_VALUE_ATTR)) {
          throw new SsurgeonParseException("Each entry of table " + name + " needs a " + SsurgeonPattern.TABLE_KEY_ATTR +
                                           " and a " + SsurgeonPattern.TABLE_VALUE_ATTR);
        }
        SsurgeonTable.addEntry(name, entries,
                               entryElt.getAttribute(SsurgeonPattern.TABLE_KEY_ATTR),
                               entryElt.getAttribute(SsurgeonPattern.TABLE_VALUE_ATTR));
      }
      SsurgeonTable table = new SsurgeonTable(name, entries);
      if (tables.put(name, table) != null) {
        throw new SsurgeonParseException("There are two tables named " + name);
      }
    }
    return tables;
  }

  /**
   * Reads all Ssurgeon patterns from the .xml files in a directory.
   * A file which cannot be read is logged and skipped.
   *
   * @param dir The directory to read
   * @return The patterns from all of the files
   * @throws Exception if {@code dir} is not a directory
   */
  public List<SsurgeonPattern> readFromDirectory(File dir) throws Exception {
    if (!dir.isDirectory()) throw new Exception("Given path not a directory, path="+dir.getAbsolutePath());
    if (VERBOSE)
      System.out.println("Reading Ssurgeon patterns from directory = "+dir.getAbsolutePath());
    File[] files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".xml"));
    List<SsurgeonPattern> patterns = new ArrayList<>();
    for (File file : files) {
      try {
        patterns.addAll(readFromFile(file));
      } catch (Exception e) {
        log.error(e);
      }
    }
    return patterns;
  }

  /**
   * Given the root Element for a SsurgeonPattern (SSURGEON_ELEM_TAG), converts
   * it into its corresponding SsurgeonPattern object.
   *
   * @param elt The element to convert
   * @return The new pattern
   * @throws SsurgeonParseException if an edit or predicate cannot be parsed
   */
  public static SsurgeonPattern ssurgeonPatternFromXML(Element elt) {
    return ssurgeonPatternFromXML(elt, Collections.emptyMap());
  }

  /**
   * Given the root Element for a SsurgeonPattern (SSURGEON_ELEM_TAG), converts
   * it into its corresponding SsurgeonPattern object, with the tables its
   * {@code remap} edits can use.
   *
   * @param elt The element to convert
   * @param tables The tables a {@code remap} can name, by name
   * @return The new pattern
   * @throws SsurgeonParseException if an edit or predicate cannot be parsed, or an edit
   *   names a table which is not given
   */
  @SuppressWarnings("unchecked")
  public static SsurgeonPattern ssurgeonPatternFromXML(Element elt, Map<String, SsurgeonTable> tables) {
    String uid = getTagText(elt, SsurgeonPattern.UID_ELEM_TAG);
    String notes = getTagText(elt, SsurgeonPattern.NOTES_ELEM_TAG);
    String semgrexString = getTagText(elt, SsurgeonPattern.SEMGREX_ELEM_TAG);
    SemgrexPattern semgrexPattern = SemgrexPattern.compile(semgrexString);
    SsurgeonPattern retPattern = new SsurgeonPattern(uid, semgrexPattern);
    retPattern.setNotes(notes);

    String language = getTagText(elt, SsurgeonPattern.LANGUAGE_TAG);
    if (!language.equals("")) {
      retPattern.setLanguage(language);
    }

    Set<String> knownEdges = new HashSet<>(semgrexPattern.getKnownEdges());

    NodeList editNodes = elt.getElementsByTagName(SsurgeonPattern.EDIT_LIST_ELEM_TAG);
    for (int i=0; i<editNodes.getLength(); i++) {
      Node node = editNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        // read all arguments such as `after=" "` off the node
        // this way, arguments which can't be parsed via whitespace
        // (especially arguments which actually contain whitespace)
        // can be passed to an EditLine
        // LinkedHashMap so we can preserve insertion order
        Map<String, String> attributeArgs = new LinkedHashMap<>();
        for (int j = 0; j < node.getAttributes().getLength(); ++j) {
          Node attrNode = node.getAttributes().item(j);
          if (attrNode.getNodeType() == Node.ATTRIBUTE_NODE) {
            Attr attr = (Attr) attrNode;
            attributeArgs.put(attr.getName(), attr.getValue());
          }
        }

        Element editElt = (Element) node;
        String editVal = getEltText(editElt);
        retPattern.addEdit(Ssurgeon.parseEditLine(editVal, attributeArgs, retPattern.getLanguage(), knownEdges, tables));
      }
    }


    // If predicate available, parse
    Element predElt = getFirstTag(elt, SsurgeonPattern.PREDICATE_TAG);
    if (predElt != null) {
      SsurgPred pred = assemblePredFromXML(getFirstChildElement(predElt));
      retPattern.setPredicate(pred);
    }
    return retPattern;
  }

  /**
   * Constructs a {@code SsurgPred} structure from file, given the root element.
   *
   * @param elt The element describing the predicate
   * @return The predicate
   * @throws SsurgeonParseException if the element is not a known predicate,
   *   or is an and or an or with no predicates in it
   */
  public static SsurgPred assemblePredFromXML(Element elt) {
    String eltName = elt.getTagName();
    switch (eltName) {
      case SsurgeonPattern.PREDICATE_AND_TAG:
        SsurgAndPred andPred = new SsurgAndPred();
        for (Element childElt : getChildElements(elt)) {
          andPred.add(assemblePredFromXML(childElt));
        }
        if (!andPred.isEmpty()) {
          return andPred;
        }
        break;
      case SsurgeonPattern.PREDICATE_OR_TAG:
        SsurgOrPred orPred = new SsurgOrPred();
        for (Element childElt : getChildElements(elt)) {
          orPred.add(assemblePredFromXML(childElt));
        }
        if (!orPred.isEmpty()) {
          return orPred;
        }
        break;
      case SsurgeonPattern.PRED_WORDLIST_TEST_TAG:
        String id = elt.getAttribute(SsurgeonPattern.PRED_ID_ATTR);
        String resourceID = elt.getAttribute("resourceID");
        String typeStr = elt.getAttribute("type");
        String matchName = getEltText(elt).trim(); // node name to match on

        if (matchName == null) {
          throw new SsurgeonParseException("Could not find match name for " + elt);
        }
        if (id == null) {
          throw new SsurgeonParseException("No ID attribute for element = " + elt);
        }
        return new WordlistTest(id, resourceID, typeStr, matchName);
    }

    // Not a valid node, error out!
    throw new SsurgeonParseException("Invalid node encountered during Ssurgeon predicate processing, node name="+eltName);
  }



  /**
   * Reads in the test file and prints readable to string (for debugging).
   * Input file consists of semantic graphs, in compact form.
   *<br>
   * Reads the patterns in a directory, then repeatedly reads a graph from stdin
   * and prints the graphs the patterns generate from it.  Exits on an empty line.
   *
   * @param tgtDirPath The directory of patterns
   * @throws Exception if {@code tgtDirPath} is not a directory
   */
  public void testRead(File tgtDirPath) throws Exception {
    List<SsurgeonPattern> patterns = readFromDirectory(tgtDirPath);

    System.out.println("Patterns, num = "+patterns.size());
    int num = 1;
    for (SsurgeonPattern pattern : patterns) {
      System.out.println("\n# "+(num++));
      System.out.println(pattern);
    }

    System.out.println("\n\nRESOURCES ");
    for (SsurgeonWordlist rsrc : inst().getResources()) {
      System.out.println(rsrc+"* * * * *");
    }

    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    boolean runFlag = true;
    Ssurgeon.inst().initLog(new File("./ssurgeon_run.log"));
    while (runFlag) {
      try {
        System.out.println("Enter a sentence:");
        String line = in.readLine();
        if (line.isEmpty()) {
          System.exit(0);
        }
        System.out.println("Parsing...");
        SemanticGraph sg = SemanticGraph.valueOf(line);
        System.out.println("Graph = "+sg);
        Collection<SemanticGraph> generated = Ssurgeon.inst().exhaustFromPatterns(patterns, sg);
        System.out.println("# generated = "+generated.size());
        int index = 1;
        for (SemanticGraph gsg : generated) {
          System.out.println("\n# "+index);
          System.out.println(gsg);
          index++;
        }
      } catch (Exception e) {
        log.error(e);
      }
    }
  }


  /*
   * XML convenience routines
   */
  // todo [cdm 2016]: Aren't some of these methods available as generic XML methods elsewhere??

  /**
   * For the given element, returns the text for the first child Element with
   * the given tag.
   *
   * @param element The element to search
   * @param tag The tag to look for
   * @return The text, or the empty string if there is no such tag
   */
  public static String getTagText(Element element, String tag) {
    try {
      // From root element, identify first with tag, then find the
      // first child under that, which we treat as a TEXT node.
      Element firstElt = getFirstTag(element, tag);
      if (firstElt == null) return "";
      return getEltText(firstElt);
    } catch (Exception e) {
      log.warning("Exception thrown attempting to get tag text for tag="+tag+", from element="+element);
    }
    return "";
  }

  /**
   * For a given Element, treats the first child as a text element
   * and returns its value.
   *
   * @param element The element to read
   * @return The value of the first child, or the empty string if there are no children
   */
  public static String getEltText(Element element) {
    try {
      NodeList childNodeList = element.getChildNodes();
      if (childNodeList.getLength() == 0) return "";
      return childNodeList.item(0).getNodeValue();
    } catch (Exception e) {
      log.warning("Exception e=" + e.getMessage() + " thrown calling getEltText on element=" + element);
    }
    return "";
  }

  /**
   * For the given element, finds the first child Element with the given tag.
   */
  private static Element getFirstTag(Element element, String tag) {
    try {
      NodeList nodeList = element.getElementsByTagName(tag);
      if (nodeList.getLength() == 0) return null;
      for (int i=0; i < nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE)
          return (Element) node;
      }
    } catch (Exception e) {
      log.warning("Error getting first tag "+tag+" under element="+element);
    }
    return null;
  }

  /**
   * Returns the first child whose node type is Element under the given Element.
   */
  private static Element getFirstChildElement(Element element) {
    try {
      NodeList nodeList = element.getChildNodes();
      for (int i=0; i<nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE)
          return (Element) node;
      }
    } catch (Exception e) {
      log.warning("Error getting first child Element for element=" + element+", exception=" + e);
    }
    return null;
  }


  /**
   * Returns all of the Element typed children from the given element.  Note: disregards
   * other node types.
   */
  private static List<Element> getChildElements(Element element) {
    LinkedList<Element> childElements = new LinkedList<>();
    try {
      NodeList nodeList = element.getChildNodes();
      for (int i=0; i<nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          childElements.add((Element) node);
        }
      }
    } catch (Exception e) {
      log.warning("Exception thrown getting all children for element=" + element+ ", e=" + e);
    }
    return childElements;
  }

  /*
   * Main class evocation stuff
   */


  /** What {@link #main} should do */
  public enum RUNTYPE {
    /** Interactively test contents of pattern directory against entered sentences */
    interactive, // interactively test contents of pattern directory against entered sentences
    /** Test against a given infofile (RTE), generating rewrites for hypotheses */
    testinfo // test against a given infofile (RTE), generating rewrites for hypotheses
  }


  /** Command line arguments for {@link #main} */
  public static class ArgsBox {
    /** Creates args with the default values. */
    public ArgsBox() { }

    /** What to run */
    public RUNTYPE type = RUNTYPE.interactive;

    /** The {@code -patterns} directory, as given */
    public String patternDirStr = null;
    /** The pattern directory, set by {@link #init()} */
    public File patternDir = null;

    /** The {@code -info} file, as given */
    public String info = null;
    /** The info file, set by {@link #init()} for testinfo */
    public File infoPath = null;

    /**
     * Converts the file names to Files.
     * patternDirStr must be set, and info must be set for testinfo.
     */
    public void init() {
      patternDir = new File(patternDirStr);
      if (type == RUNTYPE.testinfo)
        infoPath = new File(info);
    }

    @Override
    public String toString() {
      StringWriter buf = new StringWriter();
      buf.write("type ="+type+"\n");
      buf.write("pattern dir = "+patternDir.getAbsolutePath());
      if (type == RUNTYPE.testinfo) {
        buf.write("info file = "+info);
        if (info != null)
          buf.write(", path = "+infoPath.getAbsolutePath());
      }
      return buf.toString();
    }
  }

  /** The args read by {@link #main} */
  protected static ArgsBox argsBox = new ArgsBox();

  /**
   * Performs a simple test and print of a given file.
   * Usage Ssurgeon [-info infoFile] -patterns patternDir [-type interactive|testinfo]
   *<br>
   * Only the interactive type is run; see {@link #testRead}.
   *
   * @param args Command line arguments, as above
   */
  public static void main(String[] args) {
    for (int argIndex = 0; argIndex < args.length; ++argIndex) {
      if (args[argIndex].equalsIgnoreCase("-info")) {
        argsBox.info = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-patterns")) {
        argsBox.patternDirStr = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-type")) {
        argsBox.type = RUNTYPE.valueOf(args[argIndex + 1]);
        argIndex += 2;
      }
    }
    if (argsBox.patternDirStr == null) {
      throw new IllegalArgumentException("Need to give a pattern location with -patterns");
    }
    argsBox.init();

    System.out.println(argsBox);
    try {
      if (argsBox.type == RUNTYPE.interactive) {
        Ssurgeon.inst().testRead(argsBox.patternDir);
      }
    } catch (Exception e) {
      log.error(e);
    }
  }

}
