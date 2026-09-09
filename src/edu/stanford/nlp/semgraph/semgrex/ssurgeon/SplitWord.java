package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
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
 * Aside from the text and the dependency, the new node is rather bare bones.
 * Adding the -name argument allows for specifying a comma-separate list
 * of names which can be used to insert the new nodes into the SemgrexMatcher
 * as named nodes.  This will allow for further edits in the same edit step.
 * This list should be 0 indexed.
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
 * Since one -reln has to serve for every new piece, name the edges with
 * -edge and relabel them afterwards:
 * <pre>
 * semgrex:
 *   {word:/yla/}=split
 * ssurgeon:
 *   splitWord -node split -exact y -exact la -reln dep -headIndex 0 -siblings true -edge 1=second
 *   relabelNamedEdge -edge second -reln det
 * </pre>
 * <br>
 * A matched node with no governor at all, such as the root, cannot be
 * split this way, and the edit does nothing.
 *
 * @author John Bauer
 */
public class SplitWord extends SsurgeonEdit {
  public static final String LABEL = "splitWord";

  final String node;
  final List<Pattern> nodeRegex;
  final List<String> exactPieces;
  final int headIndex;
  final GrammaticalRelation relation;
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

  public SplitWord(String node, List<String> nodePieces, Integer headIndex, GrammaticalRelation relation, String nodeNames, String edgeNames, boolean exactSplit) {
    this(node, nodePieces, headIndex, relation, nodeNames, edgeNames, exactSplit, false);
  }

  public SplitWord(String node, List<String> nodePieces, Integer headIndex, GrammaticalRelation relation, String nodeNames, String edgeNames, boolean exactSplit, boolean siblings) {
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

    if (relation == null) {
      throw new SsurgeonParseException("SplitWord expected a -reln to represent the dependency to use for the new words");
    }
    this.relation = relation;

    this.nodeNames = splitNames(nodeNames, "name", nodePieces.size());
    this.edgeNames = splitNames(edgeNames, "edge", nodePieces.size());

    if (this.edgeNames.containsKey(this.headIndex)) {
      throw new SsurgeonParseException("SplitWord received an edge name for " + this.headIndex + ", which is the head of the new phrase");
    }
  }

  /**
   * Renders a name map as the 0=foo,1=bar the parser reads, in index order
   */
  static String joinNames(Map<Integer, String> names) {
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
    buf.write("-reln " + relation.toString() + "\t");
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

  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord matchedNode = sm.getNode(node);
    if (matchedNode == null) {
      return false;
    }
    String origWord = matchedNode.word();

    // the new pieces attach to the governors of the matched node, so a node
    // with none of them cannot be split this way.  checked before anything
    // is edited, since a graph half split is worse than one not split
    if (siblings && sg.incomingEdgeList(matchedNode).size() == 0) {
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

    int matchedIndex = matchedNode.index();

    // at this point, we can make new words out of each of the patterns

    // move all words down by nodeRegex.size() - 1
    // then move the original word down by headIndex
    SsurgeonUtils.moveNodes(sg, sm, x -> (x > matchedIndex), x -> x+words.size() - 1, true);
    // the head node has its word replaced, and its index & links need
    // to be rearranged, but none of the links are added or removed
    if (headIndex > 0) {
      SsurgeonUtils.moveNode(sg, sm, matchedNode, matchedIndex + headIndex);
    }
    matchedNode = sm.getNode(node);
    matchedNode.setWord(words.get(headIndex));
    matchedNode.setValue(words.get(headIndex));

    // read the governors after the moves above: those renumber the nodes,
    // and an edge held from before them names a vertex the graph no longer
    // has, which would put a stale copy of it back when used
    List<SemanticGraphEdge> governingEdges =
      siblings ? sg.incomingEdgeList(matchedNode) : null;

    for (int i = 0; i < words.size(); ++i) {
      if (i == headIndex) {
        if (nodeNames.containsKey(i)) {
          sm.putNode(nodeNames.get(i), matchedNode);
        }
        continue;
      }

      // otherwise, add a word with the appropriate index,
      // then connect it to matchedNode
      // rather than adding the ability to set other values,
      // we insert the nodes and edges with names into the matcher,
      // allowing further edits to update those values
      IndexedWord newNode = new IndexedWord();
      newNode.setDocID(matchedNode.docID());
      newNode.setIndex(matchedIndex + i);
      newNode.setSentIndex(matchedNode.sentIndex());
      newNode.setWord(words.get(i));
      newNode.setValue(words.get(i));

      sg.addVertex(newNode);
      SemanticGraphEdge newEdge;
      if (siblings) {
        // one edge from each governor of the matched node, so the new piece
        // sits alongside it rather than under it.  the first is the one a
        // name refers to, since a later edit can only relabel one edge
        newEdge = null;
        for (SemanticGraphEdge parentEdge : governingEdges) {
          SemanticGraphEdge siblingEdge = sg.addEdge(parentEdge.getGovernor(), newNode, relation, 0.0, false);
          if (newEdge == null) {
            newEdge = siblingEdge;
          }
        }
      } else {
        newEdge = sg.addEdge(matchedNode, newNode, relation, 0.0, false);
      }

      if (nodeNames.containsKey(i)) {
        sm.putNode(nodeNames.get(i), newNode);
      }
      if (edgeNames.containsKey(i)) {
        sm.putNamedEdge(edgeNames.get(i), newEdge);
      }
    }

    return true;
  }
}
