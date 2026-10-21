package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Adds a new dependent node, based off of a prototype IndexedWord, with the given relation.
 * The new node's sentence index is inherited from the governing node.
 *<br>
 * Nodes will be placed at the end of the sentence by default, at the
 * start or end using "-position -" or "-position +", or
 * before or after a node with "-position -word" and "-position +word".
 * An empty word stays after the word it follows, so a node put after
 * a word goes after any empty words which follow it, and a node put
 * before a word goes after any empty words which follow the word before.
 * A node cannot be put directly before an empty word.
 *<br>
 * The new node goes into every graph of the sentence, so the graphs
 * keep sharing their words.  Its edge goes into each graph which has
 * the governor, or with "-graph", into that graph alone.
 *<br>
 * With "-name", the new node is added to the match under that name, so
 * later edits for the same match can refer to it.  Each match adds its
 * own node, and the name refers to the one added for that match.  If the
 * match already has a node by that name, such as one an optional relation
 * of the semgrex pattern found, no node is added, so the edit fills in a
 * node only where one is missing.
 *
 * @author Eric Yeh
 *
 */
public class AddDep extends SsurgeonEdit {
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "addDep";
  final Map<String, String> attributes;
  final GrammaticalRelation relation;
  final String govNodeName;
  final String position;
  final double weight;
  final String nodeName;
  /** The graph which gets the new edge, or null for every graph with the governor */
  final SemgrexGraphName graph;

  /**
   * Creates an AddDep.
   *
   * @param govNodeName the name of the matched node which governs the new node
   * @param relation the relation from the governor to the new node
   * @param attributes the attributes of the new node, such as word or lemma; the map is copied
   * @param position where to put the new node, or null for the end of the sentence.
   *   Must start with {@code -} or {@code +}
   * @param weight the weight of the new edge
   * @param nodeName the name to give the new node in the match, or null for no name
   * @param graph the graph which gets the new edge, or null for every graph which has the governor
   * @throws SsurgeonParseException if govNodeName or relation is null,
   *   the position is invalid, or the attributes can't be used for a new node
   */
  public AddDep(String govNodeName, GrammaticalRelation relation, Map<String, String> attributes, String position, double weight, String nodeName, SemgrexGraphName graph) {
    if (position != null) {
      if (!position.startsWith("-") && !position.startsWith("+")) {
        throw new SsurgeonParseException("Unknown position " + position + " in AddDep operation");
      }
    }
    if (govNodeName == null) {
      throw new SsurgeonParseException("No governor given for an AddDep");
    }
    if (relation == null) {
      throw new SsurgeonParseException("No relation given for an AddDep");
    }
    checkIllegalAttributes(attributes);

    this.attributes = new TreeMap<>(attributes);
    this.relation = relation;
    this.govNodeName = govNodeName;
    this.position = position;
    this.weight = weight;
    this.nodeName = nodeName;
    this.graph = graph;
  }

