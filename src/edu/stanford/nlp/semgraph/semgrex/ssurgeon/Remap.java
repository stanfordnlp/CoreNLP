package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;

/**
 * Looks up a variable of the match in a table, and sets a variable to
 * what it finds, for the edits after it to use.
 *<br>
 * For example, with a table {@code lemma_mapping} from wrong lemmas to
 * the right ones, this fixes each verb the table lists, and leaves the
 * rest alone:
 *<pre>{@code
 * {cpos:VERB;lemma:__#0%lemma}=verb
 * remap -var lemma -table lemma_mapping
 * editNode -node verb -lemma ${lemma}
 * }</pre>
 * The result goes in the same variable, or in the one {@code -to} names.
 * If the variable is not set, or the table has no entry for it, the
 * result is unset, so an edit after this one which uses it does nothing.
 *<br>
 * Setting a variable changes nothing in the graphs, so this never counts
 * as a change: iterate goes on to the next match unless a later edit
 * changes something.
 */
public class Remap extends SsurgeonEdit {
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "remap";

  /** The variable to look up */
  final String variable;
  /** The table to look it up in */
  final SsurgeonTable table;
  /** The variable to set to the result */
  final String target;

  /**
   * Creates an edit which looks up a variable in a table.
   *
   * @param variable the variable to look up
   * @param table the table to look it up in
   * @param target the variable to set to the result, or null for the variable looked up
   * @throws SsurgeonParseException if the variable or table is missing
   */
  public Remap(String variable, SsurgeonTable table, String target) {
    if (variable == null || variable.isEmpty()) {
      throw new SsurgeonParseException("Remap needs a variable to look up, given with " + Ssurgeon.VAR_ARG);
    }
    if (table == null) {
      throw new SsurgeonParseException("Remap needs a table, given with " + Ssurgeon.TABLE_ARG);
    }
    this.variable = variable;
    this.table = table;
    this.target = (target == null || target.isEmpty()) ? variable : target;
  }

  /**
   * Sets the target variable to the table's entry for the variable, or
   * unsets it if there is none.
   *
   * @return false, as the graphs are not changed
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatch sm) {
    String value = sm.getVariableString(variable);
    sm.putVariableString(target, value == null ? null : table.get(value));
    return false;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.VAR_ARG); buf.write(" "); buf.write(variable); buf.write("\t");
    buf.write(Ssurgeon.TABLE_ARG); buf.write(" "); buf.write(table.getName());
    if (!target.equals(variable)) {
      buf.write("\t");
      buf.write(Ssurgeon.TO_ARG); buf.write(" "); buf.write(target);
    }
    return buf.toString();
  }
}
