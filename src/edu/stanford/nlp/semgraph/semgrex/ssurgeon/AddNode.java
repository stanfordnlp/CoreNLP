package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.*;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Generics;

/**
 * Adds a new node, with no edges, built from a string such as
 * {@code {word=foo lemma=foo POS=NN value=foo current=foo}}
 * (see {@link #fromCheapString}).  The new node is registered with
 * the owning SsurgeonPattern under the given name.
 */
public class AddNode extends SsurgeonEdit {
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL="addNode";
  String nodeString = null;
  String nodeName = null;
  
  /**
   * Creates an AddNode.
   *
   * @param nodeString the description of the new node, in the format read by {@link #fromCheapString}
   * @param nodeName the name for the new node
   */
  public AddNode(String nodeString, String nodeName) {
    this.nodeString = nodeString;
    this.nodeName = nodeName;
  }
  
  /**
   * Creates an AddNode.
   *
   * @param nodeString the description of the new node, in the format read by {@link #fromCheapString}
   * @param nodeName the name for the new node
   * @return the new AddNode
   */
  public static AddNode createAddNode(String nodeString, String nodeName) {
    return new AddNode(nodeString, nodeName);
  }
  
  /**
   * Creates an AddNode which adds a node with the word, lemma, tag,
   * value and original text of the given node.
   *
   * @param node the prototype for the new node
   * @param nodeName the name for the new node
   * @return the new AddNode
   */
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

  /** Key for the word in a node string */
  public static final String WORD_KEY = "word";
  /** Key for the lemma in a node string */
  public static final String LEMMA_KEY = "lemma";
  /** Key for the value in a node string */
  public static final String VALUE_KEY = "value";
  /** Key for the original text in a node string */
  public static final String CURRENT_KEY = "current";
  /** Key for the tag in a node string */
  public static final String POS_KEY = "POS";
  /** Separates a key from its value in a node string */
  public static final String TUPLE_DELIMITER="=";
  /** Separates the key/value pairs in a node string */
  public static final String ATOM_DELIMITER = " ";

  /**
   * This converts the node into a simple string based representation.
   * NOTE: this is extremely brittle, and presumes values do not contain delimiters
   *
   * @param node the node to convert
   * @return the word, lemma, tag, value and original text of the node,
   *   in the format read by {@link #fromCheapString}
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
   * Replaces null with the empty string.
   *
   * @param str a String, possibly null
   * @return str, or the empty string if str is null
   */
  public static String nullShield(String str) {
    return str == null ? "" : str;
  }

  /**
   * Given the node arg string, converts it into an IndexedWord.
   * The first and last characters (the braces) are dropped without being checked,
   * and keys other than word, lemma, POS, value and current are ignored.
   *
   * @param rawArg the node string, such as {@code {word=foo lemma=foo}}
   * @return a new IndexedWord with those attributes; attributes missing from the string are null
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
