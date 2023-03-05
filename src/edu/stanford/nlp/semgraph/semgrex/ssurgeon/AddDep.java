package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Adds a new dependent node, based off of a prototype IndexedWord, with the given relation.
 * The new node's sentence index is inherited from the governing node.  Currently a cheap heuristic
 * is made, placing the new node as the leftmost child of the governing node.
 *
 * TODO: add position (a la Tregex)
 * TODO: determine consistent and intuitive arguments
 * TODO: because word position is important for certain features (such as bigram lexical overlap), need
 * ability to specify in which position the new node is inserted.
 *
 * @author Eric Yeh
 *
 */
public class AddDep extends SsurgeonEdit {
  public static final String LABEL = "addDep";
  final Map<String, String> attributes;
  final GrammaticalRelation relation;
  final String govNodeName;
  final double weight;

  /**
   * Creates an EnglishGrammaticalRelation AddDep edit.
   * @param newNode String representation of new dependent IndexedFeatureNode map.
   */
  public static AddDep createEngAddDep(String govNodeName, String engRelation,  Map<String, String> attributes) {
    GrammaticalRelation relation = EnglishGrammaticalRelations.valueOf(engRelation);
    return new AddDep(govNodeName, relation, attributes);
  }

  public AddDep(String govNodeName, GrammaticalRelation relation, Map<String, String> attributes) {
    this(govNodeName, relation, attributes, 0.0);
  }

  public AddDep(String govNodeName, GrammaticalRelation relation, Map<String, String> attributes, double weight) {
    // if there's an exception, we'll barf here rather than at runtime
    CoreLabel newNodeObj = fromCheapStrings(attributes);

    this.attributes = new TreeMap<>(attributes);
    this.relation = relation;
    this.govNodeName = govNodeName;
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
    buf.write(Ssurgeon.NODE_PROTO_ARG);buf.write(" ");
    buf.write("\"");
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

  /**
   * TODO: figure out how to specify where in the sentence this node goes.
   * TODO: determine if we should be copying an IndexedWord, or working just with a FeatureLabel.
   * TODO: bombproof if this gov, dep, and reln already exist.
   * TODO: This is not used anywhere, even in the old RTE code, so we can redo it however we want.
   *       Perhaps it could reorder the indices of the new nodes, for example
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord govNode = sm.getNode(govNodeName);
    // must make new copy of CoreLabel - if the same word is added
    // multiple times by the same operation, we don't want to have the
    // same backing CoreLabel in each instance
    CoreLabel newWord = fromCheapStrings(attributes);
    IndexedWord newNode = new IndexedWord(newWord);
    int newIndex = 0;
    for (IndexedWord node : sg.vertexSet()) {
      if (node.index() >= newIndex) {
        newIndex = node.index() + 1;
      }
    }
    newNode.setDocID(govNode.docID());
    newNode.setIndex(newIndex);
    newNode.setSentIndex(govNode.sentIndex());
    sg.addVertex(newNode);
    sg.addEdge(govNode, newNode, relation, weight, false);
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
    CoreLabel newWord = new CoreLabel(keys, values);
    if (newWord.value() == null && newWord.word() != null) {
      newWord.setValue(newWord.word());
    }
    return newWord;
  }
}
