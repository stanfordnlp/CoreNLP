package edu.stanford.nlp.semgraph.semgrex.stats;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.semgrex.SemgrexUtils;
import edu.stanford.nlp.semgraph.semgrex.RootPattern;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Runs a semgrex stats script over a corpus and prints the results.
 *<br>
 * A script is a semgrex pattern on its first line, followed by one
 * statistics command per line:
 *<pre>
 * # subjects, by the relation used and the part of speech of the dependent
 * {}=gov &gt;/nsubj.*&#47;=edge {}=dep
 * count edge
 * count edge dep
 *</pre>
 * Blank lines and lines beginning with {@code #} are ignored.  All of
 * the commands accumulate during a single pass over the corpus, so
 * asking several questions at once costs no more than asking one.
 *<br>
 * The command language is parsed here rather than in the semgrex
 * grammar.  A command word added to the grammar would become a reserved
 * token in the lexer -- {@code count}, {@code mean}, and {@code top} are
 * all words someone might reasonably want as a node name or a lemma --
 * and every new command would mean regenerating the parser.  This way
 * adding a command is a new class and one line in {@link #REGISTRY}.
 *
 * @author John Bauer
 */
public class SemgrexStats {
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemgrexStats.class);

  /**
   * Command word to the thing which builds it.  Add new commands here.
   */
  private static final Map<String, SemgrexStat.Factory> REGISTRY = buildRegistry();

  private static Map<String, SemgrexStat.Factory> buildRegistry() {
    Map<String, SemgrexStat.Factory> registry = new LinkedHashMap<>();
    registry.put("count", CountStat::create);
    return Collections.unmodifiableMap(registry);
  }

  public static List<String> knownCommands() {
    return new ArrayList<>(REGISTRY.keySet());
  }

  private final SemgrexPattern pattern;
  private final List<SemgrexStat> stats;

  public SemgrexStats(SemgrexPattern pattern, List<SemgrexStat> stats) {
    this.pattern = pattern;
    this.stats = Collections.unmodifiableList(new ArrayList<>(stats));
  }

  public SemgrexPattern getPattern() {
    return pattern;
  }

  public List<SemgrexStat> getStats() {
    return stats;
  }

  // ------------------------------------------------------------------
  // parsing
  // ------------------------------------------------------------------

  /**
   * Parse a whole script: pattern on the first meaningful line, commands after.
   */
  public static SemgrexStats parse(String script) {
    return parse(Arrays.asList(script.split("\n")));
  }

  public static SemgrexStats parse(List<String> lines) {
    List<String> meaningful = new ArrayList<>();
    for (String line : lines) {
      // \r so that a script written on Windows doesn't produce a
      // pattern with an invisible character glued to the end of it
      String trimmed = line.replaceAll("\r$", "").trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      meaningful.add(trimmed);
    }

    if (meaningful.isEmpty()) {
      throw new SemgrexParseException("Script was empty: expected a semgrex pattern on the first line");
    }

    // note that we compile a single line, never the whole script.
    // SemgrexPattern.Root() stops at its newline without checking for
    // EOF, so handing it the entire script would parse the pattern and
    // discard every command after it without complaining
    SemgrexPattern pattern = SemgrexPattern.compile(meaningful.get(0));

    List<SemgrexStat> stats = new ArrayList<>();
    for (String line : meaningful.subList(1, meaningful.size())) {
      stats.add(parseCommand(pattern, line));
    }

    if (stats.isEmpty()) {
      throw new SemgrexParseException("Script had a pattern but no statistics commands.  Known commands: " + knownCommands());
    }

    return new SemgrexStats(pattern, stats);
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

  /**
   * Parse one command line, such as "count -flat edge dep"
   */
  public static SemgrexStat parseCommand(SemgrexPattern pattern, String line) {
    List<String> pieces = Arrays.asList(line.trim().split("\\s+"));
    String command = pieces.get(0);
    SemgrexStat.Factory factory = REGISTRY.get(command);
    if (factory == null) {
      throw new SemgrexParseException("Unknown statistics command '" + command + "'.  Known commands: " + knownCommands());
    }
    return factory.create(pattern, pieces.subList(1, pieces.size()));
  }

  // ------------------------------------------------------------------
  // running
  // ------------------------------------------------------------------

  /**
   * Match the pattern over these sentences, feeding every match to every command.
   *<br>
   * May be called more than once, so that a corpus can be processed a
   * file at a time rather than held in memory all at once.
   */
  public void accumulate(List<CoreMap> sentences) {
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = pattern.matchSentences(sentences, false);
    for (Pair<CoreMap, List<SemgrexMatch>> sentence : matches) {
      for (SemgrexMatch match : sentence.second()) {
        for (SemgrexStat stat : stats) {
          stat.accumulate(match);
        }
      }
    }
  }

  public String report() {
    StringBuilder sb = new StringBuilder();
    for (SemgrexStat stat : stats) {
      if (sb.length() > 0) {
        sb.append(System.lineSeparator());
      }
      sb.append("# ").append(stat.toString()).append(System.lineSeparator());
      sb.append(stat.report());
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

  public static void help() {
    log.info("Possible arguments for SemgrexStats:");
    log.info(SCRIPT + ": a file containing a stats script");
    log.info(CONLLU_FILE + ": CoNLL-U dependency trees to process.  May be a file, a directory of "
                         + CONLLU_EXTENSION + " files, several of either separated by , or ; "
                         + "or the flag repeated");
    log.info(TREE_FILE + ": a file of trees to process");
    log.info(MODE + ": what mode for dependencies.  basic, collapsed, or ccprocessed.  To get 'noncollapsed', use basic with extras");
    log.info(EXTRAS + ": whether or not to use extras");
    log.info();
    log.info(SCRIPT + " is required, as is one of " + CONLLU_FILE + " or " + TREE_FILE);
    log.info();
    log.info("A script is a semgrex pattern followed by one command per line:");
    log.info("  {}=gov >nsubj=edge {}=dep");
    log.info("  count edge");
    log.info("  count edge gov");
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
    SemgrexStats stats = SemgrexStats.parse(script);

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

    // accumulate a file at a time so that a large corpus doesn't have
    // to be resident all at once
    if (argsMap.containsKey(TREE_FILE)) {
      for (String treeFile : argsMap.get(TREE_FILE)) {
        log.info("Loading file " + treeFile);
        stats.accumulate(SemgrexUtils.readTreeFile(treeFile, mode, useExtras));
      }
    }

    if (argsMap.containsKey(CONLLU_FILE)) {
      // expand the whole list before reading anything, so that a typo
      // in the last filename is reported now rather than after an hour
      // of counting
      List<File> conlluFiles;
      try {
        conlluFiles = expandConlluFiles(argsMap.get(CONLLU_FILE));
      } catch (IllegalArgumentException e) {
        log.err(e.getMessage());
        System.exit(2);
        return;
      }

      try {
        CoNLLUReader reader = new CoNLLUReader();
        for (File conlluFile : conlluFiles) {
          log.info("Loading file " + conlluFile);
          List<CoreMap> sentences = new ArrayList<>();
          for (Annotation doc : reader.readCoNLLUFile(conlluFile.toString())) {
            sentences.addAll(doc.get(CoreAnnotations.SentencesAnnotation.class));
          }
          stats.accumulate(sentences);
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    System.out.print(stats.report());
  }
}
