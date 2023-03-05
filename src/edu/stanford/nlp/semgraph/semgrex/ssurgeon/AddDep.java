package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Adds a new dependent node, based off of a prototype IndexedWord, with the given relation.
 * The new node's sentence index is inherited from the governing node.  Currently a cheap heuristic
 * is made, placing the new node as the leftmost child of the governing node.
 *
 * @author Eric Yeh
 *
 */
public class AddDep extends SsurgeonEdit {
  public static final String LABEL = "addDep";
  final Map<String, String> attributes;
  final GrammaticalRelation relation;
  final String govNodeName;
  final String position;
  final double weight;

  /**
   * Creates an EnglishGrammaticalRelation AddDep edit.
   * @param newNode String representation of new dependent IndexedFeatureNode map.
   */
  public static AddDep createEngAddDep(String govNodeName, String engRelation,  Map<String, String> attributes, String position) {
    GrammaticalRelation relation = EnglishGrammaticalRelations.valueOf(engRelation);
    return new AddDep(govNodeName, relation, attributes, position);
  }

  public AddDep(String govNodeName, GrammaticalRelation relation, Map<String, String> attributes, String position) {
    this(govNodeName, relation, attributes, position, 0.0);
  }

  public AddDep(String govNodeName, GrammaticalRelation relation, Map<String, String> attributes, String position, double weight) {
    // if there's an exception, we'll barf here rather than at runtime
    try {
      CoreLabel newNodeObj = fromCheapStrings(attributes);
    } catch (UnsupportedOperationException e) {
      throw new SsurgeonParseException("Unable to process keys for AddDep operation", e);
    }

    if (position != null) {
      if (!position.startsWith("-") && !position.startsWith("+")) {
        throw new SsurgeonParseException("Unknown position " + position + " in AddDep operation");
      }
    }

    this.attributes = new TreeMap<>(attributes);
    this.relation = relation;
    this.govNodeName = govNodeName;
    this.position = position;
    this.weight = 0;
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

    for (String key : attributes.keySet()) {
      buf.write("-");
      buf.write(key);
      buf.write(" ");
      buf.write(attributes.get(key));
      buf.write("\"\t");
    }

    buf.write(Ssurgeon.WEIGHT_ARG);buf.write(" ");
    buf.write(String.valueOf(weight));
    return buf.toString();
  }

  // TODO: update the SemgrexMatcher
  // currently the Ssurgeon will not be able to proceed after this edit
  // since all of the node and edge pointers will be rewritten
  public static void moveNode(SemanticGraph sg, IndexedWord word, int newIndex) {
    List<SemanticGraphEdge> outgoing = sg.outgoingEdgeList(word);
    List<SemanticGraphEdge> incoming = sg.incomingEdgeList(word);
    boolean isRoot = sg.isRoot(word);
    sg.removeVertex(word);

    IndexedWord newWord = new IndexedWord(word.backingLabel());
    newWord.setIndex(newIndex);

    // could be more expensive than necessary if we move multiple roots,
    // but the expectation is there is usually only the 1 root
    if (isRoot) {
      Set<IndexedWord> newRoots = new HashSet<>(sg.getRoots());
      newRoots.remove(word);
      newRoots.add(newWord);
      sg.setRoots(newRoots);
    }

    for (SemanticGraphEdge oldEdge : outgoing) {
      SemanticGraphEdge newEdge = new SemanticGraphEdge(newWord, oldEdge.getTarget(), oldEdge.getRelation(), oldEdge.getWeight(), oldEdge.isExtra());
      sg.addEdge(newEdge);
    }

    for (SemanticGraphEdge oldEdge : incoming) {
      SemanticGraphEdge newEdge = new SemanticGraphEdge(oldEdge.getSource(), newWord, oldEdge.getRelation(), oldEdge.getWeight(), oldEdge.isExtra());
      sg.addEdge(newEdge);
    }
  }

  public static void moveNodes(SemanticGraph sg, Function<Integer, Boolean> shouldMove, Function<Integer, Integer> destination) {
    // iterate first, then move, so that we don't screw up the graph while iterating
    List<IndexedWord> toMove = sg.vertexSet().stream().filter(x -> shouldMove.apply(x.index())).collect(Collectors.toList());
    Collections.sort(toMove);
    Collections.reverse(toMove);
    for (IndexedWord word : toMove) {
      moveNode(sg, word, destination.apply(word.index()));
    }
  }

  /**
   * TODO: figure out how to specify where in the sentence this node goes.
   *   currently allows - and + for start and end.  before and after
   *   matched node would be good
   * TODO: bombproof if this gov, dep, and reln already exist.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord govNode = sm.getNode(govNodeName);
    // must make new copy of CoreLabel - if the same word is added
    // multiple times by the same operation, we don't want to have the
    // same backing CoreLabel in each instance
    CoreLabel newWord = fromCheapStrings(attributes);
    IndexedWord newNode = new IndexedWord(newWord);
    final int tempIndex;
    final int newIndex;
    if (position != null && !position.equals("+")) {
      // +2 to leave room: we will increase all other nodes with the
      // proper index, so we need +1 of room, then another +1 for
      // a temp place to put this node
      // TODO: when we implement updating the SemgrexMatcher,
      // this won't be necessary
      tempIndex = SemanticGraphUtils.maxIndex(sg) + 2;

      if (position.equals("-")) {
        newIndex = SemanticGraphUtils.minIndex(sg);
      } else if (position.startsWith("-") || position.startsWith("+")) {
        String targetName = position.substring(1);
        IndexedWord target = sm.getNode(targetName);
        if (target == null) {
          return false;
        }
        if (position.startsWith("-")) {
          // it will be exactly to the left rather than pushing over
          // something a word earlier if we do .index(), not .index() - 1
          newIndex = target.index();
        } else {
          newIndex = target.index() + 1;
        }
      } else {
        throw new UnsupportedOperationException("Unknown position in AddDep: |" + position + "|");
      }
    } else {
      tempIndex = SemanticGraphUtils.maxIndex(sg) + 1;
      newIndex = -1;
    }

    newNode.setDocID(govNode.docID());
    newNode.setIndex(tempIndex);
    newNode.setSentIndex(govNode.sentIndex());

    sg.addVertex(newNode);
    sg.addEdge(govNode, newNode, relation, weight, false);

    if (position != null && !position.equals("+")) {
      // the payoff for tempIndex == maxIndex + 2:
      // everything will be moved one higher, unless it's the new node
      moveNodes(sg, x -> (x >= newIndex && x != tempIndex), x -> x+1);
      moveNode(sg, newNode, newIndex);
    }

    return true;
  }

  /**
   * Given the keys and values of the CoreAnnotation attributes,
   * build a CoreLabel to use as the new word
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
