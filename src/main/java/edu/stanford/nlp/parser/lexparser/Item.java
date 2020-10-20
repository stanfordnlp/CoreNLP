package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.util.Scored;


/** Abstract class for parse items.
 *
 *  @author Dan Klein
 */
abstract public class Item implements Scored {

  public int start;
  public int end;
  public int state;
  public int head;
  public int tag;
  public Edge backEdge;
  public double iScore = Double.NEGATIVE_INFINITY;
  public double oScore = Double.NEGATIVE_INFINITY;
  
  private final boolean exhaustiveTest;

  public Item(boolean exhaustiveTest) {
    this.exhaustiveTest = exhaustiveTest;
  }
  
  public Item(Item item) {
    start = item.start;
    end = item.end;
    state = item.state;
    head = item.head;
    tag = item.tag;
    backEdge = item.backEdge;
    iScore = item.iScore;
    oScore = item.oScore;
    this.exhaustiveTest = item.exhaustiveTest;
  }
  
  public double score() {
    if (exhaustiveTest) {
      return iScore;
    } else {
      return iScore + oScore;
    }
  }

  public boolean isEdge() {
    return false;
  }

  public boolean isPreHook() {
    return false;
  }

  public boolean isPostHook() {
    return false;
  }

}
