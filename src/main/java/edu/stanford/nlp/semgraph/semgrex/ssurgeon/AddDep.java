package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.util.Generics;

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
  IndexedWord newNodePrototype;
  GrammaticalRelation relation;
  String govNodeName;
  double weight;

  /**
   * Creates an EnglishGrammaticalRelation AddDep edit.
   * @param newNode String representation of new dependent IndexedFeatureNode map.
   */
  public static AddDep createEngAddDep(String govNodeName, String engRelation,  String newNode) {
    GrammaticalRelation relation = EnglishGrammaticalRelations.valueOf(engRelation);
//  IndexedWord newNodeObj = new IndexedWord(CoreLabel.fromAbstractMapLabel(IndexedFeatureLabel.valueOf(newNode, MapFactory.HASH_MAP_FACTORY)));
    IndexedWord newNodeObj = fromCheapString(newNode);
    return new AddDep(govNodeName, relation, newNodeObj);
  }

  public AddDep(String govNodeName, GrammaticalRelation relation, IndexedWord newNodePrototype) {
    this.newNodePrototype = newNodePrototype;
    this.relation = relation;
    this.govNodeName = govNodeName;
    this.weight = 0;
  }

  public AddDep(String govNodeName, GrammaticalRelation relation, IndexedWord newNodePrototype, double weight) {
    this(govNodeName, relation, newNodePrototype);
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
    buf.write(Ssurgeon.NODE_PROTO_ARG);buf.write(" ");
    buf.write("\"");
//  buf.write(newNodePrototype.toString("map")); buf.write("\"\t")
    buf.write(cheapWordToString(newNodePrototype));
    buf.write("\"\t");

    buf.write(Ssurgeon.WEIGHT_ARG);buf.write(" ");
    buf.write(String.valueOf(weight));
    return buf.toString();
  }

  /**
   * TODO: figure out how to specify where in the sentence this node goes.
   * TODO: determine if we should be copying an IndexedWord, or working just with a FeatureLabel.
   * TODO: bombproof if this gov, dep, and reln already exist.
   */
  @Override
  public void evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord govNode = sm.getNode(govNodeName);
    IndexedWord newNode = new IndexedWord(newNodePrototype);
    int newIndex = SemanticGraphUtils.leftMostChildVertice(govNode, sg).index(); // cheap En-specific hack for placing copula (beginning of governing phrase)
    newNode.setDocID(govNode.docID());
    newNode.setIndex(newIndex);
    newNode.setSentIndex(govNode.sentIndex());
    sg.addVertex(newNode);
    sg.addEdge(govNode, newNode, relation, weight,false);
  }

  public static final String WORD_KEY = "word";
  public static final String LEMMA_KEY = "lemma";
  public static final String VALUE_KEY = "value";
  public static final String CURRENT_KEY = "current";
  public static final String POS_KEY = "POS";
  public static final String TUPLE_DELIMITER="=";
  public static final String ATOM_DELIMITER = " ";

  // Simple mapping of all the stuff we care about (until IndexedFeatureLabel --> CoreLabel map pain is fixed)
  /**
   * This converts the node into a simple string based representation.
   * NOTE: this is extremely brittle, and presumes values do not contain delimiters
   */
  public static String cheapWordToString(IndexedWord node) {
    StringWriter buf = new StringWriter();
    buf.write("{");
    buf.write(WORD_KEY);
    buf.write(TUPLE_DELIMITER);
    buf.write(nullShield(node.word()));
    buf.write(ATOM_DELIMITER);

    buf.write(LEMMA_KEY);
    buf.write(TUPLE_DELIMITER);
    buf.write(nullShield(node.lemma()));
    buf.write(ATOM_DELIMITER);

    buf.write(POS_KEY);
    buf.write(TUPLE_DELIMITER);
    buf.write(nullShield(node.tag()));
    buf.write(ATOM_DELIMITER);

    buf.write(VALUE_KEY);
    buf.write(TUPLE_DELIMITER);
    buf.write(nullShield(node.value()));
    buf.write(ATOM_DELIMITER);

    buf.write(CURRENT_KEY);
    buf.write(TUPLE_DELIMITER);
    buf.write(nullShield(node.originalText()));
    buf.write("}");
    return buf.toString();
  }

  /**
   * Given the node arg string, converts it into an IndexedWord.
   */
  public static IndexedWord fromCheapString(String rawArg) {
    String arg = rawArg.substring(1, rawArg.length()-1);
    String[] tuples=arg.split(ATOM_DELIMITER);
    Map<String,String> args = Generics.newHashMap();
    for (String tuple : tuples) {
      String[] vals = tuple.split(TUPLE_DELIMITER);
      String key = vals[0];
      String value = "";
      if (vals.length == 2)
        value = vals[1];
      args.put(key, value);
    }
    IndexedWord newWord = new IndexedWord();
    newWord.setWord(args.get(WORD_KEY));
    newWord.setLemma(args.get(LEMMA_KEY));
    newWord.setTag(args.get(POS_KEY));
    newWord.setValue(args.get(VALUE_KEY));
    newWord.setOriginalText(args.get(CURRENT_KEY));
    return newWord;
  }

  public static String nullShield(String str) {
    return str == null ? "" : str;
  }
}
