package edu.stanford.nlp.semgraph.semgrex.stats;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.semgraph.semgrex.SemgrexUtils;

/**
 * Gathers the distinct values of one key into a named set, for a later stage to restrict on.
 *<br>
 * Script syntax:
 *<pre>
 * collect KEY as NAME
 *</pre>
 * This exists because some questions need two passes.  Asking which
 * relations a possessed noun takes part in cannot be one pattern: the
 * sentences where the noun is possessed are not all of the sentences
 * containing that noun.  So one stage collects the nouns and a later
 * stage counts over the whole corpus, restricted to them:
 *<pre>
 * pattern {}=word &lt;nmod:poss {}
 * collect word as possessed
 *
 * pattern {}=word &lt;=relation {}
 * count -restrict word=possessed word relation
 *</pre>
 * Note that the values collected are whatever SemgrexUtils.getKey
 * returns for the key, which for a plain node is its word form.  To
 * gather lemmas instead, bind one in the pattern and collect that:
 *<pre>
 * pattern {lemma:__#1%lemma} &lt;nmod:poss {}
 * collect lemma as possessed
 *</pre>
 *
 * @author John Bauer
 */
public class CollectStat implements SemgrexStat {
  private static final String AS = "as";

  private final String key;
  private final String name;
  private final Set<String> values;

  CollectStat(String key, String name, Set<String> values) {
    this.key = key;
    this.name = name;
    this.values = values;
  }

  /**
   * Parses "collect KEY as NAME"
   */
  public static SemgrexStat create(SemgrexStats.Context context, List<String> args) {
    if (args.size() != 3 || !AS.equals(args.get(1))) {
      throw new SemgrexParseException("collect takes exactly 'collect KEY " + AS + " NAME'");
    }
    String key = args.get(0);
    String name = args.get(2);

    context.validateKeys("collect", Collections.singletonList(key));
    Set<String> values = context.declareSet("collect", name);

    return new CollectStat(key, name, values);
  }

  @Override
  public String name() {
    return "collect";
  }

  @Override
  public List<String> getKeys() {
    return Collections.singletonList(key);
  }

  public String getName() {
    return name;
  }

  public Set<String> getValues() {
    return Collections.unmodifiableSet(values);
  }

  @Override
  public void accumulate(SemgrexMatch match) {
    String value = SemgrexUtils.getKey(match, key);
    // a key which was not bound in this match, eg under an optional
    // relation, is not a value anyone can restrict on later
    if (value != null) {
      values.add(value);
    }
  }

  @Override
  public String report() {
    return values.size() + " distinct values collected" + System.lineSeparator();
  }

  @Override
  public String toString() {
    return "collect " + key + " " + AS + " " + name;
  }
}
