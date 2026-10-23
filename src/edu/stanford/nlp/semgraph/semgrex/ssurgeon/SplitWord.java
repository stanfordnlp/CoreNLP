package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Split a word into pieces based on the regex expressions provided by the -regex arguments
 * <br>
 * As an example of where this is useful, a tokenization dataset had "
 * stuck to each of the words.  We can separate that out by using two
 * regex, one which matches the " in a group, one which matches the
 * rest of the word without the "
 * <br>
 * The pieces are counted from 0, in the order they appear in the
 * sentence, for -headIndex, -name, -edge, and -reln alike.
 * <br>
 * Aside from the text and the dependency, the new node is rather bare bones.
 * Adding the -name argument allows for specifying a comma-separate list
 * of names which can be used to insert the new nodes into the SemgrexMatcher
 * as named nodes.  This will allow for further edits in the same edit step.
 * <br>
 * For example, this will split "foobar" and put the pos ADJ on the first word
 * <pre>
 * semgrex:
 *   {word:/foobar/}=split
 * ssurgeon:
 *   splitWord -node split -regex ^(foo)bar$ -regex ^foo(bar)$ -reln dep -headIndex 1 -name 0=asdf
 *   editNode -node asdf -pos ADJ
 * </pre>
 * <br>
 * The piece named by -headIndex keeps the matched node, along with its
 * index, its incoming edges and its children.  The other pieces are new
 * nodes, attached by -reln.  By default they are attached to that piece,
 * which suits a word whose pieces really are headed by one of them.
 * <br>
 * With {@code -siblings true} they are attached to the governors of the
 * matched node instead, so that all of the pieces sit side by side under
 * whatever the original word hung from.  A Spanish contraction such as
 * "yla" splits into "y" and "la", and neither of those is the head of the
 * other; they both belong to the phrase the original word belonged to.
 * <br>
 * -reln is either one relation for every new piece, or a relation for
 * each piece, written as 1=det,2=obj.  In that form, each new piece needs
 * a relation.  The head piece may have one as well, which replaces the
 * relation of each of its incoming edges; otherwise those are left as
 * they are.  So "yla" can be split with each piece getting its own
 * relation:
 * <pre>
 * semgrex:
 *   {word:/yla/}=split
 * ssurgeon:
 *   splitWord -node split -exact y -exact la -reln 0=cc,1=det -headIndex 0 -siblings true
 * </pre>
 * -edge names the edges of the new pieces, so that later edits can
 * change them.  The head piece keeps the edges it had, which the
 * semgrex pattern can name.
 * <br>
 * The new pieces go into every graph of the sentence, so the graphs keep
 * sharing their words.  In each graph, a new piece is attached to the
 * head piece, or with -siblings, to each governor the matched node has
 * in that graph.  A name given with -edge refers to the edge in the basic
 * graph.  An empty word after the matched node goes after the last piece.
 * <br>
 * A matched node with no governor at all, such as the root, cannot be
 * split with -siblings, and the edit does nothing.
 *
 * @author John Bauer
 */
public class SplitWord extends SsurgeonEdit {
  /** The name of this edit in an Ssurgeon script */
  public static final String LABEL = "splitWord";

  final String node;
  final List<Pattern> nodeRegex;
  final List<String> exactPieces;
  final int headIndex;
  /** The relation for every new piece, or null if each piece has its own */
  final GrammaticalRelation relation;
  /** The relation for each piece, by its index, or empty if every new piece has the same one */
  final Map<Integer, GrammaticalRelation> pieceRelations;
  final Map<Integer, String> nodeNames;
  final Map<Integer, String> edgeNames;
  final boolean siblings;

  static Map<Integer, String> splitNames(String names, String type, int numPieces) {
    if (names == null) {
      return Collections.emptyMap();
    }

    String[] namePieces = names.split(",");
    HashMap<Integer, String> references = new HashMap<>();
    for (String name : namePieces) {
      String[] pieces = name.split("=", 2);
      if (pieces.length < 2) {
        throw new SsurgeonParseException("SplitWord got a -" + type + " parameter which did not have a number for one of the names.  Should look like 0=foo,1=bar");
      }
      int idx = Integer.valueOf(pieces[0]);
      if (idx >= numPieces) {
        throw new SsurgeonParseException("SplitWord got an index in -" + type + " which was larger than the largest possible split piece, " + idx + " (this is 0-indexed)");
      }
      references.put(idx, pieces[1]);
    }
    return references;
  }

