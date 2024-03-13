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
                             // TODO: separate the contractions so we can adjust the lemmas?
                             // In some other way fix those lemmas?
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
                             "    <notes>Edit a node's MWT for wanna</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/(?i)wan/;after://}=first . {word:/(?i)na/}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "    <edit-list>EditNode -node first -lemma want</edit-list>",
                             "    <edit-list>EditNode -node second -lemma to</edit-list>",
                             "  </ssurgeon-pattern>",
                             "  <ssurgeon-pattern>",
                             "    <uid>3b</uid>",
                             "    <notes>Edit a node's MWT for gonna</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/(?i)gon/;after://}=first . {word:/(?i)na/}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "    <edit-list>EditNode -node first -lemma go</edit-list>",
                             "    <edit-list>EditNode -node second -lemma to</edit-list>",
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
                             "    <edit-list>EditNode -node first -lemma it</edit-list>",
                             "    <edit-list>EditNode -node second -lemma be</edit-list>",
                             "  </ssurgeon-pattern>",
                             "  <ssurgeon-pattern>",
                             "    <uid>6</uid>",
                             "    <notes>Edit a node's MWT for dinna</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/(?i)din/}=first . {word:/(?i)na/}=second") + "</semgrex>",
                             "    <edit-list>CombineMWT -node first -node second</edit-list>",
                             "    <edit-list>EditNode -node first -lemma do</edit-list>",
                             "    <edit-list>EditNode -node second -lemma not</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(mwt);
    for (SsurgeonPattern editSsurgeon : patterns) {
      sg = editSsurgeon.iterate(sg).first;
    }
    return sg;
  }
}
