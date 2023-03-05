package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.*;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Generics;

public class AddNode extends SsurgeonEdit {
  public static final String LABEL="addNode";
  String nodeString = null;
  String nodeName = null;
  
  public AddNode(String nodeString, String nodeName) {
    this.nodeString = nodeString;
    this.nodeName = nodeName;
  }
  
  public static AddNode createAddNode(String nodeString, String nodeName) {
    return new AddNode(nodeString, nodeName);
  }
  
  public static AddNode createAddNode(IndexedWord node, String nodeName) {
    String nodeString = cheapWordToString(node);
    return new AddNode(nodeString, nodeName);
  }

  // TODO: can this be bombproofed if the node is already added?
  // otherwise, we can insist the user make sure the
  // node doesn't already exist, similar to Tsurgeon
  // Alternatively we could just not export this one and
  // make AddDep a bit more configurable.
  // This one is actually used in its current form in RTE
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord newNode = fromCheapString(nodeString);
    sg.addVertex(newNode);
    addNamedNode(newNode, nodeName);
    return true;
  }

  
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.NODE_PROTO_ARG);buf.write(" ");
    buf.write("\"");
    buf.write(nodeString);
    buf.write("\"\t");
    buf.write(Ssurgeon.NAME_ARG); buf.write("\t");
    buf.write(nodeName);
    return buf.toString();
  }

  public static final String WORD_KEY = "word";
  public static final String LEMMA_KEY = "lemma";
  public static final String VALUE_KEY = "value";
  public static final String CURRENT_KEY = "current";
  public static final String POS_KEY = "POS";
  public static final String TUPLE_DELIMITER="=";
  public static final String ATOM_DELIMITER = " ";

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

  public static String nullShield(String str) {
    return str == null ? "" : str;
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
}
