package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.Ssurgeon;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonRuntimeException;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonWordlist;

public class WordlistTest extends NodeTest {
  public static enum TYPE {
    lemma, current_lasttoken, lemma_and_currlast, word, pos
  };
  private TYPE type;
  private String resourceID;
  private String myID;

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
