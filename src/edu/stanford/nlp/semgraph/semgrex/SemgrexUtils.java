package edu.stanford.nlp.semgraph.semgrex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Refactored a useful semgrex key comparison, so as to allow multiple places to use it
 *
 * @author John Bauer
 */
public class SemgrexUtils {
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemgrexUtils.class);

  /** Creates a SemgrexUtils; all of its methods are static. */
  public SemgrexUtils() { }

  /**
   * Compares two keys element by element.  A null key sorts after a
   * non-null key.  Keys which match up to the length of the shorter
   * key are considered equal.
   *
   * @param first The first key
   * @param second The second key
   * @return A negative number, zero, or a positive number as {@code first}
   * sorts before, equal to, or after {@code second}
   */
  static public int compareKeys(List<String> first, List<String> second) {
    if (first == null && second == null) {
      return 0;
    }
    if (second == null) {
      return -1;
    }
    if (first == null) {
      return 1;
    }
    for (int idx = 0; idx < first.size() && idx < second.size(); ++idx) {
      int cmp = first.get(idx).compareTo(second.get(idx));
      if (cmp != 0) {
        return cmp;
      }
    }
    // what if they are different lengths?
    // shouldn't happen here anyway
    return 0;
  }

  /**
   * For a pattern such as UniqPattern, get this key from the SemgrexMatch.
   *<br>
   * The key is expected to be unambiguous, which can be enforced at SemgrexPattern compilation time
   *
   * @param match The match to read from
   * @param key The name of a node, variable group, or edge, checked in that order
   * @return The node's value, the variable's string, or the edge's relation,
   * or null if nothing in the match has that name
   */
  static public String getKey(SemgrexMatch match, String key) {
    IndexedWord node = match.getNode(key);
    if (node != null) {
      return node.value();
    }
    String varString = match.getVariableString(key);
    if (varString != null) {
      return varString;
    }
    SemanticGraphEdge edge = match.getEdge(key);
    if (edge != null) {
      return edge.getRelation().toString();
    }
    return null;
  }

  /**
   * Builds a key from a match using {@link #getKey} for each name.
   *
   * @param match The match to read from
   * @param keys The names to look up
   * @return The value for each name, with null for names not in the match
   */
  static public List<String> buildKey(SemgrexMatch match, List<String> keys) {
    List<String> matchKey = new ArrayList<>();
    for (String key : keys) {
      matchKey.add(getKey(match, key));
    }
    return matchKey;
  }

  static class KeyPairComparator implements Comparator<Pair<Integer, List<String>>> {
    public int compare(Pair<Integer, List<String>> first, Pair<Integer, List<String>> second) {
      return SemgrexUtils.compareKeys(first.second, second.second);
    }
  }

  /**
   * Reads trees from a file and converts them to English dependency graphs.
   * Each graph is stored as the {@code BasicDependenciesAnnotation} of a
   * new sentence, whatever {@code mode} is, with the graph's words as
   * its tokens.
   *
   * @param treeFile The file or directory of trees to read
   * @param mode Which kind of dependencies to build
   * @param useExtras Whether to include all extra dependencies
   * @return One sentence per tree
   */
  public static List<CoreMap> readTreeFile(String treeFile, SemanticGraphFactory.Mode mode, boolean useExtras) {
    List<CoreMap> sentences = new ArrayList<>();
    MemoryTreebank treebank = new MemoryTreebank(new TreeNormalizer());
    treebank.loadPath(treeFile);
    for (Tree tree : treebank) {
      // TODO: allow other languages... this defaults to English
      SemanticGraph graph = SemanticGraphFactory.makeFromTree(tree, mode, useExtras ?
              GrammaticalStructure.Extras.MAXIMAL : GrammaticalStructure.Extras.NONE);
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
      List<CoreLabel> tokens = graph.vertexListSorted().stream().map(x -> x.backingLabel()).collect(Collectors.toList());
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
      sentences.add(sentence);
    }
    return sentences;
  }

  /**
   * Splits a command line into the arguments given for each flag.
   *<br>
   * A flag which names files takes every argument up to the next flag, so
   * that a list of them can be given at once.  A flag in
   * {@code singleValued} takes one argument, which leaves the arguments
   * after it free to be files.  Anything not claimed by a flag is
   * collected under a null key.
   *
   * @param args The command line
   * @param singleValued The flags which take exactly one argument
   * @return The arguments for each flag, in the order the flags first appear
   */
  public static Map<String, List<String>> parseArgs(String[] args, Set<String> singleValued) {
    return parseArgs(args, singleValued, Collections.emptySet());
  }

  /**
   * Splits a command line into the arguments given for each flag.
   *<br>
   * As {@link #parseArgs(String[], Set)}, except that a flag in
   * {@code noValue} takes no arguments, so what follows it is free to be
   * files.  Such a flag is present in the result with an empty list.
   * A flag given more than once collects the arguments from each time.
   *
   * @param args The command line
   * @param singleValued The flags which take exactly one argument
   * @param noValue The flags which take no arguments
   * @return The arguments for each flag, in the order the flags first appear
   */
  public static Map<String, List<String>> parseArgs(String[] args, Set<String> singleValued, Set<String> noValue) {
    Map<String, List<String>> parsed = new LinkedHashMap<>();
    parsed.put(null, new ArrayList<>());
    String flag = null;
    for (String arg : args) {
      if (!arg.isEmpty() && arg.charAt(0) == '-' && !isFilename(arg)) {
        flag = arg;
        parsed.computeIfAbsent(flag, key -> new ArrayList<>());
        if (noValue.contains(flag)) {
          flag = null;
        }
      } else {
        parsed.computeIfAbsent(flag, key -> new ArrayList<>()).add(arg);
        if (singleValued.contains(flag)) {
          flag = null;
        }
      }
    }
    return parsed;
  }

  /** A leading "-" is a flag unless it names something on disk, such as -oddly-named.conllu */
  private static boolean isFilename(String arg) {
    return new File(arg).exists();
  }

  /**
   * The first argument given for a flag.
   *
   * @param args Arguments as returned by {@link #parseArgs}
   * @param flag The flag to look up
   * @param fallback What to return if the flag was not given or has no arguments
   * @return The first argument for the flag, or {@code fallback}
   */
  public static String firstArg(Map<String, List<String>> args, String flag, String fallback) {
    List<String> values = args.get(flag);
    return (values == null || values.isEmpty()) ? fallback : values.get(0);
  }

  /**
   * Every file named by the given paths, expanding directories and globs.
   *<br>
   * A path may be a file, a directory, or a pattern such as
   * es_gsd-ud-*.conllu.  A shell normally expands a pattern before the
   * program sees it, but not when it is quoted.  A file named outright is
   * read whatever it is called; a directory gives up only the files with
   * the given extension.
   *
   * @param paths The files, directories, and patterns to expand
   * @param extension The ending of the files to take from a directory
   * @return The files, in the order the paths were given, sorted within each directory or pattern
   * @throws IOException if a path is neither a file nor a directory and matches no files
   */
  public static List<File> expandFiles(List<String> paths, String extension) throws IOException {
    List<File> files = new ArrayList<>();
    for (String path : paths) {
      File file = new File(path);
      if (file.isFile()) {
        files.add(file);
      } else if (file.isDirectory()) {
        // a directory is a request for the files of the kind being read,
        // not for a README which happens to sit beside them
        File[] listed = file.listFiles();
        if (listed != null) {
          List<File> sorted = new ArrayList<>(Arrays.asList(listed));
          Collections.sort(sorted);
          int found = 0;
          for (File child : sorted) {
            if (child.isFile() && child.getName().endsWith(extension)) {
              files.add(child);
              ++found;
            }
          }
          if (found == 0) {
            log.info("No files ending in " + extension + " in " + file);
          }
        }
      } else {
        List<File> matches = glob(path);
        if (matches.isEmpty()) {
          throw new FileNotFoundException("Could not find any files matching " + path);
        }
        files.addAll(matches);
      }
    }
    return files;
  }

  /** The files matching a path whose last piece is a pattern */
  private static List<File> glob(String path) {
    File asFile = new File(path);
    File parent = asFile.getParentFile() == null ? new File(".") : asFile.getParentFile();
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + asFile.getName());
    File[] listed = parent.listFiles();
    List<File> matches = new ArrayList<>();
    if (listed != null) {
      for (File child : listed) {
        if (child.isFile() && matcher.matches(Paths.get(child.getName()))) {
          matches.add(child);
        }
      }
    }
    Collections.sort(matches);
    return matches;
  }
}
