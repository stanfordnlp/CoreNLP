package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.Ssurgeon;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonRuntimeException;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonWordlist;

/**
 * Tests whether a field of the matched node is in a wordlist resource
 * registered with {@link Ssurgeon}.
 */
public class WordlistTest extends NodeTest {
  /** Which field of the node is looked up in the wordlist */
  public static enum TYPE {
    /** The lemma, lowercased */
    lemma,
    /** The last whitespace separated token of the original text, lowercased */
    current_lasttoken,
    /** Either the lowercased lemma or the lowercased last token of the original text */
    lemma_and_currlast,
    /** The word, with its case unchanged */
    word,
    /** The POS tag, with its case unchanged */
    pos
  };
  private TYPE type;
  private String resourceID;
  private String myID;

  /**
   * Creates a wordlist test.
   *
   * @param myID the ID of this test
   * @param resourceID the ID of the wordlist resource; it is looked up each time the test is evaluated
   * @param type the name of the {@link TYPE} to test, which must match exactly
   * @param matchName the name of the node in the Semgrex match to test
   * @throws IllegalArgumentException if type is not the name of a {@link TYPE}
   */
  public WordlistTest(String myID, String resourceID, String type, String matchName) {
    super(matchName);
    this.resourceID = resourceID;
    this.myID = myID;
    this.type = TYPE.valueOf(type);
  }

  /**
   * Checks to see if the given node's field matches the resource
   */
  @Override
  protected boolean evaluate(IndexedWord node) {
    SsurgeonWordlist wl = Ssurgeon.inst().getResource(resourceID);
    if (wl == null) {
      throw new SsurgeonRuntimeException("No wordlist resource with ID="+resourceID);
    }
    if (type == TYPE.lemma)
      return wl.contains(node.lemma().toLowerCase());
    if (type == TYPE.current_lasttoken)
    {
      // This is done in special case, where tokens are collapsed.  Here, we
      // take the last token of the current value for the node and compare against
      // that.
      String[] tokens = node.originalText().split("\\s+");
      String lastCurrent = tokens[tokens.length-1].toLowerCase();
      return wl.contains(lastCurrent);
    }
    else if (type == TYPE.lemma_and_currlast)
    {
      // test against both the lemma and the last current token
      String[] tokens = node.originalText().split("\\s+");
      String lastCurrent = tokens[tokens.length-1].toLowerCase();
      return wl.contains(node.lemma().toLowerCase()) || wl.contains(lastCurrent);
    }
    else if (type == TYPE.word)
      return wl.contains(node.word());
    else if (type == TYPE.pos)
      return wl.contains(node.tag());
    else
      return false;
  }


  @Override
  public String getDisplayName() {
    return "wordlist-test :type "+type+" :resourceID "+resourceID;
  }

  @Override
  public String getID() {
    return myID;
  }

}