  /**
   * Creates a split.
   *
   * @param node the name of the matched node to split
   * @param nodePieces the regexes (or exact strings, if {@code exactSplit}) giving each piece
   * @param headIndex the 0-indexed piece which keeps the matched node
   * @param relation the relation attaching each new piece, or null if pieceRelations gives one for each
   * @param pieceRelations the relation for each piece, by its 0-based index, or null if relation is used.
   *   Each new piece needs one; one for the head piece replaces the relation of its incoming edges
   * @param nodeNames names for the pieces, such as {@code 0=foo,1=bar}, or null
   * @param edgeNames names for the new edges, in the same format, or null
   * @param exactSplit whether {@code nodePieces} are exact strings rather than regexes
   * @param siblings if true, attach the new pieces to the governors of the
   *   matched node rather than to the head piece
   * @throws SsurgeonParseException if a required argument is missing, there are
   *   fewer than two pieces, a name or relation refers to a piece which does
   *   not exist, a new piece has no relation, or an edge name is given for
   *   the head piece
   */
  public SplitWord(String node, List<String> nodePieces, Integer headIndex, GrammaticalRelation relation, Map<Integer, GrammaticalRelation> pieceRelations,
                   String nodeNames, String edgeNames, boolean exactSplit, boolean siblings) {
    this.siblings = siblings;
    if (node == null) {
      throw new SsurgeonParseException("SplitWord expected -node with the name of the matched node to split");
    }
    this.node = node;

    if (nodePieces == null || nodePieces.size() == 0) {
      throw new SsurgeonParseException("SplitWord expected -exact or -regex with regex to determine which pieces to split the word into");
    }
    if (nodePieces.size() == 1) {
      throw new SsurgeonParseException("SplitWord expected at least two -exact or -regex");
    }
    if (exactSplit) {
      this.exactPieces = new ArrayList<>(nodePieces);
      this.nodeRegex = null;
    } else {
      this.nodeRegex = new ArrayList<>();
      for (int i = 0; i < nodePieces.size(); ++i) {
        this.nodeRegex.add(Pattern.compile(nodePieces.get(i)));
      }
      this.exactPieces = null;
    }

    if (headIndex == null) {
      throw new SsurgeonParseException("SplitWord expected a -headIndex, 0-indexed for the word piece to use when chopping up the word");
    }
    this.headIndex = headIndex;

    if (relation == null && (pieceRelations == null || pieceRelations.isEmpty())) {
      throw new SsurgeonParseException("SplitWord expected a -reln to represent the dependency to use for the new words");
    }
    if (relation != null && pieceRelations != null && !pieceRelations.isEmpty()) {
      throw new SsurgeonParseException("SplitWord expected either one -reln for every new word, or one for each word, not both");
    }
    this.relation = relation;
    if (relation == null) {
      for (Integer index : pieceRelations.keySet()) {
        if (index < 0 || index >= nodePieces.size()) {
          throw new SsurgeonParseException("SplitWord got an index in -reln which was not one of the split pieces, " + index + " (this is 0-indexed)");
        }
      }
      for (int i = 0; i < nodePieces.size(); ++i) {
        if (i != headIndex && !pieceRelations.containsKey(i)) {
          throw new SsurgeonParseException("SplitWord got a -reln for each piece, but none for piece " + i + " (this is 0-indexed)");
        }
      }
      this.pieceRelations = new HashMap<>(pieceRelations);
    } else {
      this.pieceRelations = Collections.emptyMap();
    }

    this.nodeNames = splitNames(nodeNames, "name", nodePieces.size());
    this.edgeNames = splitNames(edgeNames, "edge", nodePieces.size());

    if (this.edgeNames.containsKey(this.headIndex)) {
      throw new SsurgeonParseException("SplitWord received an edge name for " + this.headIndex + ", which is the head of the new phrase");
    }
  }

