package edu.stanford.nlp.semgraph.semgrex.stats;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;

/**
 * Gathers the distinct values of one key into a named set, for a later stage to restrict on.
 *<br>
 * Script syntax:
 *<pre>
 * collect [-lower KEY] KEY as NAME
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
 * {@code -lower KEY} collects the values lowercased, for a later
 * {@code count -lower KEY -restrict KEY=NAME} to match against.  It has
 * to name the key being collected.
 *<br>
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
  /** the key, if its values are lowercased, or else nothing */
  private final Set<String> lowered;

  CollectStat(String key, String name, Set<String> values, Set<String> lowered) {
    this.key = key;
    this.name = name;
    this.values = values;
    this.lowered = Collections.unmodifiableSet(new HashSet<>(lowered));
  }

  /**
   * Parses "collect [-lower KEY] KEY as NAME"
   *
   * @param context the stage being built; KEY is checked against its pattern and NAME is declared as a set in it
   * @param args the words after "collect": optionally -lower and KEY, then KEY, "as", NAME
   * @return a stat which adds the value of KEY in each match to the set NAME
   * @throws SemgrexParseException if args are not in that form, KEY is not a
   *     single name in the pattern, -lower names some other key, or NAME was already declared
   */
  public static SemgrexStat create(SemgrexStats.Context context, List<String> args) {
    Set<String> lowered = new HashSet<>();
    if (!args.isEmpty() && SemgrexStats.LOWER.equals(args.get(0))) {
      if (args.size() < 2) {
        throw new SemgrexParseException(SemgrexStats.LOWER + " needs a key after it");
      }
      lowered.add(args.get(1));
      args = args.subList(2, args.size());
    }
    if (args.size() != 3 || !AS.equals(args.get(1))) {
      throw new SemgrexParseException("collect takes exactly 'collect [" + SemgrexStats.LOWER + " KEY] KEY " + AS + " NAME'");
    }
    String key = args.get(0);
    String name = args.get(2);

    context.validateKeys("collect", Collections.singletonList(key));
    SemgrexStats.checkLowered("collect", lowered, Collections.singletonList(key));
    Set<String> values = context.declareSet("collect", name);

    return new CollectStat(key, name, values, lowered);
  }

  @Override
  public String name() {
    return "collect";
  }

  @Override
  public List<String> getKeys() {
    return Collections.singletonList(key);
  }

  /**
   * Returns the name of the set being collected into.
   *
   * @return the NAME in "collect KEY as NAME"
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the values collected so far.
   *
   * @return an unmodifiable view of the set, which reflects later additions
   */
  public Set<String> getValues() {
    return Collections.unmodifiableSet(values);
  }

  @Override
  public void accumulate(CoreMap sentence, SemgrexMatch match) {
    String value = SemgrexStats.keyValue(match, key, lowered);
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
    String lower = lowered.isEmpty() ? "" : SemgrexStats.LOWER + " " + key + " ";
    return "collect " + lower + key + " " + AS + " " + name;
  }
}
