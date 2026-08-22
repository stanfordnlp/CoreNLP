package edu.stanford.nlp.semgraph.semgrex.stats;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.io.IOUtils;
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

  /** Opens a new stage.  Handled by the script parser, not by the registry. */
  public static final String PATTERN_COMMAND = "pattern";

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
    return Collections.unmodifiableMap(registry);
  }

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

    Stage(SemgrexPattern pattern, List<SemgrexStat> stats) {
      this.pattern = pattern;
      this.stats = Collections.unmodifiableList(new ArrayList<>(stats));
    }

    public SemgrexPattern getPattern() {
      return pattern;
    }

    public List<SemgrexStat> getStats() {
      return stats;
    }

    void accumulate(List<CoreMap> sentences) {
      List<Pair<CoreMap, List<SemgrexMatch>>> matches = pattern.matchSentences(sentences, false);
      for (Pair<CoreMap, List<SemgrexMatch>> sentence : matches) {
        for (SemgrexMatch match : sentence.second()) {
          for (SemgrexStat stat : stats) {
            stat.accumulate(match);
          }
        }
      }
    }

    String report() {
      StringBuilder sb = new StringBuilder();
      for (SemgrexStat stat : stats) {
        if (sb.length() > 0) {
          sb.append(System.lineSeparator());
        }
        sb.append(COMMENT).append(" ").append(stat.toString()).append(System.lineSeparator());
        sb.append(stat.report());
      }
      return sb.toString();
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
     */
    public boolean bidiAllowed() {
      return bidiAllowed;
    }

    public SemgrexPattern getPattern() {
      return pattern;
    }

    /** Checks that keys name something in this stage's pattern.  See validateKeys. */
    public void validateKeys(String command, List<String> keys) {
      SemgrexStats.validateKeys(pattern, command, keys);
    }

    /**
     * Declares a set for a command in this stage to fill.
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

  public SemgrexStats(List<Stage> stages) {
    this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
  }

  public List<Stage> getStages() {
    return stages;
  }

  public int numStages() {
    return stages.size();
  }

  // ------------------------------------------------------------------
  // parsing
  // ------------------------------------------------------------------

  public static SemgrexStats parse(String script) {
    return parse(script, true);
  }

  public static SemgrexStats parse(String script, boolean bidiAllowed) {
    return parse(Arrays.asList(script.split("\n")), bidiAllowed);
  }

  public static SemgrexStats parse(List<String> lines) {
    return parse(lines, true);
  }

  public static SemgrexStats parse(List<String> lines, boolean bidiAllowed) {
    List<String> meaningful = new ArrayList<>();
    for (String line : lines) {
      // \r so that a script written on Windows doesn't produce a
      // pattern with an invisible character glued to the end of it
      String trimmed = line.replaceAll("\r$", "").trim();
      if (trimmed.isEmpty() || trimmed.startsWith(COMMENT)) {
        continue;
      }
      meaningful.add(trimmed);
    }

    if (meaningful.isEmpty()) {
      throw new SemgrexParseException("Script was empty: expected a '" + PATTERN_COMMAND + "' line");
    }

    Map<String, Set<String>> sets = new HashMap<>();
    Map<String, Integer> setStages = new HashMap<>();

    List<Stage> stages = new ArrayList<>();
    Context context = null;
    List<SemgrexStat> stats = new ArrayList<>();

    for (String line : meaningful) {
      Pair<String, String> split = splitCommand(line);
      String command = split.first();
      String rest = split.second();

      if (PATTERN_COMMAND.equals(command)) {
        if (context != null) {
          stages.add(finishStage(context, stats));
          stats = new ArrayList<>();
        }
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
    }

    stages.add(finishStage(context, stats));
    return new SemgrexStats(stages);
  }

  private static Stage finishStage(Context context, List<SemgrexStat> stats) {
    if (stats.isEmpty()) {
      throw new SemgrexParseException("A '" + PATTERN_COMMAND + "' line had no statistics commands after it.  " +
                                      "Known commands: " + knownCommands());
    }
    return new Stage(context.getPattern(), stats);
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
                                        "(as a node, regex, or edge).  Available names: " + available);
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
   * Feed a batch of sentences to one stage.
   *<br>
   * May be called more than once for a stage, so that a corpus can be
   * processed a file at a time rather than held in memory all at once.
   * Stages must be run in order, since a later one may read a set an
   * earlier one is still filling.
   */
  public void accumulate(int stage, List<CoreMap> sentences) {
    stages.get(stage).accumulate(sentences);
  }

  public String report() {
    StringBuilder sb = new StringBuilder();
    for (int idx = 0; idx < stages.size(); ++idx) {
      if (sb.length() > 0) {
        sb.append(System.lineSeparator());
      }
      if (stages.size() > 1) {
        sb.append(COMMENT).append(COMMENT).append(" stage ").append(idx + 1).append(System.lineSeparator());
      }
      sb.append(stages.get(idx).report());
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
  private static final String NO_BIDI = "-noBidi";

  public static void help() {
    log.info("Possible arguments for SemgrexStats:");
    log.info(SCRIPT + ": a file containing a stats script");
    log.info(CONLLU_FILE + ": CoNLL-U dependency trees to process.  May be a file, a directory of "
                         + CONLLU_EXTENSION + " files, several of either separated by , or ; "
                         + "or the flag repeated");
    log.info(TREE_FILE + ": a file of trees to process");
    log.info(MODE + ": what mode for dependencies.  basic, collapsed, or ccprocessed.  To get 'noncollapsed', use basic with extras");
    log.info(EXTRAS + ": whether or not to use extras");
    log.info(NO_BIDI + ": never add the Unicode bidi controls which keep a right to left "
                     + "script from reversing a chart's columns.  They are added automatically "
                     + "when the data needs them, which is right for a browser or a chat client "
                     + "but not for a terminal which mangles them");
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
   * A directory contributes the CONLLU_EXTENSION files directly inside
   * it, sorted, since File.listFiles is in no particular order and a
   * UD treebank directory also holds a README and a LICENSE.  A file
   * named explicitly is read whatever it happens to be called.
   */
  static List<File> expandConlluFiles(String[] paths) {
    List<File> files = new ArrayList<>();
    for (String arg : paths) {
      for (String path : arg.split("[,;]")) {
        path = path.trim();
        if (path.isEmpty()) {
          continue;
        }

        File file = new File(path);
        if (file.isDirectory()) {
          File[] contents = file.listFiles();
          if (contents == null) {
            throw new IllegalArgumentException("Could not read the directory " + path);
          }
          List<File> found = new ArrayList<>();
          for (File child : contents) {
            if (child.isFile() && child.getName().toLowerCase().endsWith(CONLLU_EXTENSION)) {
              found.add(child);
            }
          }
          if (found.isEmpty()) {
            throw new IllegalArgumentException("Found no " + CONLLU_EXTENSION + " files in the directory " + path);
          }
          Collections.sort(found);
          files.addAll(found);
        } else if (file.isFile()) {
          files.add(file);
        } else {
          throw new IllegalArgumentException("Not a file or a directory: " + path);
        }
      }
    }
    if (files.isEmpty()) {
      throw new IllegalArgumentException("No files to read");
    }
    return files;
  }

  public static void main(String[] args) throws IOException {
    Map<String, Integer> flagMap = Generics.newHashMap();
    flagMap.put(SCRIPT, 1);
    flagMap.put(TREE_FILE, 1);
    flagMap.put(CONLLU_FILE, 1);
    flagMap.put(MODE, 1);
    flagMap.put(EXTRAS, 1);
    flagMap.put(NO_BIDI, 0);

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

    String modeString = DEFAULT_MODE;
    if (argsMap.containsKey(MODE) && argsMap.get(MODE).length > 0) {
      modeString = argsMap.get(MODE)[0].toUpperCase();
    }
    SemanticGraphFactory.Mode mode = SemanticGraphFactory.Mode.valueOf(modeString);

    boolean useExtras = true;
    if (argsMap.containsKey(EXTRAS) && argsMap.get(EXTRAS).length > 0) {
      useExtras = Boolean.parseBoolean(argsMap.get(EXTRAS)[0]);
    }

    if (!argsMap.containsKey(TREE_FILE) && !argsMap.containsKey(CONLLU_FILE)) {
      help();
      System.exit(2);
    }

    List<File> conlluFiles = Collections.emptyList();
    if (argsMap.containsKey(CONLLU_FILE)) {
      // expand the whole list before reading anything, so that a typo
      // in the last filename is reported now rather than after an hour
      // of counting
      try {
        conlluFiles = expandConlluFiles(argsMap.get(CONLLU_FILE));
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

      if (argsMap.containsKey(TREE_FILE)) {
        for (String treeFile : argsMap.get(TREE_FILE)) {
          log.info("Loading file " + treeFile);
          stats.accumulate(stage, SemgrexUtils.readTreeFile(treeFile, mode, useExtras));
        }
      }

      if (!conlluFiles.isEmpty()) {
        try {
          CoNLLUReader reader = new CoNLLUReader();
          for (File conlluFile : conlluFiles) {
            log.info("Loading file " + conlluFile);
            List<CoreMap> sentences = new ArrayList<>();
            for (Annotation doc : reader.readCoNLLUFile(conlluFile.toString())) {
              sentences.addAll(doc.get(CoreAnnotations.SentencesAnnotation.class));
            }
            stats.accumulate(stage, sentences);
          }
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    }

    System.out.print(stats.report());
  }
}