  /**
   * Emits a parseable instruction string.
   */
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);  buf.write("\t");
    buf.write(Ssurgeon.GOV_NODENAME_ARG);buf.write(" ");
    buf.write(govNodeName); buf.write("\t");
    buf.write(Ssurgeon.RELN_ARG);buf.write(" ");
    buf.write(relation.toString()); buf.write("\t");

    if (position != null) {
      buf.write(Ssurgeon.POSITION_ARG);buf.write(" ");
      buf.write(position);buf.write("\t");
    }

    if (nodeName != null) {
      buf.write(Ssurgeon.NAME_ARG);buf.write(" ");
      buf.write(nodeName);buf.write("\t");
    }

    if (graph != null) {
      buf.write(Ssurgeon.GRAPH_ARG);buf.write(" ");
      buf.write(graph.lowerName);buf.write("\t");
    }

    for (String key : attributes.keySet()) {
      buf.write("-");
      buf.write(key);
      buf.write(" ");
      buf.write("\"");
      buf.write(attributes.get(key));
      buf.write("\"\t");
    }

    if (this.weight != 0.0) {
      buf.write(Ssurgeon.WEIGHT_ARG);buf.write(" ");
      buf.write(String.valueOf(weight));
    }
    return buf.toString();
  }

  /**
   * Adds the node, as {@link #evaluate(SemgrexGraphs, SemgrexMatcher)} does
   * for a sentence with only this graph.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    return evaluate(new SemgrexGraphs(sg), sm);
  }

  /**
   * Adds the new node to every graph of the sentence, and its edge from the
   * governor to the graph this edit names, or to each graph with the governor.
   *<br>
   * The words from the new node's index on move up by one in every graph
   * to make room for it.  This does nothing if the governor, or the node
   * the position names, is not in the match, if the graph this edit names
   * has not got the governor, or if the match already has a node by the
   * name this edit gives its new node.
   *<br>
   * Otherwise, nothing checks whether the governor already has such a
   * dependent, so the semgrex pattern has to stop matching once the node
   * is added, such as with {@code !> {word:blue}}, or find it with an
   * optional relation and name it as this edit does.  Otherwise each new
   * match adds another.
   *
   * @throws SsurgeonRuntimeException if the position is directly before an empty word
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    if (nodeName != null && sm.getNode(nodeName) != null) {
      // the match already has this node, so there is nothing to fill in
      return false;
    }
    IndexedWord govNode = sm.getNode(govNodeName);
    if (govNode == null) {
      return false;
    }
    List<SemanticGraph> edgeGraphs = new ArrayList<>();
    if (graph != null) {
      SemanticGraph sg = graphs.get(graph);
      if (sg == null || !sg.containsVertex(govNode)) {
        return false;
      }
      edgeGraphs.add(sg);
    } else {
      for (SemgrexGraphName name : SemgrexGraphName.values()) {
        SemanticGraph sg = graphs.get(name);
        if (sg != null && sg.containsVertex(govNode)) {
          edgeGraphs.add(sg);
        }
      }
    }

    final int newIndex;
    if (position == null || position.equals("+")) {
      // after every word, including any empty words after the last one
      newIndex = maxIndex(graphs) + 1;
    } else if (position.equals("-")) {
      // before every word, but after any empty words before the first one
      newIndex = minWordIndex(graphs);
    } else {
      IndexedWord target = sm.getNode(position.substring(1));
      if (target == null) {
        return false;
      }
      if (position.startsWith("-")) {
        if (target.getEmptyIndex() != 0) {
          throw new SsurgeonRuntimeException("AddDep cannot put a word directly before the empty word " + target.toCopyOrEmptyIndex() + ", as an empty word follows the word before it");
        }
        newIndex = target.index();
      } else {
        newIndex = target.index() + 1;
      }
    }
    SsurgeonUtils.moveNodes(graphs, sm, x -> x >= newIndex, x -> x + 1);
    // the governor may have moved
    govNode = sm.getNode(govNodeName);

    // must make new copy of CoreLabel - if the same word is added
    // multiple times by the same operation, we don't want to have the
    // same backing CoreLabel in each instance
    CoreLabel newWord = fromCheapStrings(attributes);
    IndexedWord newNode = new IndexedWord(newWord);
    newNode.setDocID(govNode.docID());
    newNode.setIndex(newIndex);
    newNode.setSentIndex(govNode.sentIndex());

    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg != null) {
        sg.addVertex(newNode);
      }
    }
    for (SemanticGraph sg : edgeGraphs) {
      sg.addEdge(govNode, newNode, relation, weight, false);
    }
    if (nodeName != null) {
      sm.putNode(nodeName, newNode);
    }
    return true;
  }

  /** The largest index of any node in the graphs */
  private static int maxIndex(SemgrexGraphs graphs) {
    int index = Integer.MIN_VALUE;
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg != null) {
        for (IndexedWord word : sg.vertexSet()) {
          index = Math.max(index, word.index());
        }
      }
    }
    return index;
  }

  /** The smallest index of any word in the graphs, leaving out empty words */
  private static int minWordIndex(SemgrexGraphs graphs) {
    int index = Integer.MAX_VALUE;
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg != null) {
        for (IndexedWord word : sg.vertexSet()) {
          if (word.getEmptyIndex() == 0) {
            index = Math.min(index, word.index());
          }
        }
      }
    }
    return index;
  }

  /**
   * AddDep changes the words of every graph, so the graphs still describe the same words.
   *
   * @return true
   */
  @Override
  public boolean changesWordsInEveryGraph() {
    return true;
  }

  /**
   * The graph this edit puts its edge in, if it names one other than the basic graph.
   */
  @Override
  public Set<SemgrexGraphName> requiredGraphs() {
    return graph == null || graph == SemgrexGraphName.BASIC ? Collections.emptySet() : Collections.singleton(graph);
  }

  /**
   * Certain attributes cannot be edited, especially docid, sentid, idx,
   * or they mess up the hashmaps in the SemanticGraph
   *
   * @param attributes the attribute keys and values for a node
   * @throws SsurgeonParseException if the attributes include idx, sentIndex, or docID,
   *   or if they can't be converted to a CoreLabel
   */
  public static void checkIllegalAttributes(Map<String, String> attributes) {
    if (attributes.containsKey("idx")) {
      throw new SsurgeonParseException("Cannot manually set the index attribute.  If you need a moveWord operation, please file an issue on github.");
    }
    if (attributes.containsKey("sentIndex")) {
      throw new SsurgeonParseException("Cannot manually change the sentence index.  If you need an operation to change an entire sentence's sentIndex, please file an issue on github.");
    }
    if (attributes.containsKey("docID")) {
      throw new SsurgeonParseException("Cannot manually change a document ID.  If you need an operation to change an entire sentence's document ID, please file an issue on github.");
    }

    // if there's an exception, we'll barf when creating the pattern rather than at runtime
    try {
      CoreLabel newNodeObj = fromCheapStrings(attributes);
    } catch (UnsupportedOperationException e) {
      throw new SsurgeonParseException("Unable to process node attribute keys for Ssurgeon operation", e);
    }
  }

  /**
   * Given the keys and values of the CoreAnnotation attributes,
   * build a CoreLabel to use as the new word.
   * If there is no value attribute, the value is set to the word.
   *
   * @param attributes the attribute keys and values for the new word
   * @return a new CoreLabel with those attributes
   */
  public static CoreLabel fromCheapStrings(Map<String, String> attributes) {
    String[] keys = new String[attributes.size()];
    String[] values = new String[attributes.size()];
    int idx = 0;
    for (String key : attributes.keySet()) {
      String value = attributes.get(key);
      keys[idx] = key;
      values[idx] = value;
      ++idx;
    }
    final CoreLabel newWord = new CoreLabel(keys, values);
    if (newWord.value() == null && newWord.word() != null) {
      newWord.setValue(newWord.word());
    }
    return newWord;
  }
}
