package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.Map;
import java.util.TreeMap;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * Edit an existing node to have new attributes.
 *
 * @author John Bauer
 */
public class EditNode extends SsurgeonEdit {
  public static final String LABEL = "editNode";

  final String nodeName;
  final Map<String, String> attributes;

  public EditNode(String nodeName, Map<String, String> attributes) {
    if (nodeName == null) {
      throw new SsurgeonParseException("Cannot make an EditNode with no nodeName");
    }
    if (attributes.size() == 0) {
      throw new SsurgeonParseException("Cannot make an EditNode with no attributes");
    }
    AddDep.checkIllegalAttributes(attributes);
    this.nodeName = nodeName;
    this.attributes = new TreeMap<>(attributes);
  }


  /**
   * Emits a parseable instruction string.
   */
  @Override
  public String toEditString() {
    StringBuilder buf = new StringBuilder();
    buf.append(LABEL);  buf.append("\t");
    buf.append(Ssurgeon.NODENAME_ARG);buf.append(" ");
    buf.append(nodeName); buf.append("\t");

    for (String key : attributes.keySet()) {
      buf.append("-");
      buf.append(key);
      buf.append(" ");
      buf.append(attributes.get(key));
      buf.append("\"\t");
    }

    return buf.toString();
  }

  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord word = sm.getNode(nodeName);
    if (word == null)
      return false;

    CoreLabel other = AddDep.fromCheapStrings(attributes);
    boolean changed = false;
    for (Class key : other.keySet()) {
      Object thisV = word.get(key);
      Object otherV = other.get(key);
      if (thisV == null || otherV == null) {
        if (thisV != null || otherV != null) {
          changed = true;
          word.set(key, otherV);
        }
      } else {
        if (!thisV.equals(otherV)) {
          changed = true;
          word.set(key, otherV);
        }
      }
    }

    return changed;
  }
}
