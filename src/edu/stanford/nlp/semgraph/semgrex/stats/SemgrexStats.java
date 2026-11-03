package edu.stanford.nlp.semgraph.semgrex.stats;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.semgrex.RootPattern;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.semgraph.semgrex.SemgrexUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Runs a semgrex stats script over a corpus and prints the results.
 *<br>
 * Every line of a script is a command word and its arguments.  The
 * {@code pattern} command opens a stage; the commands after it are the
 * statistics gathered for that stage:
 *<pre>
 * # subjects, by the relation used and the part of speech of the dependent
 * pattern {}=gov &gt;/nsubj.*&#47;=edge {}=dep
 * count edge
 * count edge dep
 *</pre>
 * All of the commands in one stage accumulate during a single pass over
 * the corpus, so asking several questions at once costs no more than
 * asking one.  Blank lines and lines beginning with {@code #} are
 * ignored, which is why patterns need the {@code pattern} keyword:
 * {@code #} is also the semgrex token for an empty node, so a bare
 * pattern at the start of a line could not be told apart from a
 * comment.
 *<br>
 * The comment lines directly above a {@code pattern} line, with no blank
 * line between them, label its stage, and the report repeats them at
 * the top of the stage's results as {@code ## } lines.  In the same way,
 * the comment lines directly above a command label that command, and
 * are repeated above its results.  Other comments, such as those with a
 * blank line under them, are left out of the report.
 *<br>
 * Besides the names in its pattern, a command can use the key
 * {@code $corpus}, the corpus each sentence came from.  Each path given
 * on the command line is one corpus, named by its last component, so
 *<pre>
 * java edu.stanford.nlp.semgraph.semgrex.stats.SemgrexStats -script none.stats UD_English-EWT/ UD_English-GUM/
 *</pre>
 * with {@code count form pron $corpus} in the script gives one chart with
 * a column for each treebank, with 0 where one treebank has a value
 * the other has not.  {@code $} cannot be part of a semgrex name, so the
 * key never means anything in a pattern.
 *<br>
 * How the results are written is up to the run rather than the script:
 * {@code -format latex} writes each {@code count} chart as a LaTeX
 * {@code tabular}, and {@code -format latexFloat} as a {@code table}
 * float.  A chart's caption is the label of its command, or if it has
 * none, the label of its stage:
 *<pre>{@code
 * # Count of acl vs acl:relcl, showing a deficit in AnCora UD 2.17
 * pattern {} </acl|acl:relcl/=edge {}
 * count -sort edge -sort $corpus edge $corpus
 * }</pre>
 * A float with no label is captioned with its command, and an inline
 * tabular with no label has no caption.  The rest of the report becomes
 * {@code %} comments, so that the whole report can go into a document.  {@code -noTotal} leaves the totals out of every
 * chart, as {@code count -noTotal} does for one.
 *<br>
 * A script with more than one stage runs one pass over the corpus per
 * stage, in order.  A later stage can be restricted to values gathered
 * by an earlier one, which is a question a single pattern cannot ask,
 * since the sentences matching the first pattern are not all of the
 * sentences containing those words:
 *<pre>
 * pattern {}=word &lt;nmod:poss {}
 * collect word as possessed
 *
 * pattern {}=word &lt;=relation {}
 * count -restrict word=possessed word relation
 *</pre>
 *<br>
 * The command language is parsed here rather than in the semgrex
 * grammar.  A command word added to the grammar would become a reserved
 * token in the lexer -- {@code count}, {@code mean}, and {@code top} are
 * all words someone might reasonably want as a node name or a lemma --
 * and every new command would mean regenerating the parser.  This way
 * adding a command is a new class and one line in the registry.
 *
 * @author John Bauer
 */
public class SemgrexStats {
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemgrexStats.class);

  /** How a report is written */
  public enum Format {
    /** charts as columns of text, with # comments */
    TEXT,
    /**
     * charts as LaTeX tabulars, booktabs style, with % comments, and with
     * a caption below if the chart has a label, which needs the caption
     * or capt-of package
     */
    LATEX,
    /** charts as LaTeX tabulars in table floats, each captioned with its label or its command */
    LATEX_FLOAT;

    /**
     * The format named on the command line: text, latex, or latexFloat, in any case.
     *
     * @param name the name
     * @return the format
     * @throws IllegalArgumentException if the name is not a format
     */
    public static Format fromName(String name) {
      for (Format format : values()) {
        if (format.toString().replace("_", "").equalsIgnoreCase(name)) {
          return format;
        }
      }
      throw new IllegalArgumentException("Unknown format " + name + ".  Known formats: text, latex, latexFloat");
    }

    /** What starts a comment line in this format */
    String commentMarker() {
      return this == TEXT ? COMMENT : "%";
    }
  }

  /**
   * How a report is presented, as chosen for the whole run: its format,
   * and whether the charts have their totals.  A script can still leave
   * the totals out of one chart, with {@code count -noTotal}.
   */
  public static final class Presentation {
    /** Text, with the totals */
    public static final Presentation DEFAULT = new Presentation(Format.TEXT, true);

    private final Format format;
    private final boolean totals;

    /**
     * Creates a presentation.
     *
     * @param format how to write the report
     * @param totals whether the charts have their TOTAL rows and columns and their subtotals
     */
    public Presentation(Format format, boolean totals) {
      this.format = format;
      this.totals = totals;
    }

    /**
     * How to write the report.
     *
     * @return the format
     */
    public Format format() {
      return format;
    }

    /**
     * Whether the charts have their totals, unless a command leaves them out.
     *
     * @return whether to write the totals
     */
    public boolean totals() {
      return totals;
    }
  }

  /** Opens a new stage.  Handled by the script parser, not by the registry. */
  public static final String PATTERN_COMMAND = "pattern";

  /** The key whose value is the corpus a sentence came from */
  public static final String CORPUS_KEY = "$corpus";

  /**
   * The corpus a sentence came from, which a command reads as the key
   * {@link #CORPUS_KEY}.  {@link #main} sets it to the last component of
   * the path the sentence's file was found through.
   */
  public static class CorpusAnnotation implements CoreAnnotation<String> {
    /** Creates the key; the annotation has no state of its own. */
    public CorpusAnnotation() { }

    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The option which lowercases the values of one key, so that, say, "Lo"
   * and "lo" are counted together.  It changes only what the command
   * sees, not the match: a backreference or an Ssurgeon edit still uses
   * the value as it was written.
   */
  public static final String LOWER = "-lower";

  /** Comments and the semgrex empty node both start with this, which is why patterns are prefixed */
  private static final String COMMENT = "#";

  /**
   * Command word to the thing which builds it.  Add new commands here.
   */
  private static final Map<String, SemgrexStat.Factory> REGISTRY = buildRegistry();

  private static Map<String, SemgrexStat.Factory> buildRegistry() {
    Map<String, SemgrexStat.Factory> registry = new LinkedHashMap<>();
    registry.put("count", CountStat::create);
    registry.put("collect", CollectStat::create);
    registry.put("sample", SampleStat::create);
    return Collections.unmodifiableMap(registry);
  }

  /**
   * Returns the command words which may follow a {@code pattern} line.
   *
   * @return A new list of the registered command words, in registration order
   */
  public static List<String> knownCommands() {
    return new ArrayList<>(REGISTRY.keySet());
  }

  // ------------------------------------------------------------------
  // stages
  // ------------------------------------------------------------------

  /**
   * One pattern and the statistics gathered for it in a single pass over the corpus.
   */
  public static class Stage {
    private final SemgrexPattern pattern;
    private final List<SemgrexStat> stats;
    private final List<String> labels;
    private final List<List<String>> commandLabels;

    Stage(SemgrexPattern pattern, List<SemgrexStat> stats, List<String> labels, List<List<String>> commandLabels) {
      if (stats.size() != commandLabels.size()) {
        throw new IllegalArgumentException("Every command needs a label, empty if it has none");
      }
      this.pattern = pattern;
      this.stats = Collections.unmodifiableList(new ArrayList<>(stats));
      this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
      List<List<String>> copied = new ArrayList<>();
      for (List<String> label : commandLabels) {
        copied.add(Collections.unmodifiableList(new ArrayList<>(label)));
      }
      this.commandLabels = Collections.unmodifiableList(copied);
    }

    /**
     * Returns the label of this stage: the comment lines directly above its
     * {@code pattern} line, without their {@code #}.
     *
     * @return An unmodifiable list of the lines, empty if the stage has no label
     */
    public List<String> getLabels() {
      return labels;
    }

    /**
     * Returns the label of each command: the comment lines directly above
     * it, without their {@code #}.
     *
     * @return An unmodifiable list with a label for each of {@link #getStats()},
     *   in the same order, each empty if that command has no label
     */
    public List<List<String>> getCommandLabels() {
      return commandLabels;
    }

    /**
     * Returns the pattern matched in this stage.
     *
     * @return The pattern
     */
    public SemgrexPattern getPattern() {
      return pattern;
    }

    /**
     * Returns the statistics gathered in this stage.
     *
     * @return An unmodifiable list of the statistics, in script order
     */
    public List<SemgrexStat> getStats() {
      return stats;
    }

    void accumulate(List<CoreMap> sentences) {
      List<Pair<CoreMap, List<SemgrexMatch>>> matches = pattern.matchSentences(sentences, false);
      for (Pair<CoreMap, List<SemgrexMatch>> sentence : matches) {
        for (SemgrexMatch match : sentence.second()) {
          for (SemgrexStat stat : stats) {
            stat.accumulate(sentence.first(), match);
          }
        }
      }
    }

    String report(Presentation presentation) {
      String marker = presentation.format().commentMarker();
      StringBuilder sb = new StringBuilder();
      appendLabel(sb, marker, labels);
      for (int idx = 0; idx < stats.size(); ++idx) {
        if (idx > 0) {
          sb.append(System.lineSeparator());
        }
        SemgrexStat stat = stats.get(idx);
        List<String> commandLabel = commandLabels.get(idx);
        appendLabel(sb, marker, commandLabel);
        sb.append(marker).append(" ").append(stat.toString()).append(System.lineSeparator());
        sb.append(stat.report(presentation, commandLabel.isEmpty() ? labels : commandLabel));
      }
      return sb.toString();
    }

    /** The lines of a label, as doubled comment markers */
    private static void appendLabel(StringBuilder sb, String marker, List<String> label) {
      for (String line : label) {
        sb.append(marker).append(marker);
        if (!line.isEmpty()) {
          sb.append(" ").append(line);
        }
        sb.append(System.lineSeparator());
      }
    }
  }

  /**
   * What a command can see while it is being built: the pattern of the
   * stage it belongs to, and the sets gathered by earlier stages.
   *<br>
   * The sets are shared, mutable, and filled in as the stages run.  A
   * command which reads one holds the same Set object the command which
   * fills it holds, so by the time a later stage accumulates, an earlier
   * stage has already finished writing to it.
   */
  public static class Context {
    private final SemgrexPattern pattern;
    private final int stage;
    private final Map<String, Set<String>> sets;
    private final Map<String, Integer> setStages;
    private final boolean bidiAllowed;

    Context(SemgrexPattern pattern, int stage, Map<String, Set<String>> sets, Map<String, Integer> setStages,
            boolean bidiAllowed) {
      this.pattern = pattern;
      this.stage = stage;
      this.sets = sets;
      this.setStages = setStages;
      this.bidiAllowed = bidiAllowed;
    }

    /**
     * Whether a command may use Unicode bidi controls in its output.
     *<br>
     * This is a run level setting rather than a property of the script,
     * since whether the controls help depends on what is displaying the
     * output rather than on what is being counted.  A command may still
     * override it for itself.
     *
     * @return Whether bidi controls are allowed
     */
    public boolean bidiAllowed() {
      return bidiAllowed;
    }

    /**
     * Returns the pattern of the stage the command belongs to.
     *
     * @return The pattern
     */
    public SemgrexPattern getPattern() {
      return pattern;
    }

    /**
     * Checks that keys name something in this stage's pattern.  See validateKeys.
     *
     * @param command The command being built, used in error messages
     * @param keys The names to check
     */
    public void validateKeys(String command, List<String> keys) {
      SemgrexStats.validateKeys(pattern, command, keys);
    }

    /**
     * Declares a set for a command in this stage to fill.
     *
     * @param command The command being built, used in error messages
     * @param name The name of the set
     * @return The new, empty set, which the caller fills
     * @throws SemgrexParseException if a set with this name was already declared
     */
    public Set<String> declareSet(String command, String name) {
      if (sets.containsKey(name)) {
        throw new SemgrexParseException(command + " tried to collect into '" + name +
                                        "', which was already collected in stage " + (setStages.get(name) + 1));
      }
      Set<String> values = new LinkedHashSet<>();
      sets.put(name, values);
      setStages.put(name, stage);
      return values;
    }

    /**
     * Looks up a set gathered by an earlier stage.
     *<br>
     * It has to be an earlier one: a set collected in this same stage is
     * still being filled while this stage runs, so restricting on it
     * would depend on the order the sentences happened to arrive in.
     *
     * @param command The command being built, used in error messages
     * @param name The name of the set
     * @return The shared set, which may still be empty when this is called
     * @throws SemgrexParseException if the set was never declared or is declared in this or a later stage
     */
    public Set<String> useSet(String command, String name) {
      Integer collected = setStages.get(name);
      if (collected == null) {
        throw new SemgrexParseException(command + " asked for the set '" + name + "', which was never collected." +
                                        (sets.isEmpty() ? "" : "  Collected sets: " + new TreeSet<>(sets.keySet())));
      }
      if (collected >= stage) {
        throw new SemgrexParseException(command + " asked for the set '" + name + "', which is collected in the same " +
                                        "stage.  A set can only be used by a stage after the one which collects it");
      }
      return sets.get(name);
    }
  }

  private final List<Stage> stages;

  /**
   * Creates a runner for the given stages.
   *
   * @param stages The stages, in the order they are run; copied
   */
  public SemgrexStats(List<Stage> stages) {
    this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
  }

  /**
   * Returns the stages of the script.
   *
   * @return An unmodifiable list of the stages, in order
   */
  public List<Stage> getStages() {
    return stages;
  }

  /**
   * Returns the number of stages, which is the number of passes over the corpus.
   *
   * @return The number of stages
   */
  public int numStages() {
    return stages.size();
  }

  // ------------------------------------------------------------------
  // parsing
  // ------------------------------------------------------------------

  /**
   * Parses a script, with bidi controls allowed.
   *
   * @param script The text of the script
   * @return The parsed script
   * @throws SemgrexParseException if the script is empty or malformed
   */
  public static SemgrexStats parse(String script) {
    return parse(script, true);
  }

  /**
   * Parses a script, splitting it into lines.
   *
   * @param script The text of the script
   * @param bidiAllowed Whether commands may add Unicode bidi controls to their output
   * @return The parsed script
   * @throws SemgrexParseException if the script is empty or malformed
   */
  public static SemgrexStats parse(String script, boolean bidiAllowed) {
    return parse(Arrays.asList(script.split("\n")), bidiAllowed);
  }

  /**
   * Parses the lines of a script, with bidi controls allowed.
   *
   * @param lines The lines of the script
   * @return The parsed script
   * @throws SemgrexParseException if the script is empty or malformed
   */
  public static SemgrexStats parse(List<String> lines) {
    return parse(lines, true);
  }

  /**
   * Parses the lines of a script.  Blank lines and comments are skipped;
   * each {@code pattern} line starts a new stage, labelled by the comment
   * lines directly above it, and each command is labelled the same way.
   *
   * @param lines The lines of the script
   * @param bidiAllowed Whether commands may add Unicode bidi controls to their output
   * @return The parsed script
   * @throws SemgrexParseException if the script is empty, has a command before
   *   the first pattern, has a stage with no commands, or has a bad pattern or command
   */
  public static SemgrexStats parse(List<String> lines, boolean bidiAllowed) {
    // each command line, with the comment lines directly above it
    List<Pair<String, List<String>>> meaningful = new ArrayList<>();
    List<String> comments = new ArrayList<>();
    for (String line : lines) {
      // \r so that a script written on Windows doesn't produce a
      // pattern with an invisible character glued to the end of it
      String trimmed = line.replaceAll("\r$", "").trim();
      if (trimmed.isEmpty()) {
        comments = new ArrayList<>();
      } else if (trimmed.startsWith(COMMENT)) {
        comments.add(trimmed.substring(COMMENT.length()).trim());
      } else {
        meaningful.add(new Pair<>(trimmed, comments));
        comments = new ArrayList<>();
      }
    }

    if (meaningful.isEmpty()) {
      throw new SemgrexParseException("Script was empty: expected a '" + PATTERN_COMMAND + "' line");
    }

    Map<String, Set<String>> sets = new HashMap<>();
    Map<String, Integer> setStages = new HashMap<>();

    List<Stage> stages = new ArrayList<>();
    Context context = null;
    List<SemgrexStat> stats = new ArrayList<>();
    List<List<String>> commandLabels = new ArrayList<>();
    List<String> labels = Collections.emptyList();

    for (Pair<String, List<String>> meaningfulLine : meaningful) {
      String line = meaningfulLine.first();
      Pair<String, String> split = splitCommand(line);
      String command = split.first();
      String rest = split.second();

      if (PATTERN_COMMAND.equals(command)) {
        if (context != null) {
          stages.add(finishStage(context, stats, labels, commandLabels));
          stats = new ArrayList<>();
          commandLabels = new ArrayList<>();
        }
        labels = meaningfulLine.second();
        if (rest.isEmpty()) {
          throw new SemgrexParseException("A '" + PATTERN_COMMAND + "' line needs a semgrex pattern after it");
        }
        // note that we compile a single line, never the whole script.
        // SemgrexPattern.Root() stops at its newline without checking
        // for EOF, so handing it more than one line would parse the
        // first and discard the rest without complaining
        context = new Context(SemgrexPattern.compile(rest), stages.size(), sets, setStages, bidiAllowed);
        continue;
      }

      if (context == null) {
        throw new SemgrexParseException("Found the command '" + command + "' before any '" + PATTERN_COMMAND + "' line");
      }
      stats.add(parseCommand(context, command, rest));
      commandLabels.add(meaningfulLine.second());
    }

    stages.add(finishStage(context, stats, labels, commandLabels));
    return new SemgrexStats(stages);
  }

  private static Stage finishStage(Context context, List<SemgrexStat> stats, List<String> labels,
                                   List<List<String>> commandLabels) {
    if (stats.isEmpty()) {
      throw new SemgrexParseException("A '" + PATTERN_COMMAND + "' line had no statistics commands after it.  " +
                                      "Known commands: " + knownCommands());
    }
    return new Stage(context.getPattern(), stats, labels, commandLabels);
  }

  /**
   * Splits a line into its command word and everything after it.
   *<br>
   * The remainder is kept verbatim, since for a pattern line it is the
   * pattern and its internal spacing matters.
   */
  static Pair<String, String> splitCommand(String line) {
    String[] pieces = line.split("\\s+", 2);
    return new Pair<>(pieces[0], pieces.length > 1 ? pieces[1].trim() : "");
  }

  /**
   * Builds one statistics command from its command word and arguments.
   *
   * @param context The stage the command belongs to
   * @param command The command word
   * @param rest The rest of the line, which is split on whitespace into arguments
   * @return The new statistic
   * @throws SemgrexParseException if the command word is unknown or the arguments are invalid
   */
  public static SemgrexStat parseCommand(Context context, String command, String rest) {
    SemgrexStat.Factory factory = REGISTRY.get(command);
    if (factory == null) {
      throw new SemgrexParseException("Unknown statistics command '" + command + "'.  Known commands: " + knownCommands() +
                                      ", and '" + PATTERN_COMMAND + "' to start a new stage");
    }
    List<String> args = rest.isEmpty() ? Collections.emptyList() : Arrays.asList(rest.split("\\s+"));
    return factory.create(context, args);
  }

  /**
   * The value of a key in a match, as a command sees it: lowercased if
   * the command named the key with {@link #LOWER}.  Lowercasing uses
   * {@code Locale.ROOT}, so the result does not depend on where the
   * script is run.
   *
   * @param sentence the sentence of the match, which knows its corpus
   * @param match the match
   * @param key the name of a node, edge, or regex group in the pattern, or {@link #CORPUS_KEY}
   * @param lowered the keys the command lowercases
   * @return the value, or null if the key was not bound in this match, or
   *   the key is {@link #CORPUS_KEY} and the sentence has no corpus
   */
  public static String keyValue(CoreMap sentence, SemgrexMatch match, String key, Set<String> lowered) {
    String value = CORPUS_KEY.equals(key) ? sentence.get(CorpusAnnotation.class) : SemgrexUtils.getKey(match, key);
    if (value != null && lowered.contains(key)) {
      value = value.toLowerCase(Locale.ROOT);
    }
    return value;
  }

  /**
   * Checks that each key a command lowercases is one the command uses,
   * so that a typo is a parse error rather than an option which does nothing.
   *
   * @param command the command being built, used in error messages
   * @param lowered the keys given with {@link #LOWER}
   * @param used the keys the command reads
   * @throws SemgrexParseException if a lowered key is not one the command reads
   */
  public static void checkLowered(String command, Set<String> lowered, Collection<String> used) {
    checkOptionKeys(command, LOWER, lowered, used);
  }

  /**
   * Checks that each key an option of a command names is one the command
   * uses, so that a typo is a parse error rather than an option which
   * does nothing.
   *
   * @param command the command being built, used in error messages
   * @param option the option, such as {@code -sort}
   * @param named the keys given with the option
   * @param used the keys the command reads
   * @throws SemgrexParseException if a named key is not one the command reads
   */
  public static void checkOptionKeys(String command, String option, Collection<String> named, Collection<String> used) {
    for (String key : named) {
      if (!used.contains(key)) {
        throw new SemgrexParseException(command + " " + option + " " + key + " names a key which " + command + " does not use");
      }
    }
  }

  /**
   * Checks that every key a command was given actually names something in the pattern.
   *<br>
   * Any command which takes keys should call this from its factory, so
   * that a typo is a parse error rather than a chart in which every row
   * is unbound.  This mirrors the check the grammar already makes for
   * the ::uniq and ::sort keys.
   *<br>
   * TODO: the parser does not track relation names (the ~name form), so
   * a key which names a relation is rejected here as unknown.  That is
   * only reachable on the transitive relations, since GraphRelation
   * refuses =name on those and =name sets the relation name anyway on
   * the others.
   *
   * @param pattern The compiled pattern, which must be a RootPattern
   * @param command The command being built, used in error messages
   * @param keys The names to check
   * @throws IllegalArgumentException if {@code pattern} is not a RootPattern
   * {@link #CORPUS_KEY} is not in the pattern, and is always allowed.
   *
   * @throws SemgrexParseException if a key is unknown or ambiguous
   */
  public static void validateKeys(SemgrexPattern pattern, String command, List<String> keys) {
    if (!(pattern instanceof RootPattern)) {
      throw new IllegalArgumentException("Can only validate keys against a compiled pattern, not a " +
                                         pattern.getClass().getSimpleName());
    }
    RootPattern root = (RootPattern) pattern;
    Set<String> variables = root.getKnownVariables();
    Set<String> varGroups = root.getKnownVarGroups();
    Set<String> edges = root.getKnownEdges();

    for (String key : keys) {
      if (CORPUS_KEY.equals(key)) {
        continue;
      }
      int found = 0;
      if (variables.contains(key)) {
        ++found;
      }
      if (varGroups.contains(key)) {
        ++found;
      }
      if (edges.contains(key)) {
        ++found;
      }

      if (found == 0) {
        TreeSet<String> available = new TreeSet<>();
        available.addAll(variables);
        available.addAll(varGroups);
        available.addAll(edges);
        throw new SemgrexParseException(command + " was asked for '" + key + "', which does not exist in the pattern " +
                                        "(as a node, regex, or edge).  Available names: " + available + ", and " + CORPUS_KEY);
      }
      if (found > 1) {
        throw new SemgrexParseException(command + " was asked for '" + key + "', which is very confusing, as it is " +
                                        "ambiguous between node, regex, and edge.  Please rename one of them");
      }
    }
  }

  // ------------------------------------------------------------------
  // running
  // ------------------------------------------------------------------

  /**
   * Marks sentences as coming from a corpus, which a command reads as {@link #CORPUS_KEY}.
   *
   * @param sentences the sentences
   * @param corpus the name of their corpus
   */
  public static void setCorpus(List<CoreMap> sentences, String corpus) {
    for (CoreMap sentence : sentences) {
      sentence.set(CorpusAnnotation.class, corpus);
    }
  }

  /**
   * Feed a batch of sentences to one stage.
   *<br>
   * May be called more than once for a stage, so that a corpus can be
   * processed a file at a time rather than held in memory all at once.
   * Stages must be run in order, since a later one may read a set an
   * earlier one is still filling.
   *
   * @param stage The index of the stage, counting from 0
   * @param sentences The sentences to match, each with a dependency graph
   */
  public void accumulate(int stage, List<CoreMap> sentences) {
    stages.get(stage).accumulate(sentences);
  }

  /**
   * Formats the results of every stage as text.  When there is more than
   * one stage, each is headed by a {@code ## stage N} line.  A stage with a
   * label has it next, as {@code ## } lines.
   *
   * @return The report text
   */
  public String report() {
    return report(Format.TEXT);
  }

  /**
   * Formats the results of every stage, as {@link #report()} does, in the
   * given format.  In LaTeX, the comment lines start with {@code %} rather
   * than {@code #}, so the whole report can go into a document.
   *
   * @param format how to write the report
   * @return The report text
   */
  public String report(Format format) {
    return report(new Presentation(format, true));
  }

  /**
   * Formats the results of every stage, as {@link #report(Format)} does,
   * with the charts' totals left out if the presentation says so.
   *
   * @param presentation how to write the report
   * @return The report text
   */
  public String report(Presentation presentation) {
    String marker = presentation.format().commentMarker();
    StringBuilder sb = new StringBuilder();
    for (int idx = 0; idx < stages.size(); ++idx) {
      if (sb.length() > 0) {
        sb.append(System.lineSeparator());
      }
      if (stages.size() > 1) {
        sb.append(marker).append(marker).append(" stage ").append(idx + 1).append(System.lineSeparator());
      }
      sb.append(stages.get(idx).report(presentation));
    }
    return sb.toString();
  }

  // ------------------------------------------------------------------
  // command line
  // ------------------------------------------------------------------

  private static final String SCRIPT = "-script";
  private static final String TREE_FILE = "-treeFile";
  private static final String CONLLU_FILE = "-conlluFile";
  private static final String MODE = "-mode";
  private static final String DEFAULT_MODE = "BASIC";
  private static final String EXTRAS = "-extras";
  private static final String CONLLU_EXTENSION = ".conllu";
  private static final String CONLLU_EXTENSION_FLAG = "-conlluExtension";
  private static final String NO_BIDI = "-noBidi";
  private static final String FORMAT = "-format";
  private static final String NO_TOTAL = "-noTotal";
  private static final String CORPUS_FLAG = "-corpus";

  /**
   * Logs a description of the command line arguments and the script format.
   */
  public static void help() {
    log.info("Possible arguments for SemgrexStats:");
    log.info(SCRIPT + ": a file containing a stats script");
    log.info(CONLLU_FILE + ": CoNLL-U dependency trees to process.  May be a file, a directory, "
                         + "several of either separated by , or ; or the flag repeated.  Bare paths "
                         + "with no flag in front of them are read the same way, so a list of files "
                         + "can simply be separated by spaces.  Each path is one corpus, named by "
                         + "its last component, which a command can use as the key " + CORPUS_KEY);
    log.info(CORPUS_FLAG + " NEW=OLD: call the corpus OLD by the name NEW instead, where OLD is the name "
                         + "it would have, such as UD_Spanish-AnCora, or the path it is read from.  "
                         + "May be repeated.  Giving two corpora the same name counts them as one");
    log.info(CONLLU_EXTENSION_FLAG + ": which extensions to pick up when a directory is given, "
                         + "separated by , or ; .  Default " + CONLLU_EXTENSION + ".  A file named "
                         + "explicitly is always read whatever it is called");
    log.info(TREE_FILE + ": a file of trees to process");
    log.info(MODE + ": what mode for dependencies.  basic, collapsed, or ccprocessed.  To get 'noncollapsed', use basic with extras");
    log.info(EXTRAS + ": whether or not to use extras");
    log.info(NO_BIDI + ": never add the Unicode bidi controls which keep a right to left "
                     + "script from reversing a chart's columns.  They are added automatically "
                     + "when the data needs them, which is right for a browser or a chat client "
                     + "but not for a terminal which mangles them");
    log.info(FORMAT + ": how to write the charts: text, the default; latex, as LaTeX tabulars with the rules "
                    + "of the booktabs package; or latexFloat, as tabulars in table floats.  A chart's caption "
                    + "is the comment directly above its command, or else the one directly above its pattern.  "
                    + "An inline caption needs the caption or capt-of package.  In LaTeX, the rest of the "
                    + "report is % comments");
    log.info(NO_TOTAL + ": leave the TOTAL rows and columns, and the subtotals, out of every chart, "
                    + "as count -noTotal does for one chart");
    log.info();
    log.info(SCRIPT + " is required, as is one of " + CONLLU_FILE + " or " + TREE_FILE);
    log.info();
    log.info("A script is one command per line, with '" + PATTERN_COMMAND + "' starting a stage:");
    log.info("  " + PATTERN_COMMAND + " {}=gov >nsubj=edge {}=dep");
    log.info("  count edge");
    log.info("  count edge gov");
    log.info();
    log.info("A second stage can be restricted to values gathered by the first,");
    log.info("at the cost of one more pass over the corpus:");
    log.info("  " + PATTERN_COMMAND + " {}=word <nmod:poss {}");
    log.info("  collect word as possessed");
    log.info("  " + PATTERN_COMMAND + " {}=word <=relation {}");
    log.info("  count -restrict word=possessed word relation");
    log.info();
    log.info("Known commands: " + knownCommands());
  }

  /**
   * Turns the -conlluFile arguments into the ordered list of files to read.
   *<br>
   * Each argument may be a filename or a directory, and may itself be
   * several of those separated by , or ; ... the flag can also be
   * repeated, since StringUtils.argsToMap appends repeats of a flag it
   * knows the arity of.
   *<br>
   * A directory contributes the files directly inside it whose name ends
   * in one of the extensions, sorted, since File.listFiles is in no
   * particular order and a UD treebank directory also holds a README and
   * a LICENSE.  A file named explicitly is read whatever it is called.
   *<br>
   * Anything in a directory which is passed over is reported, since a
   * file which should have been counted and wasn't is a chart which is
   * quietly wrong rather than obviously broken.
   */
  static List<File> expandConlluFiles(List<String> paths, List<String> extensions) {
    List<File> files = new ArrayList<>();
    for (Pair<File, String> file : expandCorpora(paths, extensions)) {
      files.add(file.first());
    }
    return files;
  }

  /**
   * As {@link #expandConlluFiles}, with the corpus of each file: the last
   * component of the path it was found through, such as
   * {@code UD_English-EWT} for every file in {@code UD_English-EWT/}.
   *
   * @param paths the -conlluFile arguments
   * @param extensions the extensions of the files to read in a directory
   * @return each file, in the order to read them, with its corpus
   * @throws IllegalArgumentException if a path is neither a file nor a directory,
   *   a directory has no files to read, or there are no files at all
   */
  static List<Pair<File, String>> expandCorpora(List<String> paths, List<String> extensions) {
    List<String> split = splitPaths(paths);
    return expandNamedCorpora(split, nameCorpora(split, Collections.emptyList()), extensions);
  }

  /**
   * As {@link #expandCorpora(List, List)}, for paths which are already
   * split, each of them one corpus, with the name of each corpus given.
   *
   * @param paths the paths, one file or directory each
   * @param corpora the name of the corpus of each path
   * @param extensions the extensions of the files to read in a directory
   * @return each file, in the order to read them, with its corpus
   * @throws IllegalArgumentException if a path is neither a file nor a directory,
   *   a directory has no files to read, or there are no files at all
   */
  static List<Pair<File, String>> expandNamedCorpora(List<String> paths, List<String> corpora, List<String> extensions) {
    List<Pair<File, String>> files = new ArrayList<>();
    for (int idx = 0; idx < paths.size(); ++idx) {
      String path = paths.get(idx);
      File file = new File(path);
      String corpus = corpora.get(idx);
      if (file.isDirectory()) {
        File[] contents = file.listFiles();
        if (contents == null) {
          throw new IllegalArgumentException("Could not read the directory " + path);
        }
        List<File> found = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (File child : contents) {
          if (!child.isFile()) {
            continue;
          }
          if (matchesExtension(child.getName(), extensions)) {
            found.add(child);
          } else {
            skipped.add(child.getName());
          }
        }
        if (found.isEmpty()) {
          throw new IllegalArgumentException("Found no " + extensions + " files in the directory " + path);
        }
        Collections.sort(found);
        if (!skipped.isEmpty()) {
          Collections.sort(skipped);
          log.warn("Skipped " + skipped.size() + " file(s) in " + path + " which do not end in " +
                   extensions + ": " + skipped + "  (use " + CONLLU_EXTENSION_FLAG + " to include them)");
        }
        for (File child : found) {
          files.add(new Pair<>(child, corpus));
        }
      } else if (file.isFile()) {
        files.add(new Pair<>(file, corpus));
      } else {
        throw new IllegalArgumentException("Not a file or a directory: " + path);
      }
    }
    if (files.isEmpty()) {
      throw new IllegalArgumentException("No files to read");
    }
    return files;
  }

  /**
   * The name of the corpus read through a path: its last component, or
   * the path as given if it has none, such as {@code .}
   */
  static String corpusName(File path) {
    String name = path.getName();
    if (name.isEmpty() || name.equals(".") || name.equals("..")) {
      File absolute = path.getAbsoluteFile().toPath().normalize().toFile();
      name = absolute.getName();
    }
    return name.isEmpty() ? path.getPath() : name;
  }

  /** The paths in the -conlluFile arguments, each of which may be several separated by , or ; */
  private static List<String> splitPaths(List<String> args) {
    List<String> paths = new ArrayList<>();
    for (String arg : args) {
      for (String path : arg.split("[,;]")) {
        path = path.trim();
        if (!path.isEmpty()) {
          paths.add(path);
        }
      }
    }
    return paths;
  }

  /**
   * The name of the corpus read through each path: the last component of
   * the path, unless a -corpus argument gives it another.  A -corpus
   * argument NEW=OLD names a path NEW when OLD is the path's own corpus
   * name, or when OLD is a path to the same file or directory, which
   * tells apart two corpora with the same last component.
   *
   * @param paths the paths, each of them one corpus
   * @param names the -corpus arguments, each as its new name and the corpus it names
   * @return the name of each path's corpus, in the same order
   * @throws IllegalArgumentException if a -corpus argument names none of the
   *   corpora, or two give one corpus different names
   */
  static List<String> nameCorpora(List<String> paths, List<Pair<String, String>> names) {
    List<String> defaults = new ArrayList<>();
    List<String> corpora = new ArrayList<>();
    boolean[] used = new boolean[names.size()];
    for (String path : paths) {
      File file = new File(path);
      String corpus = corpusName(file);
      defaults.add(corpus);
      String renamed = null;
      for (int i = 0; i < names.size(); ++i) {
        if (!namesCorpus(names.get(i).second(), file, corpus)) {
          continue;
        }
        String name = names.get(i).first();
        if (renamed != null && !renamed.equals(name)) {
          throw new IllegalArgumentException(CORPUS_FLAG + " gives " + path + " two names, " + renamed + " and " + name);
        }
        renamed = name;
        used[i] = true;
      }
      corpora.add(renamed == null ? corpus : renamed);
    }
    for (int i = 0; i < names.size(); ++i) {
      if (!used[i]) {
        throw new IllegalArgumentException(CORPUS_FLAG + " " + names.get(i).first() + "=" + names.get(i).second() +
                                           ": " + names.get(i).second() + " is not one of the corpora read, " + defaults +
                                           ".  The path has to be given as well, as " + CONLLU_FILE + " or a bare path");
      }
    }
    return corpora;
  }

  /**
   * Whether the OLD of a -corpus argument means the corpus read through
   * this file: the same file or directory, if OLD is a path which exists,
   * and otherwise the same corpus name
   */
  private static boolean namesCorpus(String old, File file, String corpus) {
    File oldFile = new File(old);
    if (oldFile.exists() && file.exists()) {
      try {
        return oldFile.getCanonicalFile().equals(file.getCanonicalFile());
      } catch (IOException e) {
        // fall back on the name
      }
    }
    return corpusName(oldFile).equals(corpus);
  }

  /**
   * Splits a -corpus argument, NEW=OLD, at its first =, so OLD, which may
   * be a path, can have an = of its own but NEW cannot.
   *
   * @param arg the argument
   * @return the new name and the corpus it names
   * @throws IllegalArgumentException if the argument has no =, or nothing on one side of it
   */
  static Pair<String, String> parseCorpusArg(String arg) {
    int equals = arg.indexOf('=');
    if (equals < 0) {
      throw new IllegalArgumentException(CORPUS_FLAG + " takes NEW=OLD, such as AnCora=UD_Spanish-AnCora, not " + arg);
    }
    String name = arg.substring(0, equals).trim();
    String old = arg.substring(equals + 1).trim();
    if (name.isEmpty() || old.isEmpty()) {
      throw new IllegalArgumentException(CORPUS_FLAG + " needs both a new name and a corpus: " + arg);
    }
    return new Pair<>(name, old);
  }

  private static boolean matchesExtension(String filename, List<String> extensions) {
    // ROOT so that a filename is cased the same way an extension was.
    // in a Turkish locale the default would send "I" to a dotless
    // lowercase and the two sides would stop agreeing
    String lower = filename.toLowerCase(Locale.ROOT);
    for (String extension : extensions) {
      if (lower.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Which extensions to pick up when a directory is given
   */
  static List<String> parseExtensions(String arg) {
    List<String> extensions = new ArrayList<>();
    for (String extension : arg.split("[,;]")) {
      extension = extension.trim().toLowerCase(Locale.ROOT);
      if (extension.isEmpty()) {
        continue;
      }
      if (!extension.startsWith(".")) {
        extension = "." + extension;
      }
      extensions.add(extension);
    }
    if (extensions.isEmpty()) {
      throw new IllegalArgumentException(CONLLU_EXTENSION_FLAG + " was given no extensions");
    }
    return extensions;
  }

  /**
   * Runs a stats script over a corpus and prints the report to stdout.
   * See {@link #help()} for the arguments.  Exits with status 2 if a
   * required argument is missing, the script cannot be read, or a
   * CoNLL-U path is invalid.
   *
   * @param args Command line arguments
   * @throws IOException if a CoNLL-U file cannot be read
   */
  public static void main(String[] args) throws IOException {
    Map<String, Integer> flagMap = Generics.newHashMap();
    flagMap.put(SCRIPT, 1);
    flagMap.put(TREE_FILE, 1);
    flagMap.put(CONLLU_FILE, 1);
    flagMap.put(MODE, 1);
    flagMap.put(EXTRAS, 1);
    flagMap.put(NO_BIDI, 0);
    flagMap.put(FORMAT, 1);
    flagMap.put(NO_TOTAL, 0);
    flagMap.put(CONLLU_EXTENSION_FLAG, 1);
    flagMap.put(CORPUS_FLAG, 1);

    Map<String, String[]> argsMap = StringUtils.argsToMap(args, flagMap);

    if (!argsMap.containsKey(SCRIPT) || argsMap.get(SCRIPT).length == 0) {
      help();
      System.exit(2);
    }

    String scriptFile = argsMap.get(SCRIPT)[0];
    String script;
    try {
      script = IOUtils.slurpFile(scriptFile);
    } catch (IOException e) {
      log.err("Could not read script file " + scriptFile + ": " + e.getMessage());
      System.exit(2);
      return;
    }
    SemgrexStats stats = SemgrexStats.parse(script, !argsMap.containsKey(NO_BIDI));
    Format format = Format.TEXT;
    if (argsMap.containsKey(FORMAT) && argsMap.get(FORMAT).length > 0) {
      try {
        format = Format.fromName(argsMap.get(FORMAT)[0]);
      } catch (IllegalArgumentException e) {
        log.err(e.getMessage());
        System.exit(2);
      }
    }

    String modeString = DEFAULT_MODE;
    if (argsMap.containsKey(MODE) && argsMap.get(MODE).length > 0) {
      // ROOT, since this is matched against the names of enum constants
      // rather than against text.  in a Turkish locale the default would
      // uppercase the i of "basic" to a dotted capital I, and the valueOf
      // below would then fail
      modeString = argsMap.get(MODE)[0].toUpperCase(Locale.ROOT);
    }
    SemanticGraphFactory.Mode mode = SemanticGraphFactory.Mode.valueOf(modeString);

    boolean useExtras = true;
    if (argsMap.containsKey(EXTRAS) && argsMap.get(EXTRAS).length > 0) {
      useExtras = Boolean.parseBoolean(argsMap.get(EXTRAS)[0]);
    }

    // StringUtils.argsToMap cannot give a flag a variable number of
    // arguments, since it takes one count and uses it as both the minimum
    // and the maximum, so -conlluFile a b c would eat the next flag.  it
    // does collect everything not attached to a flag under the null key
    // though, which makes bare paths a space separated list for free
    List<String> conlluPaths = new ArrayList<>();
    if (argsMap.containsKey(CONLLU_FILE)) {
      conlluPaths.addAll(Arrays.asList(argsMap.get(CONLLU_FILE)));
    }
    String[] positional = argsMap.get(null);
    if (positional != null) {
      conlluPaths.addAll(Arrays.asList(positional));
    }

    if (!argsMap.containsKey(TREE_FILE) && conlluPaths.isEmpty()) {
      help();
      System.exit(2);
    }

    List<Pair<String, String>> corpusNames = new ArrayList<>();
    List<String> treeFiles = argsMap.containsKey(TREE_FILE) ? Arrays.asList(argsMap.get(TREE_FILE)) : Collections.emptyList();
    List<String> conlluSplit = splitPaths(conlluPaths);
    List<String> treeCorpora;
    List<String> conlluCorpora;
    try {
      if (argsMap.containsKey(CORPUS_FLAG)) {
        for (String arg : argsMap.get(CORPUS_FLAG)) {
          corpusNames.add(parseCorpusArg(arg));
        }
      }
      // every -corpus has to name something, so check them against the
      // tree files and the CoNLL-U paths together
      List<String> allPaths = new ArrayList<>(treeFiles);
      allPaths.addAll(conlluSplit);
      List<String> allCorpora = nameCorpora(allPaths, corpusNames);
      treeCorpora = allCorpora.subList(0, treeFiles.size());
      conlluCorpora = allCorpora.subList(treeFiles.size(), allCorpora.size());
    } catch (IllegalArgumentException e) {
      log.err(e.getMessage());
      System.exit(2);
      return;
    }

    List<Pair<File, String>> conlluFiles = Collections.emptyList();
    if (!conlluPaths.isEmpty()) {
      // expand the whole list before reading anything, so that a typo
      // in the last filename is reported now rather than after an hour
      // of counting
      try {
        List<String> extensions = Collections.singletonList(CONLLU_EXTENSION);
        if (argsMap.containsKey(CONLLU_EXTENSION_FLAG) && argsMap.get(CONLLU_EXTENSION_FLAG).length > 0) {
          extensions = parseExtensions(argsMap.get(CONLLU_EXTENSION_FLAG)[0]);
        }
        conlluFiles = expandNamedCorpora(conlluSplit, conlluCorpora, extensions);
      } catch (IllegalArgumentException e) {
        log.err(e.getMessage());
        System.exit(2);
        return;
      }
    }

    // one pass over the corpus per stage.  the files are re-read rather
    // than kept, since a script with two stages should not double the
    // memory needed for a large treebank
    for (int stage = 0; stage < stats.numStages(); ++stage) {
      if (stats.numStages() > 1) {
        log.info("Stage " + (stage + 1) + " of " + stats.numStages());
      }

      for (int idx = 0; idx < treeFiles.size(); ++idx) {
        String treeFile = treeFiles.get(idx);
        log.info("Loading file " + treeFile);
        List<CoreMap> sentences = SemgrexUtils.readTreeFile(treeFile, mode, useExtras);
        setCorpus(sentences, treeCorpora.get(idx));
        stats.accumulate(stage, sentences);
      }

      if (!conlluFiles.isEmpty()) {
        CoNLLUReader reader = new CoNLLUReader();
        for (Pair<File, String> conlluFile : conlluFiles) {
          log.info("Loading file " + conlluFile.first());
          List<CoreMap> sentences = new ArrayList<>();
          for (Annotation doc : reader.readCoNLLUFile(conlluFile.first().toString())) {
            sentences.addAll(doc.get(CoreAnnotations.SentencesAnnotation.class));
          }
          setCorpus(sentences, conlluFile.second());
          stats.accumulate(stage, sentences);
        }
      }
    }

    // UTF-8 whatever the locale, as the treebanks are, rather than letting
    // a terminal in the C locale turn every accented letter into ?
    PrintStream stdout = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "utf-8");
    stdout.print(stats.report(new Presentation(format, !argsMap.containsKey(NO_TOTAL))));
    stdout.flush();
  }
}

