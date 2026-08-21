package edu.stanford.nlp.semgraph.semgrex.stats;

import java.util.List;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;

/**
 * A single statistics command from a semgrex stats script.
 *<br>
 * A stats script is a semgrex pattern on the first line followed by one
 * command per line, in the same spirit as an Ssurgeon script, except
 * that these commands only read the matches:
 *<pre>
 * {}=gov &gt;nsubj=edge {pos:/NOUN|PRON/}=dep
 * count edge
 * count dep gov
 *</pre>
 * Each command accumulates over every match in the corpus and then
 * reports.  Commands are independent of one another, so a script with
 * several of them produces several charts from a single pass over the
 * data.
 *<br>
 * To add a new command, implement this interface and register a factory
 * for it in {@link SemgrexStats}.  Deliberately, none of this lives in
 * the semgrex grammar: a command word such as {@code count} or
 * {@code mean} would otherwise become a reserved token in the lexer and
 * stop being usable as an ordinary identifier.
 *
 * @author John Bauer
 */
public interface SemgrexStat {
  /**
   * The command word this stat was parsed from, eg "count".
   */
  String name();

  /**
   * The named nodes, edges, and regex groups this command reads, in the order given.
   */
  List<String> getKeys();

  /**
   * Tally a single match.  Called once per match, in corpus order.
   */
  void accumulate(SemgrexMatch match);

  /**
   * Render the accumulated results.  Should end with a line separator.
   */
  String report();

  /**
   * Builds a SemgrexStat from the arguments on one line of a script.
   *<br>
   * The context carries the pattern of the stage this command belongs
   * to, so that a command can complain at parse time about arguments
   * which could never match anything, plus the sets gathered by
   * earlier stages.
   */
  interface Factory {
    SemgrexStat create(SemgrexStats.Context context, List<String> args);
  }
}