  /**
   * Renders a name map as the 0=foo,1=bar the parser reads, in index order
   */
  static String joinNames(Map<Integer, ?> names) {
    List<Integer> indices = new ArrayList<>(names.keySet());
    Collections.sort(indices);
    StringBuilder buf = new StringBuilder();
    for (Integer index : indices) {
      if (buf.length() > 0) {
        buf.append(",");
      }
      buf.append(index).append("=").append(names.get(index));
    }
    return buf.toString();
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    buf.write("\t");
    buf.write("-node " + node + "\t");
    if (nodeRegex != null) {
      for (Pattern regex : nodeRegex) {
        buf.write("-regex " + regex + "\t");
      }
    } else {
      for (String piece : exactPieces) {
        buf.write("-exact " + piece + "\t");
      }
    }
    if (relation != null) {
      buf.write("-reln " + relation.toString() + "\t");
    } else {
      buf.write("-reln " + joinNames(pieceRelations) + "\t");
    }
    if (nodeNames.size() > 0) {
      buf.write("-name " + joinNames(nodeNames) + "\t");
    }
    if (edgeNames.size() > 0) {
      buf.write("-edge " + joinNames(edgeNames) + "\t");
    }
    if (siblings) {
      buf.write("-siblings true\t");
    }
    buf.write("-headIndex " + headIndex);
    return buf.toString();
  }

  /** The relation for the new piece with the given index */
  private GrammaticalRelation relationFor(int index) {
    return relation != null ? relation : pieceRelations.get(index);
  }

