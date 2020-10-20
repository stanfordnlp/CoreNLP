package edu.stanford.nlp.parser.lexparser;

/** Class for parse edges.
 *
 *  @author Dan Klein
 */
public class Edge extends Item {

  public Hook backHook;

  public Edge(boolean exhaustiveTest) {
  	super(exhaustiveTest);
  }
  
  public Edge(Edge e) {
  	super(e);
  	backHook = e.backHook;
  }
  
  @Override
  public boolean isEdge() {
    return true;
  }

  @Override
  public String toString() {
    // TODO: used to contain more useful information
    //return "Edge(" + Numberer.getGlobalNumberer("states").object(state) + ":" + start + "-" + end + "," + head + "/" + Numberer.getGlobalNumberer("tags").object(tag) + ")";
    return "Edge(" + state + ":" + start + "-" + end + "," + head + "/" + tag + ")";
  }

  @Override
  public int hashCode() {
    return (state << 1) ^ (head << 8) ^ (tag << 16) ^ (start << 4) ^ (end << 24);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof Edge) {
      Edge e = (Edge) o;
      if (state == e.state && head == e.head && tag == e.tag && start == e.start && end == e.end) {
        return true;
      }
    }
    return false;
  }

}
