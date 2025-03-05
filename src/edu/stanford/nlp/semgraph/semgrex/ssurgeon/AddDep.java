package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Adds a new dependent node, based off of a prototype IndexedWord, with the given relation.
 * The new node's sentence index is inherited from the governing node.
 *<br>
 * Nodes will be placed at the end of the sentence by default, at the
 * start or end using "-position -" or "-position +", or
 * before or after a node with "-position -word" and "-position +word"
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

  public AddDep(String govNodeName, GrammaticalRelation relation, Map<String, String> attributes, String position) {
    this(govNodeName, relation, attributes, position, 0.0);
  }

  public AddDep(String govNodeName, GrammaticalRelation relation, Map<String, String> attributes, String position, double weight) {
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

    if (this.weight != 0.0) {
      buf.write(Ssurgeon.WEIGHT_ARG);buf.write(" ");
      buf.write(String.valueOf(weight));
    }
    return buf.toString();
  }

  /**
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
      // TODO: we could theoretically put the new node in the right place
      // immediately and move the other nodes, but this is easier
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
      SsurgeonUtils.moveNodes(sg, sm, x -> (x >= newIndex && x != tempIndex), x -> x+1, true);
      SsurgeonUtils.moveNode(sg, sm, newNode, newIndex);
    }

    return true;
  }

  /**
   * Certain attributes cannot be edited, especially docid, sentid, idx,
   * or they mess up the hashmaps in the SemanticGraph
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