  /**
   * Splits the word in every graph of the sentence which has it.
   *<br>
   * The words after the matched node move down to make room for the
   * pieces, along with any empty words after the matched node, which end
   * up after the last piece.  The head piece keeps the matched node and
   * its edges in each graph.  Each new piece goes into every graph which
   * has the matched node, attached to the head piece or, with -siblings,
   * to each governor the matched node has in that graph.
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    IndexedWord matchedNode = sm.getNode(node);
    if (matchedNode == null) {
      return false;
    }
    SemanticGraph basic = graphs.getDefault();
    String origWord = matchedNode.word();

    // the new pieces attach to the governors of the matched node, so a node
    // with none of them cannot be split this way.  checked before anything
    // is edited, since a graph half split is worse than one not split
    if (siblings && (!basic.containsVertex(matchedNode) || basic.incomingEdgeList(matchedNode).size() == 0)) {
      return false;
    }

    // first, iterate over the regex patterns we had at creation time
    //
    // each new word created will be the concatenation of all of the
    // matching groups from this pattern
    List<String> words;
    if (exactPieces != null) {
      words = new ArrayList<>(exactPieces);
    } else {
      words = new ArrayList<>();
      for (int i = 0; i < nodeRegex.size(); ++i) {
        Matcher regexMatcher = nodeRegex.get(i).matcher(origWord);
        if (!regexMatcher.matches()) {
          return false;
        }

        StringBuilder newWordBuilder = new StringBuilder();
        for (int j = 0; j < regexMatcher.groupCount(); ++j) {
          newWordBuilder.append(regexMatcher.group(j+1));
        }
        String newWord = newWordBuilder.toString();
        if (newWord.length() == 0) {
          return false;
        }
        words.add(newWord);
      }
    }

    final int matchedIndex = matchedNode.index();
    final int lastIndex = matchedIndex + words.size() - 1;

    // the graphs which have the matched node get the new pieces
    List<SemgrexGraphName> withNode = new ArrayList<>();
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph graph = graphs.get(name);
      if (graph != null && graph.containsVertex(matchedNode)) {
        withNode.add(name);
      }
    }

    // move the matched node to the place of the last piece, along with
    // everything after it, so that its empty words follow the last piece.
    // then the head piece, which keeps the matched node, goes to its own
    // place, leaving the empty words where they are
    SsurgeonUtils.moveNodes(graphs, sm, x -> (x >= matchedIndex), x -> x + words.size() - 1);
    if (matchedIndex + headIndex != lastIndex) {
      SsurgeonUtils.moveNode(graphs, sm, sm.getNode(node), matchedIndex + headIndex);
    }
    matchedNode = sm.getNode(node);
    matchedNode.setWord(words.get(headIndex));
    matchedNode.setValue(words.get(headIndex));

    if (pieceRelations.containsKey(headIndex)) {
      relabelIncoming(graphs, sm, withNode, matchedNode, pieceRelations.get(headIndex));
    }

    // read the governors after the moves above: those renumber the nodes,
    // and an edge held from before them names a vertex the graph no longer
    // has, which would put a stale copy of it back when used
    Map<SemgrexGraphName, List<IndexedWord>> governors = new EnumMap<>(SemgrexGraphName.class);
    for (SemgrexGraphName name : withNode) {
      List<IndexedWord> graphGovernors = new ArrayList<>();
      if (siblings) {
        for (SemanticGraphEdge edge : graphs.get(name).incomingEdgeList(matchedNode)) {
          graphGovernors.add(edge.getGovernor());
        }
      } else {
        graphGovernors.add(matchedNode);
      }
      governors.put(name, graphGovernors);
    }

    for (int i = 0; i < words.size(); ++i) {
      if (i == headIndex) {
        if (nodeNames.containsKey(i)) {
          sm.putNode(nodeNames.get(i), matchedNode);
        }
        continue;
      }

      // otherwise, add a word with the appropriate index,
      // then connect it to its governors
      // rather than adding the ability to set other values,
      // we insert the nodes and edges with names into the matcher,
      // allowing further edits to update those values
      IndexedWord newNode = new IndexedWord();
      newNode.setDocID(matchedNode.docID());
      newNode.setIndex(matchedIndex + i);
      newNode.setSentIndex(matchedNode.sentIndex());
      newNode.setWord(words.get(i));
      newNode.setValue(words.get(i));

      // the name of an edge refers to the first edge in the basic graph.
      // with -siblings, that is the one from the first governor
      SemanticGraphEdge namedEdge = null;
      for (SemgrexGraphName name : withNode) {
        SemanticGraph graph = graphs.get(name);
        graph.addVertex(newNode);
        for (IndexedWord governor : governors.get(name)) {
          SemanticGraphEdge newEdge = graph.addEdge(governor, newNode, relationFor(i), 0.0, false);
          if (namedEdge == null && name == SemgrexGraphName.BASIC) {
            namedEdge = newEdge;
          }
        }
      }

      if (nodeNames.containsKey(i)) {
        sm.putNode(nodeNames.get(i), newNode);
      }
      if (edgeNames.containsKey(i) && namedEdge != null) {
        sm.putNamedEdge(edgeNames.get(i), namedEdge, SemgrexGraphName.BASIC);
      }
    }

    return true;
  }

  /**
   * Gives each incoming edge of the node, in each of the graphs, the
   * relation.  A name in the matcher which referred to one of the edges
   * refers to its replacement.
   */
  private static void relabelIncoming(SemgrexGraphs graphs, SemgrexMatcher sm, List<SemgrexGraphName> withNode,
                                      IndexedWord node, GrammaticalRelation relation) {
    for (SemgrexGraphName name : withNode) {
      SemanticGraph graph = graphs.get(name);
      for (SemanticGraphEdge oldEdge : graph.incomingEdgeList(node)) {
        if (oldEdge.getRelation().equals(relation)) {
          continue;
        }
        graph.removeEdge(oldEdge);
        SemanticGraphEdge newEdge = graph.addEdge(oldEdge.getGovernor(), node, relation, oldEdge.getWeight(), oldEdge.isExtra());
        for (String edgeName : sm.getEdgeNames()) {
          if (sm.getEdge(edgeName) == oldEdge && sm.getEdgeGraph(edgeName) == name) {
            sm.putNamedEdge(edgeName, newEdge, name);
          }
        }
      }
    }
  }

}
