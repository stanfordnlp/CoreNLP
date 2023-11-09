package edu.stanford.nlp.trees.ud;

import java.util.List;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.Ssurgeon;
import edu.stanford.nlp.semgraph.semgrex.ssurgeon.SsurgeonPattern;
import edu.stanford.nlp.util.XMLUtils;

public class EnglishMWTCombiner {
  static final String newline = System.getProperty("line.separator");

  public SemanticGraph combineMWTs(SemanticGraph sg) {
    Ssurgeon inst = Ssurgeon.inst();

    // combine using the CombineMWT operation, using the default concatenation for the MWT text
    String mwt = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>1</uid>",
                             "    <notes>Edit a node's MWT for common contractions</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=first . {word:/(?i)'s|n't|'ll|'ve|'re|'d|s'|'m/}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "  </ssurgeon-pattern>",
                             "  <ssurgeon-pattern>",
                             "    <uid>2</uid>",
                             "    <notes>Edit a node's MWT for cannot</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/(?i)can/;after://}=first . {word:/(?i)not/}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "  </ssurgeon-pattern>",
                             "  <ssurgeon-pattern>",
                             "    <uid>3</uid>",
                             "    <notes>Edit a node's MWT for cannot</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/(?i)wan|gon/;after://}=first . {word:/(?i)na/}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "  </ssurgeon-pattern>",
                             "  <ssurgeon-pattern>",
                             "    <uid>4</uid>",
                             "    <notes>Edit a node's MWT for POS</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=first . {word:/'/;cpos:PART}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "  </ssurgeon-pattern>",
                             "  <ssurgeon-pattern>",
                             "    <uid>5</uid>",
                             "    <notes>Edit a node's MWT for 'tis and 'twas</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/'[tT]/}=first . {word:/(?i)is|was/}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(mwt);
    for (SsurgeonPattern editSsurgeon : patterns) {
      sg = editSsurgeon.iterate(sg).first;
    }
    return sg;
  }
}
